package app.clodex.harness.data

import java.net.URI

/** Turns a settings entry into a real workspace. SAF folders keep their tree URI verbatim. */
internal object WorkDirMapping {
    fun toWorkspace(dir: WorkDir): WorkspaceEntity {
        require(dir.path.isNotBlank()) { "Пустой путь рабочей папки" }
        val path = if (dir.tag == "saf") URI(dir.path).path.orEmpty() else dir.path
        val name = path.trimEnd('/').substringAfterLast('/').substringAfterLast(':').ifBlank { "Папка" }
        return WorkspaceEntity(name = name, kind = "dev", path = dir.path)
    }
}
