package app.clodex.harness.updates

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal fun copyUpdateBytes(input: InputStream, output: OutputStream, expected: Long,
    checkActive: () -> Unit = {}, onProgress: (Long) -> Unit,
) {
    require(expected in 1..MAX_APK_BYTES)
    val bytes = ByteArray(32 * 1024)
    var count = 0L
    while (true) {
        checkActive()
        val n = input.read(bytes)
        if (n == -1) break
        count += n
        if (count > expected) throw IOException("APK too large")
        output.write(bytes, 0, n)
        onProgress(count)
    }
    if (count != expected) throw IOException("Incomplete APK")
}
