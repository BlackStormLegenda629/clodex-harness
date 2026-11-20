package app.clodex.harness

import app.clodex.harness.ui.screens.isHtmlReply
import app.clodex.harness.ui.screens.htmlExportName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlReplyTest {
    @Test fun exportedHtmlUsesABasenameAndAnHtmlExtension() {
        assertEquals("report.HTM", htmlExportName("../../report.HTM"))
        assertEquals("report.html", htmlExportName("folder\\report"))
        assertEquals("index.html", htmlExportName(".."))
    }
    @Test fun onlyNonEmptyHtmlBlocksOpenAsArtifacts() {
        assertTrue(isHtmlReply("HTML", "<h1>Example</h1>"))
        assertTrue(isHtmlReply("htm", "<p>Example</p>"))
        assertTrue(isHtmlReply("xhtml", "<p>Example</p>"))
        assertFalse(isHtmlReply("html", " \n"))
        assertFalse(isHtmlReply("xml", "<p>Example</p>"))
    }
}
