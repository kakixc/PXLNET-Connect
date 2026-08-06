package io.nekohasekai.sfa.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.BoxService
import io.nekohasekai.sfa.compose.MainActivity
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.constant.ServiceMode
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.utils.PxlLinks
import io.nekohasekai.sfa.utils.PxlSubscriptionConverter
import io.nekohasekai.sfa.utils.SubscriptionInfoStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

class PxlConnectWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAll(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TOGGLE) return
        if (Settings.startedByUser) {
            BoxService.stop()
            updateAll(context, Status.Stopping)
        } else {
            if (Settings.serviceMode == ServiceMode.VPN && VpnService.prepare(context) != null) {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
                return
            }
            BoxService.start()
            updateAll(context, Status.Starting)
        }
    }

    companion object {
        private const val ACTION_TOGGLE = "net.pxlnet.connect.widget.TOGGLE"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun updateAll(context: Context, status: Status? = null) {
            val appContext = context.applicationContext
            scope.launch {
                val manager = AppWidgetManager.getInstance(appContext)
                val component = ComponentName(appContext, PxlConnectWidgetProvider::class.java)
                val ids = manager.getAppWidgetIds(component)
                if (ids.isEmpty()) return@launch
                val snapshot = readSnapshot(appContext, status)
                ids.forEach { id ->
                    manager.updateAppWidget(
                        id,
                        createRemoteViews(appContext, manager.getAppWidgetOptions(id), snapshot),
                    )
                }
            }
        }

        private suspend fun readSnapshot(context: Context, explicitStatus: Status?): WidgetSnapshot {
            val profile = runCatching { ProfileManager.get(Settings.selectedProfile) }.getOrNull()
            val expiry = SubscriptionInfoStore.effectiveExpiry(context, Settings.selectedProfile)
            val selectedServer = profile?.let {
                runCatching {
                    val file = File(it.typed.path)
                    if (file.isFile) {
                        PxlSubscriptionConverter.serverSelection(file.readText()).selectedTag
                    } else {
                        null
                    }
                }.getOrNull()
            }
            val daysLeft = expiry
                .takeIf { it > 0 }
                ?.let { expiry ->
                    val secondsLeft = (expiry - System.currentTimeMillis() / 1000).coerceAtLeast(0)
                    (secondsLeft + TimeUnit.DAYS.toSeconds(1) - 1) / TimeUnit.DAYS.toSeconds(1)
                }
            return WidgetSnapshot(
                status = explicitStatus ?: if (Settings.startedByUser) Status.Started else Status.Stopped,
                profileName = profile?.name,
                serverName = selectedServer,
                daysLeft = daysLeft,
            )
        }

        private fun createRemoteViews(
            context: Context,
            options: Bundle,
            snapshot: WidgetSnapshot,
        ): RemoteViews {
            val active = snapshot.status == Status.Started
            val switching = snapshot.status == Status.Starting || snapshot.status == Status.Stopping
            val compact = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180) < 180
            return RemoteViews(context.packageName, R.layout.pxlnet_widget).apply {
                setTextViewText(
                    R.id.widget_status,
                    context.getString(
                        when {
                            switching -> R.string.pxlnet_widget_switching
                            active -> R.string.pxlnet_widget_protected
                            else -> R.string.pxlnet_widget_off
                        },
                    ),
                )
                setTextViewText(
                    R.id.widget_server,
                    snapshot.serverName?.takeUnless { it.equals("AUTO", true) }
                        ?: snapshot.profileName
                        ?: context.getString(R.string.pxlnet_widget_no_subscription),
                )
                setTextViewText(
                    R.id.widget_expiry,
                    snapshot.daysLeft?.let {
                        context.resources.getQuantityString(R.plurals.pxlnet_widget_days_left, it.toInt(), it)
                    } ?: context.getString(R.string.pxlnet_widget_open_account),
                )
                setViewVisibility(R.id.widget_expiry, if (compact) View.GONE else View.VISIBLE)
                setInt(
                    R.id.widget_power,
                    "setBackgroundResource",
                    if (active) R.drawable.pxlnet_widget_power_active else R.drawable.pxlnet_widget_power_idle,
                )
                setInt(
                    R.id.widget_state_dot,
                    "setBackgroundResource",
                    when {
                        switching -> R.drawable.pxlnet_widget_dot_switching
                        active -> R.drawable.pxlnet_widget_dot_active
                        else -> R.drawable.pxlnet_widget_dot_idle
                    },
                )
                setOnClickPendingIntent(R.id.widget_power, togglePendingIntent(context))
                setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))
                setOnClickPendingIntent(R.id.widget_expiry, renewPendingIntent(context))
            }
        }

        private fun togglePendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            701,
            Intent(context, PxlConnectWidgetProvider::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun openAppPendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            702,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun renewPendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            703,
            Intent(Intent.ACTION_VIEW, Uri.parse(PxlLinks.TELEGRAM_BOT)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

private data class WidgetSnapshot(
    val status: Status,
    val profileName: String?,
    val serverName: String?,
    val daysLeft: Long?,
)
