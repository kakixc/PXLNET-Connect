package io.nekohasekai.sfa.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.MainActivity
import io.nekohasekai.sfa.database.Settings
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

object PxlSubscriptionReminderWork {
    private const val PERIODIC_WORK = "pxlnet-subscription-reminders"
    private const val IMMEDIATE_WORK = "pxlnet-subscription-reminder-check"
    private const val CHANNEL_ID = "pxlnet_subscription"
    private const val NOTIFICATION_ID = 4610
    private const val PREFS = "pxlnet_subscription_reminders"
    private const val LAST_NOTIFICATION = "last_notification"

    fun schedule(context: Context) {
        if (!Settings.pxlnetSubscriptionReminders) return
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ReminderWorker>(12, TimeUnit.HOURS).build(),
        )
        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>().build(),
        )
    }

    fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelUniqueWork(PERIODIC_WORK)
        workManager.cancelUniqueWork(IMMEDIATE_WORK)
    }

    class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            if (!Settings.pxlnetSubscriptionReminders) return Result.success()
            val expiry = SubscriptionInfoStore.effectiveExpiry(applicationContext, Settings.selectedProfile)
            if (expiry <= 0) return Result.success()
            val remainingSeconds = expiry - System.currentTimeMillis() / 1000
            val days = ceil(remainingSeconds / 86_400.0).toInt()
            val threshold = when {
                days <= 0 -> 0
                days <= 1 -> 1
                days <= 3 -> 3
                days <= 7 -> 7
                else -> return Result.success()
            }
            val notificationKey = "$expiry:$threshold"
            val preferences = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (preferences.getString(LAST_NOTIFICATION, null) == notificationKey) return Result.success()

            createChannel(applicationContext)
            val renewIntent = PendingIntent.getActivity(
                applicationContext,
                4611,
                Intent(Intent.ACTION_VIEW, Uri.parse(PxlLinks.TELEGRAM_BOT)),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val accountIntent = PendingIntent.getActivity(
                applicationContext,
                4612,
                Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val text = if (threshold == 0) {
                applicationContext.getString(R.string.pxlnet_subscription_expired_notification)
            } else {
                applicationContext.resources.getQuantityString(
                    R.plurals.pxlnet_subscription_days_notification,
                    days.coerceAtLeast(1),
                    days.coerceAtLeast(1),
                )
            }
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_qs_pxlnet)
                .setContentTitle(applicationContext.getString(R.string.pxlnet_subscription_reminder_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(accountIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .addAction(0, applicationContext.getString(R.string.pxlnet_renew), renewIntent)
                .addAction(0, applicationContext.getString(R.string.pxlnet_account_title), accountIntent)
                .build()
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
            preferences.edit().putString(LAST_NOTIFICATION, notificationKey).apply()
            return Result.success()
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.pxlnet_subscription_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.pxlnet_subscription_channel_description)
            },
        )
    }
}
