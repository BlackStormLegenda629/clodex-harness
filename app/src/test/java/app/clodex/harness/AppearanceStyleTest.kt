package app.clodex.harness

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import app.clodex.harness.data.AppStyle
import app.clodex.harness.data.K
import app.clodex.harness.data.ProgressStyle
import app.clodex.harness.data.SettingsRepository
import app.clodex.harness.ui.theme.Accents
import app.clodex.harness.ui.theme.colorsFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppearanceStyleTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun `scroll effects are opt-in and progress preference survives reopening`() = runBlocking {
        withRepository { settings ->
            assertEquals(false, settings.flow.first().scrollBlur)
            assertEquals(ProgressStyle.Straight, settings.flow.first().progressStyle)
            settings.set(K.scrollBlur, true)
            settings.set(K.progressStyle, ProgressStyle.Wave.name)
        }
        withRepository { settings ->
            assertEquals(true, settings.flow.first().scrollBlur)
            assertEquals(ProgressStyle.Wave, settings.flow.first().progressStyle)
            settings.set(K.progressStyle, "FutureStyle")
            assertEquals(ProgressStyle.Straight, settings.flow.first().progressStyle)
        }
    }

    @Test fun `existing and unrecognized preferences use Material`() = runBlocking {
        withRepository { settings ->
            assertEquals(AppStyle.Material, settings.flow.first().style)
            settings.set(stringPreferencesKey("style"), "FutureStyle")
            assertEquals(AppStyle.Material, settings.flow.first().style)
        }
    }

    @Test fun `style survives reopening and can be changed back without changing theme`() = runBlocking {
        withRepository { settings ->
            settings.set(K.theme, "Тёмная")
            settings.set(K.style, AppStyle.Studio.name)
        }
        withRepository { settings ->
            assertEquals(AppStyle.Studio, settings.flow.first().style)
            settings.set(K.style, AppStyle.Material.name)
        }
        withRepository { settings ->
            assertEquals(AppStyle.Material, settings.flow.first().style)
            assertEquals("Тёмная", settings.flow.first().theme)
            settings.set(K.style, AppStyle.Classic.name)
        }
        withRepository { settings ->
            assertEquals(AppStyle.Classic, settings.flow.first().style)
            assertEquals("Тёмная", settings.flow.first().theme)
        }
    }

    @Test fun `Studio changes neutral surfaces while preserving light and dark custom accents`() {
        val accent = Accents[1]
        for (dark in listOf(false, true)) {
            val classic = colorsFor(dark, accent, AppStyle.Classic)
            val studio = colorsFor(dark, accent, AppStyle.Studio)
            assertEquals(colorsFor(dark, accent, AppStyle.Classic), classic)
            assertNotEquals(classic.bg, studio.bg)
            assertNotEquals(classic.s2, studio.s2)
            assertEquals(dark, studio.dark)
            assertEquals(if (dark) accent.dark else accent.light, studio.ac)
        }
    }

    @Test fun `diff warning and status colours stay fixed across styles`() {
        for (dark in listOf(false, true)) {
            val classic = colorsFor(dark, Accents[0], AppStyle.Classic)
            listOf(AppStyle.Studio, AppStyle.Material).forEach { style ->
                val other = colorsFor(dark, Accents[0], style)
                assertEquals(classic.ab, other.ab)
                assertEquals(classic.at, other.at)
                assertEquals(classic.db, other.db)
                assertEquals(classic.dt, other.dt)
                assertEquals(classic.wb, other.wb)
                assertEquals(classic.warn, other.warn)
                assertEquals(classic.ok, other.ok)
            }
        }
    }

    private suspend fun withRepository(block: suspend (SettingsRepository) -> Unit) {
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
