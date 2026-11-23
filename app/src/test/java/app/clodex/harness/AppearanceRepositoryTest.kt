package app.clodex.harness

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.clodex.harness.clodex.settings.AppFont
import app.clodex.harness.clodex.settings.AppearanceRepository
import app.clodex.harness.clodex.settings.AppearanceSettings
import app.clodex.harness.clodex.settings.DesignStyle
import app.clodex.harness.clodex.settings.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppearanceRepositoryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun repository() = AppearanceRepository(
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ) { temporaryFolder.root.resolve("appearance.preferences_pb") },
    )

    @Test fun defaultsMatchTheSpecification() = runBlocking {
        val settings = repository().settings.first()

        assertEquals(Color(0xFFFF7A1A), settings.accent)
        assertEquals(false, settings.liquidGlass)
        assertEquals(DesignStyle.MODERN, settings.design)
        assertEquals(AppFont.INTER, settings.font)
        assertEquals(20.dp, settings.radius)
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
    }

    @Test fun everyFieldRoundTrips() = runBlocking {
        val repo = repository()
        val next = AppearanceSettings(
            accent = Color(0xFF3DDC97),
            liquidGlass = true,
            design = DesignStyle.MATERIAL_YOU,
            font = AppFont.MANROPE,
            radius = 12.dp,
            themeMode = ThemeMode.LIGHT,
        )
        repo.setAccent(next.accent)
        repo.setLiquidGlass(next.liquidGlass)
        repo.setDesign(next.design)
        repo.setFont(next.font)
        repo.setRadius(next.radius)
        repo.setThemeMode(next.themeMode)

        val stored = repo.settings.first()
        assertEquals(next.accent, stored.accent)
        assertEquals(true, stored.liquidGlass)
        assertEquals(DesignStyle.MATERIAL_YOU, stored.design)
        assertEquals(AppFont.MANROPE, stored.font)
        assertEquals(12.dp, stored.radius)
        assertEquals(ThemeMode.LIGHT, stored.themeMode)
    }

    @Test fun unknownStoredValuesFallBackToDefaults() = runBlocking {
        val repo = repository()
        repo.setFont(AppFont.ROBOTO)
        repo.setDesign(DesignStyle.MATERIAL_YOU)

        val stored = repo.settings.first()
        assertEquals(AppFont.ROBOTO, stored.font)
        assertEquals(DesignStyle.MATERIAL_YOU, stored.design)
    }
}
