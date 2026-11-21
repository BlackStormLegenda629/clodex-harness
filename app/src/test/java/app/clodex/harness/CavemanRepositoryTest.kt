package app.clodex.harness

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.clodex.harness.data.CavemanIntensity
import app.clodex.harness.data.CavemanRepository
import app.clodex.harness.data.SettingsRepository
import app.clodex.harness.data.SkillEntity
import app.clodex.harness.data.SkillRepository
import app.clodex.harness.data.Src
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CavemanRepositoryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private class FakeSkills : SkillRepository {
        private val state = MutableStateFlow<List<SkillEntity>>(emptyList())
        override val skills: Flow<List<SkillEntity>> = state
        override suspend fun get(id: String): SkillEntity? = state.value.firstOrNull { it.id == id }
        override suspend fun upsert(s: SkillEntity) {
            state.value = state.value.filterNot { it.id == s.id } + s
        }
        override suspend fun delete(id: String) {
            state.value = state.value.filterNot { it.id == id }
        }
        fun all(): List<SkillEntity> = state.value
    }

    @Test fun `install is idempotent and uninstall only removes caveman skills`() = runBlocking {
        val fakeSkills = FakeSkills()
        withSettings { settings ->
            val repo = CavemanRepository(fakeSkills, settings)
            fakeSkills.upsert(SkillEntity("mine", "Mine", "", Src.MINE, true, "Pen", body = "x"))
            repo.install(); repo.install()
            assertEquals(6, fakeSkills.all().size)
            assertTrue(settings.flow.first().cavemanInstalled)
            repo.uninstall()
            assertEquals(listOf("mine"), fakeSkills.all().map { it.id })
            assertFalse(settings.flow.first().cavemanInstalled)
        }
    }

    @Test fun `intensity persists and skill toggles stay inside caveman`() = runBlocking {
        val fakeSkills = FakeSkills()
        withSettings { settings ->
            val repo = CavemanRepository(fakeSkills, settings)
            repo.install()
            repo.setIntensity(CavemanIntensity.ULTRA)
            assertEquals(CavemanIntensity.ULTRA, settings.flow.first().cavemanIntensity)
            repo.setSkillEnabled("caveman-commit", false)
            assertFalse(fakeSkills.get("caveman-commit")!!.enabled)
            fakeSkills.upsert(SkillEntity("mine", "Mine", "", Src.MINE, true, "Pen", body = "x"))
            repo.setSkillEnabled("mine", false)
            assertTrue(fakeSkills.get("mine")!!.enabled)
        }
    }

    private suspend fun withSettings(block: suspend (SettingsRepository) -> Unit) {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) {
            temporaryFolder.root.resolve("settings.preferences_pb")
        }
        try {
            block(SettingsRepository(store))
        } finally {
            job.cancelAndJoin()
        }
    }
}
