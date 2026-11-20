package app.clodex.harness.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseProtocolTest {
    @Test fun disabledOrInvalidRepositoryNeverRequestsMetadata() {
        listOf("", "  ", "https://github.com/org/repo", "org/../repo", "org/..", "org/repo?x").forEach { repo ->
            assertNull(checkRelease(repo, 1) { error("Unexpected network request") })
        }
    }

    @Test fun stableReleaseUsesOnlyTheConfiguredRepositoryAndPreservesNotesAsText() {
        val offer = checkRelease("clodex-org/android", 1) { url ->
            assertEquals("https://api.github.com/repos/clodex-org/android/releases/latest", url)
            release()
        }
        assertEquals(2L, offer?.versionCode)
        assertEquals("<script>x</script> [link](https://example.org)", offer?.notes)
    }

    @Test fun draftPrereleaseAndNonIncreasingCodesAreNotOffered() {
        assertNull(checkRelease("clodex-org/android", 1) { release(draft = true) })
        assertNull(checkRelease("clodex-org/android", 1) { release(prerelease = true) })
        assertNull(checkRelease("clodex-org/android", 2) { release() })
        assertNull(checkRelease("clodex-org/android", 3) { release() })
    }

    @Test fun malformedAndAmbiguousAssetsAreNotOffered() {
        listOf(
            "", asset(name = "clodex-02.apk"), asset(name = "clodex-2147483648.apk"),
            asset(url = "https://example.org/clodex-2.apk"),
            asset(url = "https://github.com/other/android/releases/download/v1.1/clodex-2.apk"),
            asset(url = "$download?foo=bar"), asset(size = 0), "${asset()},${asset()}",
        ).forEach { assets -> assertNull(checkRelease("clodex-org/android", 1) { release(assets = assets) }) }
    }

    @Test fun downloadRedirectsMustStayOnTrustedHttpsHosts() {
        assertTrue(isReleaseDownloadUrl("https://release-assets.githubusercontent.com/a?sig=x"))
        assertFalse(isReleaseDownloadUrl("http://github.com/a"))
        assertFalse(isReleaseDownloadUrl("https://github.com.attacker.example/a"))
        assertFalse(isReleaseDownloadUrl("https://name@github.com/a"))
        assertFalse(isReleaseDownloadUrl("https://github.com:8443/a"))
    }

    @Test fun packageVersionAndExactNonemptyCurrentSignerSetAreRequired() {
        val installed = ApkIdentity("app.clodex.harness", 2, setOf("current", "second"))
        val candidate = installed.copy(versionCode = 3, signers = setOf("second", "current"))
        assertTrue(matchesUpdate(installed, candidate, 3))
        assertFalse(matchesUpdate(installed, null, 3))
        assertFalse(matchesUpdate(installed, candidate.copy(packageName = "other.app"), 3))
        assertFalse(matchesUpdate(installed, candidate.copy(versionCode = 2), 2))
        assertFalse(matchesUpdate(installed, candidate, 4))
        assertFalse(matchesUpdate(installed, candidate.copy(signers = setOf("current")), 3))
        assertFalse(matchesUpdate(installed, candidate.copy(signers = setOf("current", "old")), 3))
        assertFalse(matchesUpdate(installed.copy(signers = emptySet()), candidate.copy(signers = emptySet()), 3))
    }

    private val download = "https://github.com/clodex-org/android/releases/download/v1.1/clodex-2.apk"

    private fun asset(name: String = "clodex-2.apk", url: String = download, size: Int = 12345) =
        """{"name":"$name","state":"uploaded","size":$size,"browser_download_url":"$url"}"""

    private fun release(draft: Boolean = false, prerelease: Boolean = false, assets: String = asset()) =
        """{"draft":$draft,"prerelease":$prerelease,"tag_name":"v1.1","body":"<script>x</script> [link](https://example.org)","assets":[$assets]}"""
}
