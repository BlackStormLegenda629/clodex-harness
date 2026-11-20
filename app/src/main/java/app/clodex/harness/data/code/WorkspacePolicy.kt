package app.clodex.harness.data.code

import java.io.FileNotFoundException

/** Pure rules shared by workspace gateways: what may be decoded or written as text. */
object WorkspacePolicy {
    private val TEXT_EXT = setOf(
        "txt", "md", "markdown", "rst", "csv", "tsv", "json", "jsonc", "xml", "yaml", "yml", "toml", "ini", "cfg", "conf",
        "properties", "env", "log", "kt", "kts", "java", "groovy", "gradle", "scala", "py", "rb", "php", "go", "rs", "swift",
        "c", "h", "cc", "cpp", "hpp", "m", "mm", "cs", "fs", "js", "mjs", "cjs", "jsx", "ts", "tsx", "vue", "svelte",
        "html", "htm", "css", "scss", "sass", "less", "svg", "sql", "sh", "bash", "zsh", "fish", "ps1", "bat", "cmd",
        "lua", "r", "dart", "ex", "exs", "erl", "hs", "clj", "lock", "gitignore", "gitattributes", "editorconfig", "pro",
    )
    private val TEXT_NAMES = setOf(
        "readme", "license", "licence", "makefile", "dockerfile", "gradlew", "procfile", "gemfile", "rakefile",
        "changelog", "authors", "contributing", "codeowners", ".gitignore", ".gitattributes", ".editorconfig", ".env",
    )
    private val BINARY_EXT = setOf(
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "heic", "pdf", "zip", "gz", "tgz", "xz", "7z", "rar", "jar",
        "aar", "apk", "aab", "dex", "so", "a", "o", "class", "exe", "dll", "bin", "mp3", "mp4", "mov", "wav", "ogg",
        "ttf", "otf", "woff", "woff2", "db", "sqlite", "keystore", "jks",
    )
    private val TEXT_MIME = setOf(
        "application/json", "application/xml", "application/javascript", "application/x-javascript",
        "application/x-sh", "application/x-yaml", "application/yaml", "application/toml", "application/sql",
    )

    private fun ext(name: String): String = name.substringAfterLast('.', "").lowercase()

    private fun isTextName(name: String): Boolean {
        val n = name.lowercase()
        val e = ext(n)
        if (e in BINARY_EXT) return false
        return e in TEXT_EXT || n in TEXT_NAMES || n.substringBefore('.') in TEXT_NAMES
    }

    fun canReadText(name: String, mime: String, size: Long): Boolean {
        if (size > WorkspaceLimits.MAX_READ_BYTES) return false
        if (ext(name) in BINARY_EXT) return false
        return isTextName(name) || mime.startsWith("text/") || mime in TEXT_MIME
    }

    fun unreadableReason(name: String, size: Long): String =
        if (size > WorkspaceLimits.MAX_READ_BYTES) "больше ${WorkspaceLimits.MAX_READ_BYTES / 1024} КБ" else "двоичный файл"

    /** null when the write is allowed. Unknown extensions are allowed: the content is text by construction. */
    fun writeError(path: String, byteCount: Int): String? = when {
        ext(path.substringAfterLast('/')) in BINARY_EXT -> "двоичный файл"
        byteCount > WorkspaceLimits.MAX_WRITE_BYTES -> "больше ${WorkspaceLimits.MAX_WRITE_BYTES / 1024} КБ"
        else -> null
    }

    fun canWriteText(name: String, byteCount: Int): Boolean = writeError(name, byteCount) == null

    fun looksBinary(bytes: ByteArray): Boolean = bytes.any { it == 0.toByte() }

    fun mapSafError(e: Throwable): Throwable = when (e) {
        is WorkspaceException -> e
        is SecurityException -> WorkspaceException("Нет доступа к папке — выберите папку заново в «Рабочие области»", recoverable = true)
        is FileNotFoundException -> WorkspaceException("Файл или папка не найдены: ${e.message.orEmpty()}".trimEnd(' ', ':'))
        else -> WorkspaceException(e.message ?: "Ошибка доступа к папке")
    }
}
