package io.nekohasekai.sfa.vendor

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.MainActivity
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.update.UpdateInfo
import java.util.concurrent.TimeUnit

object UpdateNotification {
    private const val CHANNEL_ID = "pxlnet_updates"
    private const val NOTIFICATION_ID = 4609
    private const val TEST_NOTIFICATION_ID = 4610

    fun showIfDue(context: Context, update: UpdateInfo) {
        if (!Settings.updateNotificationEnabled || !notificationsAllowed(context)) return
        val now = System.currentTimeMillis()
        if (now - Settings.lastUpdateNotificationAt < TimeUnit.DAYS.toMillis(1)) return
        if (!runCatching { createChannel(context) }.isSuccess || !channelAllowed(context)) return
        val notification = buildNotification(
            context = context,
            title = context.getString(R.string.pxlnet_update_notification_title),
            text = context.getString(R.string.pxlnet_update_notification_text, update.versionName),
            requestCode = NOTIFICATION_ID,
            intentExtra = MainActivity.EXTRA_OPEN_UPDATE_DETAILS,
        )
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
            .onSuccess { Settings.lastUpdateNotificationAt = now }
    }

    /** Developer-only visual check; never changes real update state or the daily reminder timer. */
    fun showTest(context: Context): Boolean {
        if (!notificationsAllowed(context)) return false
        if (!runCatching { createChannel(context) }.isSuccess || !channelAllowed(context)) return false
        val notification = buildNotification(
            context = context,
            title = context.getString(R.string.pxlnet_update_test_notification_title),
            text = context.getString(R.string.pxlnet_update_test_notification_text),
            requestCode = TEST_NOTIFICATION_ID,
            intentExtra = MainActivity.EXTRA_OPEN_UPDATE_PREVIEW,
        )
        return runCatching {
            NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, notification)
        }.isSuccess
    }

    private fun notificationsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Effective OS permission, including an existing channel disabled in system settings. */
    fun canNotify(context: Context): Boolean = notificationsAllowed(context) && channelAllowed(context)

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.pxlnet_update_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
    }

    private fun channelAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE

    private fun buildNotification(
        context: Context,
        title: String,
        text: String,
        requestCode: Int,
        intentExtra: String,
    ) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_qs_pxlnet)
        .setContentTitle(title)
        .setContentText(text)
        .setContentIntent(
            PendingIntent.getActivity(
                context,
                requestCode,
                Intent(context, MainActivity::class.java)
                    .putExtra(intentExtra, true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .build()
}
