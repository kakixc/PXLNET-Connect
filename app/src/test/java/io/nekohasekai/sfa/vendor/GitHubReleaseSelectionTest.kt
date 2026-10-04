package io.nekohasekai.sfa.vendor

import io.nekohasekai.sfa.update.UpdateTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubReleaseSelectionTest {
    private val releases = """
        [
          {
            "tag_name":"v0.6.4-beta", "draft":false, "prerelease":false,
            "html_url":"https://github.com/kakixc/PXLNET-Connect/releases/tag/v0.6.4-beta",
            "assets":[
              {"name":"SFA-version-metadata.json","browser_download_url":"https://example.test/beta/metadata"},
              {"name":"PXLNET-Connect-0.6.4-beta-arm64-v8a.apk","browser_download_url":"https://example.test/beta/arm64"},
              {"name":"PXLNET-Connect-0.6.4-beta-universal.apk","browser_download_url":"https://example.test/beta/universal"}
            ]
          },
          {
            "tag_name":"v0.6.3", "draft":false, "prerelease":false,
            "html_url":"https://github.com/kakixc/PXLNET-Connect/releases/tag/v0.6.3",
            "assets":[
              {"name":"SFA-version-metadata.json","browser_download_url":"https://example.test/stable/metadata"},
              {"name":"PXLNET-Connect-0.6.3-universal.apk","browser_download_url":"https://example.test/stable/universal"}
            ]
          }
        ]
    """.trimIndent()

    private val newer: (String, String) -> Boolean = { candidate, current ->
        candidate.removeSuffix("-beta").split('.').map(String::toInt)
            .zip(current.removeSuffix("-beta").split('.').map(String::toInt))
            .firstOrNull { (a, b) -> a != b }
            ?.let { (a, b) -> a > b }
            ?: (current.endsWith("-beta") && !candidate.endsWith("-beta"))
    }

    @Test
    fun betaChannelFindsPrereleaseAndCorrectAbi() {
        val selected = GitHubReleaseSelection.select(releases, UpdateTrack.BETA, "0.6.3-beta", "arm64-v8a", newer)
        assertEquals("0.6.4-beta", selected?.versionName)
        assertEquals("https://example.test/beta/arm64", selected?.apkUrl)
        assertEquals("https://example.test/beta/metadata", selected?.metadataUrl)
        assertEquals(true, selected?.prerelease)
    }

    @Test
    fun stableChannelDoesNotOfferBeta() {
        assertNull(GitHubReleaseSelection.select(releases, UpdateTrack.STABLE, "0.6.3", "arm64-v8a", newer))
    }

    @Test
    fun betaChannelFallsBackToUniversalOnOtherAbi() {
        val selected = GitHubReleaseSelection.select(releases, UpdateTrack.BETA, "0.6.3-beta", "x86_64", newer)
        assertEquals("https://example.test/beta/universal", selected?.apkUrl)
    }

    @Test
    fun currentVersionIsNotOfferedAgain() {
        assertNull(GitHubReleaseSelection.select(releases, UpdateTrack.BETA, "0.6.4-beta", "arm64-v8a", newer))
    }
}
