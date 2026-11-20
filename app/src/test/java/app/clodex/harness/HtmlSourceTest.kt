package app.clodex.harness

import app.clodex.harness.util.highlightHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlSourceTest {
    @Test fun highlightsMarkupWithoutChangingSource() {
        val raw = "<!-- note -->\n<a title=\"x > y\">Plain &amp; text</a>"
        val styled = highlightHtml(raw)
        assertEquals(raw, styled.text)
        val colors = styled.spanStyles.map { it.item.color }.distinct()
        assertTrue(colors.size >= 4)
        val prose = raw.indexOf("Plain")
        assertTrue(styled.spanStyles.none { prose in it.start until it.end })
    }
}
