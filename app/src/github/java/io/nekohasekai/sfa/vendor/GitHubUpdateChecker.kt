package io.nekohasekai.sfa.vendor

import android.os.Build
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.ktx.unwrap
import io.nekohasekai.sfa.update.UpdateInfo
import io.nekohasekai.sfa.update.UpdateTrack
import io.nekohasekai.sfa.utils.HTTPClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.io.IOException

class GitHubUpdateChecker : Closeable {
    companion object {
        private const val RELEASES_URL = "https://api.github.com/repos/kakixc/PXLNET-Connect/releases?per_page=30"
    }

    private val client = Libbox.newHTTPClient().apply {
        modernTLS()
        keepAlive()
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Suppress("UNUSED_PARAMETER")
    fun checkUpdate(track: UpdateTrack, githubToken: String): UpdateInfo? = try {
        checkLatestRelease(track)
    } catch (exception: Exception) {
        throw IOException(
            "Не удалось проверить обновление. Проверьте интернет и попробуйте немного позже.",
            exception,
        )
    }

    private fun checkLatestRelease(track: UpdateTrack): UpdateInfo? {
        val request = client.newRequest()
        request.setURL(RELEASES_URL)
        request.setUserAgent(HTTPClient.userAgent)
        val response = request.execute()
        val release = GitHubReleaseSelection.select(
            response.content.unwrap,
            track,
            BuildConfig.VERSION_NAME.removePrefix("v"),
            Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            ::isNewerThan,
        ) ?: return null
        val metadataRequest = client.newRequest()
        metadataRequest.setURL(release.metadataUrl)
        metadataRequest.setUserAgent(HTTPClient.userAgent)
        val metadata = json.decodeFromString<VersionMetadata>(metadataRequest.execute().content.unwrap)
        if (metadata.versionName.removePrefix("v") != release.versionName ||
            metadata.versionCode <= BuildConfig.VERSION_CODE
        ) return null

        return UpdateInfo(
            versionCode = metadata.versionCode,
            versionName = release.versionName,
            downloadUrl = release.apkUrl,
            releaseUrl = release.pageUrl,
            releaseNotes = null,
            isPrerelease = release.prerelease,
        )
    }

    private fun isNewerThan(candidate: String, current: String): Boolean =
        runCatching { Libbox.compareSemver(candidate, current) }.getOrDefault(false)

    override fun close() {
        client.close()
    }

    @Serializable
    data class VersionMetadata(
        @SerialName("version_code") val versionCode: Int = 0,
        @SerialName("version_name") val versionName: String = "",
    )
}
