package app.clodex.harness

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingHtmlContractTest {
    private fun htmlFile(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        return listOfNotNull(File(cwd, "thinking.html"), cwd.parentFile?.let { File(it, "thinking.html") })
            .firstOrNull { it.isFile } ?: File(cwd, "thinking.html")
    }

    @Test fun standaloneFileContainsRequiredPublicApiAndTokens() {
        val file = htmlFile()
        assertTrue("thinking.html must exist at the project root", file.isFile)
        val html = file.readText()

        listOf(
            "function addStep(text)",
            "function finishThinking(seconds)",
            "#141414", "#1f1f1f", "#ececec", "#8a8a8a", "#d97757",
            "Escape", "1200", "prefers-reduced-motion", "aria-modal=\"true\"",
        ).forEach { required -> assertTrue("Missing $required", html.contains(required, ignoreCase = true)) }
    }
}
