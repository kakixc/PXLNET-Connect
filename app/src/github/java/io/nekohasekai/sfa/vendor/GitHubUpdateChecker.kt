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
        private const val REPOSITORY_URL = "https://github.com/kakixc/PXLNET-Connect"
        private const val METADATA_URL =
            "$REPOSITORY_URL/releases/latest/download/SFA-version-metadata.json"
    }

    private val client = Libbox.newHTTPClient().apply {
        modernTLS()
        keepAlive()
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Suppress("UNUSED_PARAMETER")
    fun checkUpdate(track: UpdateTrack, githubToken: String): UpdateInfo? = try {
        checkLatestRelease()
    } catch (exception: Exception) {
        throw IOException(
            "Не удалось проверить обновление. Проверьте интернет и попробуйте немного позже.",
            exception,
        )
    }

    private fun checkLatestRelease(): UpdateInfo? {
        val request = client.newRequest()
        request.setURL(METADATA_URL)
        request.setUserAgent(HTTPClient.userAgent)

        val response = request.execute()
        val metadata = json.decodeFromString<VersionMetadata>(response.content.unwrap)
        if (!isNewerThanCurrent(metadata.versionName)) return null

        val versionName = metadata.versionName.removePrefix("v")
        val tagName = "v$versionName"
        val currentAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        val assetSuffix = if (currentAbi == "arm64-v8a") "arm64-v8a" else "universal"
        val assetName = "PXLNET-Connect-$versionName-$assetSuffix.apk"

        return UpdateInfo(
            versionCode = metadata.versionCode,
            versionName = versionName,
            downloadUrl = "$REPOSITORY_URL/releases/download/$tagName/$assetName",
            releaseUrl = "$REPOSITORY_URL/releases/latest",
            releaseNotes = null,
            isPrerelease = versionName.contains("beta", ignoreCase = true) ||
                versionName.contains("alpha", ignoreCase = true) ||
                versionName.contains("rc", ignoreCase = true),
        )
    }

    private fun isNewerThanCurrent(versionName: String): Boolean =
        Libbox.compareSemver(versionName.removePrefix("v"), BuildConfig.VERSION_NAME.removePrefix("v"))

    override fun close() {
        client.close()
    }

    @Serializable
    data class VersionMetadata(
        @SerialName("version_code") val versionCode: Int = 0,
        @SerialName("version_name") val versionName: String = "",
    )
}
