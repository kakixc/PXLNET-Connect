package io.nekohasekai.sfa.bg

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.utils.HTTPClient
import io.nekohasekai.sfa.utils.PxlRoutingPreferences
import io.nekohasekai.sfa.utils.PxlSubscriptionConverter
import io.nekohasekai.sfa.utils.PxlXhttpSidecar
import io.nekohasekai.sfa.utils.SubscriptionInfoStore
import kotlinx.coroutines.CancellationException
import java.io.File
import java.util.Date
import java.util.concurrent.TimeUnit

class UpdateProfileWork {
    companion object {
        private const val WORK_NAME = "UpdateProfile"
        private const val TAG = "UpdateProfileWork"

        suspend fun reconfigureUpdater() {
            runCatching {
                reconfigureUpdater0()
            }.onFailure {
                Log.e(TAG, "reconfigureUpdater", it)
            }
        }

        private suspend fun reconfigureUpdater0() {
            val remoteProfiles =
                ProfileManager.list()
                    .filter { it.typed.type == TypedProfile.Type.Remote && it.typed.autoUpdate }
            if (remoteProfiles.isEmpty()) {
                WorkManager.getInstance(Application.application).cancelUniqueWork(WORK_NAME)
                return
            }

            val intervalMinutes = Settings.subscriptionUpdateIntervalMinutes
            val nowSeconds = System.currentTimeMillis() / 1000L
            val minInitDelay =
                remoteProfiles.minOf { (intervalMinutes * 60L) - (nowSeconds - (it.typed.lastUpdated.time / 1000L)) }
            WorkManager.getInstance(Application.application).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequest.Builder(UpdateTask::class.java, intervalMinutes.toLong(), TimeUnit.MINUTES)
                    .apply {
                        if (minInitDelay > 0) setInitialDelay(minInitDelay, TimeUnit.SECONDS)
                        setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
                    }
                    .build(),
            )
        }

        suspend fun refreshNow(context: Context): Boolean = refreshProfiles(context, force = true)

        internal fun isDue(lastUpdatedAtMillis: Long, nowMillis: Long, intervalMinutes: Int, force: Boolean): Boolean =
            force || nowMillis - lastUpdatedAtMillis >= intervalMinutes * 60_000L

        private suspend fun refreshProfiles(context: Context, force: Boolean): Boolean {
            var selectedProfileUpdated = false
            val remoteProfiles =
                ProfileManager.list()
                    .filter { it.typed.type == TypedProfile.Type.Remote && it.typed.autoUpdate }
            if (remoteProfiles.isEmpty()) return true
            var success = true
            val selectedProfile = Settings.selectedProfile
            for (profile in remoteProfiles) {
                if (!isDue(
                        profile.typed.lastUpdated.time,
                        System.currentTimeMillis(),
                        Settings.subscriptionUpdateIntervalMinutes,
                        force,
                    )
                ) {
                    continue
                }
                try {
                    val file = File(profile.typed.path)
                    val previousXhttp = PxlXhttpSidecar.read(file)
                    val response = HTTPClient().use { it.get(profile.typed.remoteURL) }
                    if (SubscriptionInfoStore.recordExplicitInactiveResponse(
                            context, profile.id, response.subscriptionStatus,
                        )
                    ) {
                        check(PxlXhttpSidecar.remove(File(profile.typed.path))) { "Cannot remove inactive XHTTP profile snapshot" }
                    }
                    val conversion =
                        PxlSubscriptionConverter.convert(
                            response.content,
                            PxlRoutingPreferences.isSmartRouting(context),
                            response.subscriptionStatus,
                            response.subscriptionFormat,
                            response.omittedXhttpCount,
                        )
                    val previous = file.readText()
                    val content = PxlSubscriptionConverter.preserveSelectedServer(conversion.config, previous)
                    Libbox.checkConfig(content)
                    check(PxlXhttpSidecar.replaceConfig(file, content, response)) { "Cannot save XHTTP profile snapshot" }
                    val xhttpChanged = (previousXhttp != null || response.xhttpLinks.isNotEmpty()) &&
                        (previousXhttp?.links != response.xhttpLinks ||
                            previousXhttp.smartRevisions != response.smartRevisions)
                    if (previous != content || xhttpChanged) {
                        if (profile.id == selectedProfile) {
                            selectedProfileUpdated = true
                        }
                    }
                    profile.typed.lastUpdated = Date()
                    SubscriptionInfoStore.save(context, profile.id, response.subscriptionUserInfo)
                    SubscriptionInfoStore.saveSmartStatus(context, profile.id, response.subscriptionStatus)
                    SubscriptionInfoStore.saveSkippedXhttp(context, profile.id, conversion.skippedXhttpCount)
                    ProfileManager.update(profile)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "update profile ${profile.name}", e)
                    success = false
                }
            }
            if (selectedProfileUpdated) {
                runCatching {
                    Libbox.newStandaloneCommandClient().serviceReload()
                }
            }
            return success
        }
    }

    class UpdateTask(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result =
            if (refreshProfiles(applicationContext, force = false)) Result.success() else Result.retry()
    }
}
