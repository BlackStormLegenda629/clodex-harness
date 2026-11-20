package app.clodex.harness.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File

/** SAF helpers: browse a picked tree without asking for MANAGE_EXTERNAL_STORAGE. */
object Saf {
    data class Entry(val name: String, val uri: String, val dir: Boolean, val size: Long)

    fun takePermission(ctx: Context, uri: Uri) {
        val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (runCatching { ctx.contentResolver.takePersistableUriPermission(uri, rw) }.isSuccess) return
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    fun name(ctx: Context, uri: Uri): String {
        val fromDoc = runCatching {
            if (DocumentsContract.isDocumentUri(ctx, uri)) DocumentsContract.getDocumentId(uri).substringAfterLast('/').substringAfterLast(':')
            else null
        }.getOrNull()
        val fromQuery = runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        return fromQuery?.takeIf { it.isNotBlank() }
            ?: fromDoc?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')?.takeIf { it.isNotBlank() }
            ?: "Папка"
    }

    private fun docId(uri: Uri): String = runCatching {
        if (uri.path?.contains("/document/") == true) DocumentsContract.getDocumentId(uri)
        else DocumentsContract.getTreeDocumentId(uri)
    }.getOrElse { DocumentsContract.getTreeDocumentId(uri) }

    fun children(ctx: Context, uri: Uri): List<Entry> {
        val entries = runCatching {
            val parent = DocumentsContract.buildChildDocumentsUriUsingTree(uri, docId(uri))
            val out = ArrayList<Entry>()
            ctx.contentResolver.query(
                parent,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2).orEmpty()
                    val size = if (c.isNull(3)) 0L else c.getLong(3)
                    val child = DocumentsContract.buildDocumentUriUsingTree(uri, id)
                    out += Entry(name, child.toString(), mime == DocumentsContract.Document.MIME_TYPE_DIR, size)
                }
            }
            out
        }.getOrDefault(emptyList())
        return entries.sortedWith(compareBy({ !it.dir }, { it.name.lowercase() }))
    }

    fun isText(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in setOf(
            "txt", "md", "csv", "json", "xml", "yaml", "yml", "kt", "java", "py", "js", "ts", "tsx",
            "html", "css", "sql", "sh", "toml", "gradle", "properties", "ini", "log", "svg",
        )

    fun readText(ctx: Context, uri: Uri, limit: Int = 120_000): String? = runCatching {
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            val buf = ByteArray(8192)
            val out = java.io.ByteArrayOutputStream()
            while (out.size() < limit) {
                val r = input.read(buf)
                if (r < 0) break
                out.write(buf, 0, r)
            }
            out.toString(Charsets.UTF_8.name())
        }
    }.getOrNull()

    fun humanSize(bytes: Long): String = when {
        bytes <= 0 -> ""
        bytes < 1024 -> "$bytes Б"
        bytes < 1024 * 1024 -> "%.1f КБ".format(bytes / 1024.0)
        else -> "%.1f МБ".format(bytes / (1024.0 * 1024.0))
    }

    /** For the terminal hint: SAF folders have no stable /storage path we may chdir into. */
    fun localPathOrNull(uri: Uri): String? = runCatching {
        val id = DocumentsContract.getDocumentId(uri)
        if (id.startsWith("primary:")) "/storage/emulated/0/" + id.removePrefix("primary:")
        else null
    }.getOrNull()

    fun exists(path: String?): Boolean = path != null && File(path).isDirectory
}
