package app.clodex.harness

import app.clodex.harness.ui.theme.CodeFontCatalog
import app.clodex.harness.ui.theme.UiFontCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

class FontCatalogTest {
    @Test fun uiCatalogHasRequestedBundledAndSystemChoices() {
        assertEquals(listOf("Inter", "Manrope", "Noto Sans", "System"), UiFontCatalog.names)
    }

    @Test fun codeCatalogHasRequestedBundledAndSystemChoices() {
        assertEquals(
            listOf("JetBrains Mono", "Fira Code", "IBM Plex Mono", "Source Code Pro", "System Mono"),
            CodeFontCatalog.names,
        )
    }

    @Test fun unknownSavedNamesFallBackWithoutCrashing() {
        assertEquals("Inter", UiFontCatalog.resolveName("removed-font"))
        assertEquals("JetBrains Mono", CodeFontCatalog.resolveName("removed-font"))
    }
}
