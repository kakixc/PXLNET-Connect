package io.nekohasekai.sfa.vendor

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.update.UpdateState
import java.io.File

object SystemPackageInstaller {
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    // A regular sideloaded app cannot silently install updates with PackageInstaller.
    fun canSystemSilentInstall(): Boolean = false

    /**
     * Android 8+ requires a per-source permission before our app can open the package installer.
     * Keep the update dialog visible while the settings screen is open so the user can tap Update
     * again after granting the permission; the already downloaded APK is reused.
     */
    fun ensureInstallPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()) {
            return true
        }

        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return false
    }

    fun install(context: Context, apkFile: File) {
        require(apkFile.isFile && apkFile.length() > 0L) {
            context.getString(R.string.pxlnet_update_apk_missing)
        }
        check(ensureInstallPermission(context)) {
            context.getString(R.string.pxlnet_update_install_permission)
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.cache",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            clipData = ClipData.newRawUri("PXLNET update", apkUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        check(intent.resolveActivity(context.packageManager) != null) {
            context.getString(R.string.pxlnet_update_installer_missing)
        }

        UpdateState.setInstallStatus(UpdateState.InstallStatus.Installing)
        context.startActivity(intent)
    }
}
