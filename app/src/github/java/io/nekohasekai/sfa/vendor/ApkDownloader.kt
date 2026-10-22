package io.nekohasekai.sfa.vendor

import io.nekohasekai.libbox.HTTPResponseWriteToProgressHandler
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.update.UpdateApkVerifier
import io.nekohasekai.sfa.update.UpdateInfo
import io.nekohasekai.sfa.update.UpdateState
import io.nekohasekai.sfa.utils.HTTPClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

class ApkDownloader : Closeable {
    companion object {
        // Foreground downloads and WorkManager jobs share the same APK cache.
        private val cacheMutex = Mutex()
    }

    private val client = Libbox.newHTTPClient().apply {
        modernTLS()
        keepAlive()
    }

    suspend fun download(update: UpdateInfo): File = withContext(Dispatchers.IO) {
        cacheMutex.withLock {
            val cacheDir = File(Application.application.cacheDir, "updates")
            check(cacheDir.isDirectory || cacheDir.mkdirs()) { "Cannot create update cache" }
            val apkFile = File(cacheDir, "update-${update.versionCode}.apk")
            if (UpdateApkVerifier.isValid(Application.application, apkFile, update)) {
                UpdateState.saveApkPath(apkFile)
                return@withLock apkFile
            }
            if (apkFile.exists()) apkFile.delete()
            val partialFile = File(cacheDir, "update-${update.versionCode}-partial.apk")
            if (partialFile.exists()) partialFile.delete()

            try {
                val request = client.newRequest()
                request.setUserAgent(HTTPClient.userAgent)
                request.setURL(update.downloadUrl)

                val response = request.execute()
                response.writeToWithProgress(
                    partialFile.absolutePath,
                    object : HTTPResponseWriteToProgressHandler {
                        override fun update(progress: Long, total: Long) {
                            UpdateState.downloadProgress.value =
                                if (total > 0) progress.toFloat() / total.toFloat() else null
                        }
                    },
                )

                check(UpdateApkVerifier.isValid(Application.application, partialFile, update)) {
                    "Downloaded APK failed package, version or signature verification"
                }
                check(partialFile.renameTo(apkFile)) { "Cannot save verified update" }
                UpdateState.saveApkPath(apkFile)
                apkFile
            } finally {
                if (partialFile.exists()) partialFile.delete()
            }
        }
    }

    override fun close() {
        client.close()
    }
}
