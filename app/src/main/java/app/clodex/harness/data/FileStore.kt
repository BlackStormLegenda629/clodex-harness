package app.clodex.harness.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FileStore(private val ctx: Context) {
    private fun projectDir(id: Long) = File(ctx.filesDir, "projects/$id")

    fun queryName(uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    /** Copies a picked file into app storage so the access survives restarts. Returns the stored name. */
    suspend fun importProjectFile(projectId: Long, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = projectDir(projectId).apply { mkdirs() }
            val base = queryName(uri) ?: "файл"
            var name = base
            var n = 1
            while (File(dir, name).exists()) { n++; name = base.substringBeforeLast('.', base) + "-$n" + base.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" } }
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                File(dir, name).outputStream().use { out ->
                    val buf = ByteArray(8192); var total = 0L
                    while (true) {
                        val r = input.read(buf); if (r < 0) break
                        total += r; if (total > 10_000_000) { File(dir, name).delete(); return@runCatching null }
                        out.write(buf, 0, r)
                    }
                }
            } ?: return@runCatching null
            name
        }.getOrNull()
    }

    /** Text files of a project, concatenated and capped, for the system prompt. */
    suspend fun readProjectTexts(projectId: Long, names: List<String>, limit: Int = 24_000): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        val textExt = setOf("txt", "md", "csv", "json", "xml", "yaml", "yml", "kt", "java", "py", "js", "ts", "html", "css", "sql", "sh", "toml", "gradle")
        for (n in names) {
            if (sb.length >= limit) break
            if (n.substringAfterLast('.', "").lowercase() !in textExt) continue
            val f = File(projectDir(projectId), n)
            if (!f.exists()) continue
            sb.append("### ").append(n).append('\n').append(f.readText().take(limit - sb.length)).append("\n\n")
        }
        sb.toString().trim()
    }

    fun clearAll() { File(ctx.filesDir, "projects").deleteRecursively(); File(ctx.cacheDir, "captures").deleteRecursively(); File(ctx.cacheDir, "shared").deleteRecursively() }

    fun newCaptureUri(ext: String): Uri {
        val dir = File(ctx.cacheDir, "captures").apply { mkdirs() }
        val f = File(dir, "cap_${System.currentTimeMillis()}$ext")
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
    }
}
