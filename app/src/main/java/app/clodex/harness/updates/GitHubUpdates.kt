package app.clodex.harness.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import app.clodex.harness.BuildConfig
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

internal object GitHubUpdates {
    val enabled = validUpdatesRepo(BuildConfig.UPDATES_REPO)
    private const val APK_MIME = "application/vnd.android.package-archive"
    private const val META_LIMIT = 1024L * 1024
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
    private val downloadClient = client.newBuilder().callTimeout(2, TimeUnit.MINUTES).build()

    suspend fun check(context: Context): ReleaseOffer? = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext null
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
        checkRelease(BuildConfig.UPDATES_REPO, PackageInfoCompat.getLongVersionCode(installed)) { url ->
            val request = Request.Builder().url(url)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 404) return@use null
                if (response.code != 200) throw IOException("Release lookup failed")
                val source = response.body?.source() ?: throw IOException("Missing release response")
                if (source.request(META_LIMIT + 1)) throw IOException("Release response too large")
                source.readUtf8()
            }
        }
    }

    suspend fun install(context: Context, offer: ReleaseOffer, onProgress: (Long) -> Unit, canLaunch: () -> Boolean) {
        val pm = context.packageManager
        check(enabled && pm.canRequestPackageInstalls())
        var staged: File? = null
        var verified: File? = null
        var handedOff = false
        try {
            withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "verified-updates")
                val now = System.currentTimeMillis()
                dir.listFiles()?.filter { now - it.lastModified() > TimeUnit.DAYS.toMillis(1) }?.forEach { it.delete() }
                val temp = File.createTempFile("clodex-", ".apk", context.cacheDir)
                staged = temp
                download(offer, temp, onProgress)
                verify(context, temp, offer.versionCode)
                if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create update cache")
                val apk = File(dir, temp.name)
                if (!temp.renameTo(apk)) throw IOException("Cannot stage verified update")
                verified = apk
            }

            check(canLaunch() && pm.canRequestPackageInstalls())
            val apk = checkNotNull(verified)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
            @Suppress("DEPRECATION") // This opens Android's confirmation UI instead of committing an install session.
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setDataAndType(uri, APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val installer = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY or PackageManager.MATCH_SYSTEM_ONLY)
                .firstOrNull()?.activityInfo ?: throw IOException("No system package installer")
            intent.setClassName(installer.packageName, installer.name)
            context.startActivity(intent)
            handedOff = true
        } finally {
            staged?.delete()
            if (!handedOff) verified?.delete()
        }
    }

    private suspend fun download(offer: ReleaseOffer, output: File, onProgress: (Long) -> Unit) {
        val coroutine = currentCoroutineContext()
        var url = offer.url.toHttpUrl()
        repeat(5) {
            coroutine.ensureActive()
            require(isReleaseDownloadUrl(url.toString()))
            val request = Request.Builder().url(url).build()
            downloadClient.newCall(request).execute().use { response ->
                if (response.code in setOf(301, 302, 303, 307, 308)) {
                    url = response.header("Location")?.let { url.resolve(it) } ?: throw IOException("Invalid redirect")
                    return@use
                }
                if (response.code != 200) throw IOException("Asset download failed")
                val body = response.body ?: throw IOException("Missing APK")
                if (body.contentLength() != -1L && body.contentLength() != offer.size) throw IOException("Wrong asset size")
                output.outputStream().buffered().use { out ->
                    copyUpdateBytes(body.byteStream(), out, offer.size, { coroutine.ensureActive() }, onProgress)
                }
                return
            }
        }
        throw IOException("Too many redirects")
    }

    @Suppress("DEPRECATION") // API 26–27 lack SigningInfo (and do not support signer rotation).
    private fun verify(context: Context, apk: File, advertisedVersion: Long) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = pm.getPackageInfo(context.packageName, flags)
        // Older Android archive parsers only collect certificates with GET_SIGNATURES.
        val candidate = pm.getPackageArchiveInfo(apk.absolutePath, flags or PackageManager.GET_SIGNATURES)
        if (!matchesUpdate(installed.identity(), candidate?.identity(), advertisedVersion)) throw UnverifiedApkException()
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.identity(): ApkIdentity {
        val certs = if (Build.VERSION.SDK_INT >= 28) signingInfo?.apkContentsSigners else signatures
        val signers = certs.orEmpty().map { it.toCharsString() }.toSet()
        return ApkIdentity(packageName, PackageInfoCompat.getLongVersionCode(this), signers)
    }
}

internal class UnverifiedApkException : IOException()
