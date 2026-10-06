package io.nekohasekai.sfa.vendor

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.update.UpdateInfo
import io.nekohasekai.sfa.update.UpdateSource

/** Download only; installation always requires a separate user action. */
class UpdateDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        private const val WORK_NAME = "PxlPreDownloadUpdate"
        private const val KEY_VERSION_CODE = "version_code"

        fun schedule(context: Context, update: UpdateInfo) {
            val network = if (Settings.updatePreDownloadUnmetered) NetworkType.UNMETERED else NetworkType.CONNECTED
            val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
                .setInputData(workDataOf(KEY_VERSION_CODE to update.versionCode))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        if (!Settings.updatePreDownloadEnabled ||
            UpdateSource.fromString(Settings.updateSource) != UpdateSource.GITHUB
        ) return Result.success()
        val current = UpdateInfo.fromJson(Settings.cachedUpdateInfo) ?: return Result.success()
        if (current.versionCode != inputData.getInt(KEY_VERSION_CODE, -1)) {
            return Result.success()
        }
        return try {
            ApkDownloader().use { it.download(current) }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
