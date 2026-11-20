package app.clodex.harness

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okio.BufferedSink

internal fun newScheduledHttp(configure: OkHttpClient.Builder.() -> Unit = {}): HttpClient = HttpClient(OkHttp) {
    followRedirects = false
    engine {
        config {
            configure()
            retryOnConnectionFailure(false)
            followRedirects(false)
            followSslRedirects(false)
        }
        addInterceptor { chain ->
            val request = chain.request()
            val body = request.body
            // Disabling connection retries alone still permits HTTP follow-ups (e.g. 503 Retry-After: 0).
            val once = body?.let {
                object : RequestBody() {
                    override fun contentType() = it.contentType()
                    override fun contentLength() = it.contentLength()
                    override fun writeTo(sink: BufferedSink) = it.writeTo(sink)
                    override fun isOneShot() = true
                }
            }
            chain.proceed(if (once == null) request else request.newBuilder().method(request.method, once).build())
        }
    }
    install(HttpTimeout) { requestTimeoutMillis = 300_000; connectTimeoutMillis = 20_000; socketTimeoutMillis = 120_000 }
}
