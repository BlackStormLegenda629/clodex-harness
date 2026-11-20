package app.clodex.harness.updates

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val MAX_APK_BYTES = 200L * 1024 * 1024
private val repoPattern = Regex("[A-Za-z0-9-]{1,39}/[A-Za-z0-9_.-]{1,100}")
private val apkPattern = Regex("clodex-([1-9][0-9]{0,9})\\.apk")

internal fun validUpdatesRepo(repo: String) = repoPattern.matches(repo) && repo.substringAfter('/') !in setOf(".", "..")

internal data class ReleaseOffer(val versionCode: Long, val notes: String, val url: String, val size: Long)

internal fun checkRelease(repo: String, installedVersion: Long, fetch: (String) -> String?): ReleaseOffer? {
    if (!validUpdatesRepo(repo)) return null
    val text = fetch("https://api.github.com/repos/$repo/releases/latest") ?: return null
    val release = Json.parseToJsonElement(text) as? JsonObject ?: return null
    for (flag in listOf("draft", "prerelease")) {
        val value = release[flag] as? JsonPrimitive ?: return null
        if (value.isString || value.booleanOrNull != false) return null
    }
    val tag = release.string("tag_name")?.takeIf { it.isNotBlank() } ?: return null
    val assets = release["assets"] as? JsonArray ?: return null
    val asset = assets.filterIsInstance<JsonObject>().singleOrNull { apkPattern.matches(it.string("name").orEmpty()) }
        ?: return null
    val name = asset.string("name") ?: return null
    val code = apkPattern.matchEntire(name)?.groupValues?.get(1)?.toIntOrNull()?.toLong() ?: return null
    if (code <= installedVersion || asset.string("state") != "uploaded") return null
    val size = (asset["size"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull ?: return null
    if (size !in 1..MAX_APK_BYTES) return null
    val url = asset.string("browser_download_url") ?: return null
    val parsed = url.toHttpUrlOrNull() ?: return null
    val path = parsed.pathSegments
    if (!isReleaseDownloadUrl(url) || parsed.host != "github.com" || parsed.query != null ||
        path.size != 6 || !path.take(2).joinToString("/").equals(repo, ignoreCase = true) ||
        path.drop(2) != listOf("releases", "download", tag, name)
    ) return null
    return ReleaseOffer(code, release.string("body").orEmpty().take(24_000), parsed.toString(), size)
}

private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun isReleaseDownloadUrl(url: String): Boolean {
    val parsed = url.toHttpUrlOrNull() ?: return false
    return parsed.isHttps && parsed.port == 443 && parsed.fragment == null &&
        parsed.username.isEmpty() && parsed.password.isEmpty() &&
        parsed.host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
}

internal data class ApkIdentity(val packageName: String, val versionCode: Long, val signers: Set<String>)

internal fun matchesUpdate(installed: ApkIdentity, candidate: ApkIdentity?, advertisedVersion: Long): Boolean =
    candidate != null && candidate.packageName == installed.packageName &&
        candidate.versionCode > installed.versionCode && candidate.versionCode == advertisedVersion &&
        installed.signers.isNotEmpty() && candidate.signers == installed.signers
