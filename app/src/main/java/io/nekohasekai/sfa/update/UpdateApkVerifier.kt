package io.nekohasekai.sfa.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import io.nekohasekai.sfa.BuildConfig
import java.io.File

/** Rejects stale, foreign or differently signed downloads before they enter the update cache. */
object UpdateApkVerifier {
    @Suppress("DEPRECATION")
    fun isValid(context: Context, file: File, update: UpdateInfo): Boolean = runCatching {
        if (!file.isFile || file.length() == 0L || update.versionCode <= BuildConfig.VERSION_CODE) {
            return@runCatching false
        }

        val packageManager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val archive = packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: return@runCatching false
        val installed = packageManager.getPackageInfo(context.packageName, flags)
        val archiveVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archive.longVersionCode else archive.versionCode.toLong()
        val installedVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) installed.longVersionCode else installed.versionCode.toLong()
        if (archive.packageName != context.packageName ||
            archiveVersion != update.versionCode.toLong() ||
            archiveVersion <= installedVersion
        ) {
            return@runCatching false
        }

        sameCurrentSigners(archive, installed)
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun sameCurrentSigners(archive: PackageInfo, installed: PackageInfo): Boolean {
        val archiveSigners = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.signingInfo?.apkContentsSigners?.toSet()
        } else {
            archive.signatures?.toSet()
        }
        val installedSigners = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            installed.signingInfo?.apkContentsSigners?.toSet()
        } else {
            installed.signatures?.toSet()
        }
        return !archiveSigners.isNullOrEmpty() && archiveSigners == installedSigners
    }
}
