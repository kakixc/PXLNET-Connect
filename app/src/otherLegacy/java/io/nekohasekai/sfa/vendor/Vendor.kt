package io.nekohasekai.sfa.vendor

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.camera.core.ImageAnalysis
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.RootClient
import io.nekohasekai.sfa.compose.screen.qrscan.QRCodeCropArea
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.update.UpdateCheckException
import io.nekohasekai.sfa.update.UpdateInfo
import io.nekohasekai.sfa.update.UpdateState
import io.nekohasekai.sfa.update.UpdateApkVerifier
import io.nekohasekai.sfa.update.UpdateSource
import io.nekohasekai.sfa.update.UpdateTrack

object Vendor : VendorInterface {
    private const val TAG = "Vendor"

    override fun checkUpdate(activity: Activity, byUser: Boolean) {
        try {
            val updateInfo = checkUpdateAsync()
            if (updateInfo != null) {
                activity.runOnUiThread {
                    showUpdateDialog(activity, updateInfo)
                }
            } else if (byUser) {
                activity.runOnUiThread {
                    showNoUpdatesDialog(activity)
                }
            }
        } catch (e: UpdateCheckException.TrackNotSupported) {
            Log.d(TAG, "checkUpdate: track not supported")
            if (byUser) {
                activity.runOnUiThread {
                    showTrackNotSupportedDialog(activity)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkUpdate: ", e)
            if (byUser) {
                activity.runOnUiThread {
                    showNoUpdatesDialog(activity)
                }
            }
        }
    }

    private fun showUpdateDialog(activity: Activity, updateInfo: UpdateInfo) {
        val message = buildString {
            append(activity.getString(R.string.new_version_available, updateInfo.versionName))
            if (!updateInfo.releaseNotes.isNullOrBlank()) {
                append("\n\n")
                append(updateInfo.releaseNotes.take(500))
                if (updateInfo.releaseNotes.length > 500) {
                    append("...")
                }
            }
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.check_update)
            .setMessage(message)
            .setPositiveButton(R.string.update) { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.releaseUrl))
                activity.startActivity(intent)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showNoUpdatesDialog(activity: Activity) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.check_update)
            .setMessage(R.string.no_updates_available)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showTrackNotSupportedDialog(activity: Activity) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.check_update)
            .setMessage(R.string.update_track_not_supported)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    override fun createQRCodeAnalyzer(
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit,
        onCropArea: ((QRCodeCropArea?) -> Unit)?,
    ): ImageAnalysis.Analyzer? = null

    override val hasCustomUpdate = true

    override fun checkUpdateAsync(): UpdateInfo? {
        val track = UpdateTrack.fromString(Settings.updateTrack)
        return GitHubUpdateChecker().use { checker ->
            checker.checkUpdate(track, Settings.githubToken)
        }
    }

    override fun scheduleAutoUpdate() {
        UpdateWorker.schedule(io.nekohasekai.sfa.Application.application)
    }

    override fun scheduleUpdatePreDownload(context: android.content.Context, update: UpdateInfo?) {
        if (Settings.updatePreDownloadEnabled && update != null &&
            UpdateSource.fromString(Settings.updateSource) == UpdateSource.GITHUB
        ) {
            UpdateDownloadWorker.schedule(context, update)
        } else {
            UpdateDownloadWorker.cancel(context)
        }
    }

    override suspend fun verifySilentInstallMethod(method: String): Boolean = when (method) {
        "PACKAGE_INSTALLER" -> {
            ApkInstaller.canSystemSilentInstall()
        }
        "ROOT" -> RootClient.checkRootAvailable()
        else -> false
    }

    override fun ensureUpdateInstallPermission(context: android.content.Context): Boolean =
        SystemPackageInstaller.ensureInstallPermission(context)

    override suspend fun downloadAndInstall(context: android.content.Context, downloadUrl: String) {
        val update = requireNotNull(UpdateState.updateInfo.value?.takeIf { it.downloadUrl == downloadUrl }) {
            "Update information is no longer current"
        }
        val cachedApk = UpdateState.cachedApkFile.value
        val apkFile = if (cachedApk != null && UpdateApkVerifier.isValid(context, cachedApk, update)) {
            cachedApk
        } else {
            UpdateState.clearCachedApkPath()
            ApkDownloader().use { it.download(update) }
        }
        ApkInstaller.install(context, apkFile)
    }
}
