package app.clodex.harness

import app.clodex.harness.data.agent.postSse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ScheduledTransportTest {
    @Test fun disconnectAfterReadingPostDoesNotReplayIt() = assertSinglePost(null, IOException::class.java)

    @Test fun immediateRetryAfterHeaderDoesNotReplayPost() = assertSinglePost(
        "HTTP/1.1 503 Service Unavailable\r\nRetry-After: 0\r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
        app.clodex.harness.data.ClaudeException::class.java,
    )

    private fun assertSinglePost(response: String?, expected: Class<out Exception>) {
        val loopback = InetAddress.getByName("127.0.0.1")
        ServerSocket(0, 4, loopback).use { server ->
            val executor = Executors.newSingleThreadExecutor()
            try {
                val received = executor.submit<Int> {
                    server.soTimeout = 5_000
                    server.accept().use { socket ->
                        socket.soTimeout = 5_000
                        val input = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                        assertTrue(input.readLine().startsWith("POST /stream "))
                        val headers = generateSequence(input::readLine).takeWhile { it.isNotEmpty() }.toList()
                        val length = headers.first { it.startsWith("Content-Length:", ignoreCase = true) }.substringAfter(':').trim().toInt()
                        val body = CharArray(length)
                        for (i in body.indices) body[i] = input.read().also { assertTrue(it >= 0) }.toChar()
                        assertEquals("{\"message\":\"demo\"}", String(body))
                        response?.let { socket.getOutputStream().apply { write(it.toByteArray()); flush() } }
                    }
                    server.soTimeout = 700
                    try { server.accept().use { 2 } } catch (_: SocketTimeoutException) { 1 }
                }
                newScheduledHttp { dns(object : okhttp3.Dns {
                    override fun lookup(hostname: String) = listOf(loopback, loopback)
                }) }.use { http ->
                    val error = runBlocking {
                        runCatching { http.postSse("http://schedule.test:${server.localPort}/stream", emptyMap(), "{\"message\":\"demo\"}", 4096) { true } }.exceptionOrNull()
                    }
                    assertEquals(1, received.get(7, TimeUnit.SECONDS))
                    assertTrue("Expected ${expected.simpleName}, got $error", expected.isInstance(error))
                }
            } finally { executor.shutdownNow() }
        }
    }
}
