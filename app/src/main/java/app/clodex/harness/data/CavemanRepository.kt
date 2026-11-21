package app.clodex.harness.data

/** Install/remove the five Caveman skills and keep its settings flag in sync. */
class CavemanRepository(
    private val skills: SkillRepository,
    private val settings: SettingsRepository,
) {
    /** Upserts by id, so pressing install twice never creates duplicates. */
    suspend fun install() {
        Caveman.skills.forEach { skills.upsert(it) }
        settings.set(K.cavemanInstalled, true)
    }

    /** Removes only Caveman's own ids; user skills with other ids stay untouched. */
    suspend fun uninstall() {
        Caveman.skillIds.forEach { skills.delete(it) }
        settings.set(K.cavemanInstalled, false)
    }

    suspend fun setIntensity(intensity: CavemanIntensity) = settings.set(K.cavemanIntensity, intensity.name)

    suspend fun setSkillEnabled(id: String, enabled: Boolean) {
        if (id !in Caveman.skillIds) return
        skills.get(id)?.let { skills.upsert(it.copy(enabled = enabled)) }
    }
}
