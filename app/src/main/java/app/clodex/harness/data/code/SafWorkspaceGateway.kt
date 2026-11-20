package app.clodex.harness.data.code

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * A picked SAF tree as a workspace. Paths are resolved segment by segment through document IDs and
 * display names; a SAF URI is never turned into an assumed filesystem path. No shell, no builds.
 */
class SafWorkspaceGateway(context: Context, private val treeUri: Uri) : WorkspaceGateway {
    private val resolver: ContentResolver = context.contentResolver
    private val ids = mutableMapOf<WorkspacePath, String>()

    private class Doc(val id: String, val name: String, val mime: String, val size: Long) {
        val dir get() = mime == Document.MIME_TYPE_DIR
    }

    private fun rootId(): String = ids.getOrPut(WorkspacePath.ROOT) { DocumentsContract.getTreeDocumentId(treeUri) }

    private fun docUri(id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)

    private fun children(parentId: String): List<Doc> {
        val out = ArrayList<Doc>()
        val cols = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE)
        resolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId), cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                out += Doc(id, name, c.getString(2).orEmpty(), if (c.isNull(3)) 0L else c.getLong(3))
            }
        } ?: throw SecurityException("query returned null")
        return out
    }

    private fun stat(id: String): Doc {
        val cols = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE)
        resolver.query(docUri(id), cols, null, null, null)?.use { c ->
            if (c.moveToFirst()) return Doc(id, c.getString(1).orEmpty(), c.getString(2).orEmpty(), if (c.isNull(3)) 0L else c.getLong(3))
        }
        throw java.io.FileNotFoundException(id)
    }

    /** Document id for [path], or null when a segment does not exist. */
    private fun resolve(path: WorkspacePath): String? {
        ids[path]?.let { return it }
        if (path.isRoot) return rootId()
        val parent = resolve(path.parent) ?: return null
        val doc = children(parent).firstOrNull { it.name == path.name } ?: return null
        ids[path] = doc.id
        return doc.id
    }

    private suspend fun <T> io(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
        runCatching(block).recoverCatching { throw WorkspacePolicy.mapSafError(it) }
    }

    override suspend fun list(path: WorkspacePath): Result<List<WorkspaceEntry>> = io {
        val id = resolve(path) ?: throw WorkspaceException("Папка не найдена: $path", notFound = true)
        children(id)
            .sortedWith(compareBy({ !it.dir }, { it.name.lowercase() }))
            .take(WorkspaceLimits.MAX_LIST)
            .mapNotNull { d ->
                val p = path.child(d.name).getOrNull() ?: return@mapNotNull null
                ids[p] = d.id
                WorkspaceEntry(p, d.dir, d.size, !d.dir && WorkspacePolicy.canReadText(d.name, d.mime, d.size))
            }
    }

    override suspend fun read(path: WorkspacePath): Result<WorkspaceRead> = io {
        val id = resolve(path) ?: throw WorkspaceException("Файл не найден: $path", notFound = true)
        val doc = stat(id)
        if (doc.dir) throw WorkspaceException("Это папка, а не файл: $path")
        if (!WorkspacePolicy.canReadText(path.name, doc.mime, doc.size)) {
            return@io WorkspaceRead(path, null, doc.size, WorkspacePolicy.unreadableReason(path.name, doc.size))
        }
        val bytes = readBytes(id, WorkspaceLimits.MAX_READ_BYTES + 1)
        when {
            bytes.size > WorkspaceLimits.MAX_READ_BYTES -> WorkspaceRead(path, null, bytes.size.toLong(), WorkspacePolicy.unreadableReason(path.name, bytes.size.toLong()))
            WorkspacePolicy.looksBinary(bytes) -> WorkspaceRead(path, null, bytes.size.toLong(), "двоичный файл")
            else -> WorkspaceRead(path, String(bytes, Charsets.UTF_8), bytes.size.toLong())
        }
    }

    private fun readBytes(id: String, limit: Int): ByteArray {
        val input = resolver.openInputStream(docUri(id)) ?: throw java.io.FileNotFoundException(id)
        return input.use {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (out.size() < limit) {
                val r = it.read(buf)
                if (r < 0) break
                out.write(buf, 0, r)
            }
            out.toByteArray()
        }
    }

    private fun ensureDir(path: WorkspacePath): String {
        resolve(path)?.let { return it }
        val parent = ensureDir(path.parent)
        val created = DocumentsContract.createDocument(resolver, docUri(parent), Document.MIME_TYPE_DIR, path.name)
            ?: throw WorkspaceException("Не удалось создать папку $path")
        val id = DocumentsContract.getDocumentId(created)
        ids[path] = id
        return id
    }

    /** Applies each change separately and reports exactly which files were written. */
    override suspend fun apply(changes: List<WorkspaceChange>, message: String): Result<WorkspaceApplyResult> = withContext(Dispatchers.IO) {
        val applied = mutableListOf<WorkspacePath>()
        val failed = mutableListOf<WorkspaceFailure>()
        for (ch in changes.take(WorkspaceLimits.MAX_CHANGES)) {
            try {
                val bytes = ch.after.toByteArray(Charsets.UTF_8)
                WorkspacePolicy.writeError(ch.path.value, bytes.size)?.let { throw WorkspaceException(it) }
                val existing = resolve(ch.path)
                if (existing != null) {
                    val current = String(readBytes(existing, WorkspaceLimits.MAX_WRITE_BYTES + 1), Charsets.UTF_8)
                    if (ch.before == null || current != ch.before) throw WorkspaceException("файл изменился после чтения")
                }
                if (existing == null && ch.before != null) throw WorkspaceException("файл удалён после чтения")
                val id = existing ?: run {
                    val parent = ensureDir(ch.path.parent)
                    val uri = DocumentsContract.createDocument(resolver, docUri(parent), "application/octet-stream", ch.path.name)
                        ?: throw WorkspaceException("не удалось создать файл")
                    val newId = DocumentsContract.getDocumentId(uri)
                    val actual = stat(newId).name
                    if (actual != ch.path.name) throw WorkspaceException("провайдер сохранил файл как «$actual»")
                    ids[ch.path] = newId
                    newId
                }
                write(id, bytes)
                applied += ch.path
            } catch (e: Exception) {
                val mapped = WorkspacePolicy.mapSafError(e)
                if (mapped is WorkspaceException && mapped.recoverable && applied.isEmpty()) return@withContext Result.failure(mapped)
                failed += WorkspaceFailure(ch.path, mapped.message ?: "ошибка записи")
            }
        }
        changes.drop(WorkspaceLimits.MAX_CHANGES).forEach { failed += WorkspaceFailure(it.path, "больше ${WorkspaceLimits.MAX_CHANGES} файлов за раз") }
        Result.success(WorkspaceApplyResult(applied, failed))
    }

    private fun write(id: String, bytes: ByteArray) {
        val uri = docUri(id)
        val out = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(uri, "rwt")
            ?: throw WorkspaceException("не удалось открыть файл для записи")
        out.use { it.write(bytes) }
    }
}
