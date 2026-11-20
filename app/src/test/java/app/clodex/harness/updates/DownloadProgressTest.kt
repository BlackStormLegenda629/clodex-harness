package app.clodex.harness.updates

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DownloadProgressTest {
    @Test fun reportsBytesActuallyWrittenAndPreservesPayload() {
        val payload = ByteArray(100_000) { it.toByte() }
        val output = ByteArrayOutputStream()
        val progress = mutableListOf<Long>()
        copyUpdateBytes(ByteArrayInputStream(payload), output, payload.size.toLong()) { count ->
            assertEquals(output.size().toLong(), count)
            progress.add(count)
        }
        assertEquals(listOf(32768L, 65536L, 98304L, 100000L), progress)
        assertArrayEquals(payload, output.toByteArray())
    }

    @Test fun rejectsTruncatedAndOversizedDownloads() {
        for (size in listOf(1, 3)) {
            assertThrows(IOException::class.java) {
                copyUpdateBytes(ByteArrayInputStream(ByteArray(size)), ByteArrayOutputStream(), 2) {}
            }
        }
    }
}
