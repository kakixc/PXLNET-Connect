package io.nekohasekai.sfa.vendor

import io.nekohasekai.sfa.update.UpdateTrack
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Selects a complete published release. GitHub's /releases/latest excludes prereleases. */
internal object GitHubReleaseSelection {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Asset(
        val name: String,
        @SerialName("browser_download_url") val downloadUrl: String,
    )

    @Serializable
    private data class Release(
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val pageUrl: String,
        val body: String? = null,
        val draft: Boolean,
        val prerelease: Boolean,
        val assets: List<Asset>,
    )

    data class Candidate(
        val versionName: String,
        val metadataUrl: String,
        val apkUrl: String,
        val pageUrl: String,
        val prerelease: Boolean,
        val releaseNotes: String?,
    )

    fun select(
        releasesJson: String,
        track: UpdateTrack,
        currentVersion: String,
        currentAbi: String,
        isNewer: (String, String) -> Boolean,
    ): Candidate? = json.decodeFromString<List<Release>>(releasesJson)
        .asSequence()
        .filter { release ->
            !release.draft && (track == UpdateTrack.BETA ||
                (!release.prerelease && !release.tagName.contains(Regex("-(?:alpha|beta|rc)", RegexOption.IGNORE_CASE))))
        }
        .mapNotNull { release ->
            val version = release.tagName.removePrefix("v")
            if (!isNewer(version, currentVersion)) return@mapNotNull null
            val metadata = release.assets.firstOrNull { it.name == "SFA-version-metadata.json" }
                ?: return@mapNotNull null
            val preferredAbi = if (currentAbi == "arm64-v8a") "arm64-v8a" else "universal"
            val apk = release.assets.firstOrNull {
                it.name == "PXLNET-Connect-$version-$preferredAbi.apk"
            } ?: release.assets.firstOrNull {
                it.name == "PXLNET-Connect-$version-universal.apk"
            } ?: return@mapNotNull null
            val betaVersion = version.contains(Regex("-(?:alpha|beta|rc)", RegexOption.IGNORE_CASE))
            Candidate(
                version,
                metadata.downloadUrl,
                apk.downloadUrl,
                release.pageUrl,
                release.prerelease || betaVersion,
                release.body?.take(12_000)?.takeIf(String::isNotBlank),
            )
        }
        .maxWithOrNull { a, b ->
            when {
                isNewer(a.versionName, b.versionName) -> 1
                isNewer(b.versionName, a.versionName) -> -1
                else -> 0
            }
        }
}
