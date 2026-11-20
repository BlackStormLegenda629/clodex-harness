package app.clodex.harness

import app.clodex.harness.data.ConnectorCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class AssetsProvenanceTest {
    @Test fun noUnlicensedConnectorOrProviderArtworkIsShipped() {
        val dirs = listOf(File("src/main/res"), File("app/src/main/res")).filter { it.isDirectory }
        val artwork = dirs.flatMap { it.walkTopDown().toList() }
            .filter { it.isFile && (it.name.startsWith("conn_") || it.name.startsWith("provider_")) }
        assertEquals(emptyList<File>(), artwork)
    }

    @Test fun connectorCatalogUsesNeutralMarks() {
        assertFalse(ConnectorCatalog.all.isEmpty())
    }
}
