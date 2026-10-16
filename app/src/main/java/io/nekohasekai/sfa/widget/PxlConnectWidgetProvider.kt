package io.nekohasekai.sfa.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.MainActivity
import io.nekohasekai.sfa.bg.BoxService
import io.nekohasekai.sfa.bg.StartServiceCoordinator
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.utils.PxlLinks
import io.nekohasekai.sfa.utils.PxlUiPreferences
import io.nekohasekai.sfa.utils.PxlSubscriptionConverter
import io.nekohasekai.sfa.utils.SubscriptionInfoStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        if (StartServiceCoordinator.currentStatus() == Status.Started) {
            BoxService.stop()
            updateAll(context, Status.Stopping)
        } else {
            val pendingResult = goAsync()
            scope.launch {
                try {
                    when (StartServiceCoordinator.preflight(context.applicationContext, Status.Stopped)) {
                        StartServiceCoordinator.Decision.START -> {
                            if (BoxService.start(StartServiceCoordinator.Source.WIDGET)) {
                                updateAll(context, Status.Starting)
                            }
                        }

                        StartServiceCoordinator.Decision.BLOCKED_BY_INACTIVE_SUBSCRIPTION ->
                            updateAll(context, Status.Stopped)

                        StartServiceCoordinator.Decision.OPEN_APP_FOR_NOTIFICATION_PERMISSION,
                        StartServiceCoordinator.Decision.OPEN_APP_FOR_VPN_PERMISSION ->
                            withContext(Dispatchers.Main) {
                                context.startActivity(StartServiceCoordinator.permissionIntent(context))
                            }

                        StartServiceCoordinator.Decision.ALREADY_IN_PROGRESS -> updateAll(context, Status.Starting)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        private const val ACTION_TOGGLE = "net.pxlnet.connect.widget.TOGGLE"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun updateAll(context: Context, status: Status? = null) {
            val appContext = context.applicationContext
            scope.launch {
                updateAllNow(appContext, status)
            }
        }

        /** Configuration activities must draw the new widget before reporting success to the launcher. */
        suspend fun updateAllNow(context: Context, status: Status? = null, newWidgetId: Int? = null) {
            withContext(Dispatchers.IO) {
                val appContext = context.applicationContext
                val manager = AppWidgetManager.getInstance(appContext)
                val component = ComponentName(appContext, PxlConnectWidgetProvider::class.java)
                val ids = manager.getAppWidgetIds(component).toMutableSet()
                if (newWidgetId != null && newWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) ids.add(newWidgetId)
                if (ids.isEmpty()) return@withContext
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
            // Account-wide PXLNET expiry must not describe an independent imported VPN profile.
            val expiry = SubscriptionInfoStore.read(context, Settings.selectedProfile)?.expire ?: 0L
            val access = SubscriptionInfoStore.connectionAccess(context, Settings.selectedProfile)
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
                status = explicitStatus ?: StartServiceCoordinator.currentStatus() ?: Status.Stopped,
                profileName = profile?.name,
                serverName = selectedServer,
                daysLeft = daysLeft,
                access = access,
            )
        }

        private fun createRemoteViews(
            context: Context,
            options: Bundle,
            snapshot: WidgetSnapshot,
        ): RemoteViews {
            val inactiveSubscription = snapshot.access != SubscriptionInfoStore.ConnectionAccess.ALLOWED
            val active = !inactiveSubscription && snapshot.status == Status.Started
            val switching = !inactiveSubscription && (snapshot.status == Status.Starting || snapshot.status == Status.Stopping)
            val compact = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180) < 180
            val style = PxlUiPreferences.WidgetColorStyle.forAndroidVersion(
                PxlUiPreferences.readWidgetColorStyle(context), Build.VERSION.SDK_INT,
            )
            val monet = style != PxlUiPreferences.WidgetColorStyle.CLASSIC
            val soft = style == PxlUiPreferences.WidgetColorStyle.MONET_SOFT
            return RemoteViews(context.packageName, R.layout.pxlnet_widget).apply {
                setInt(
                    R.id.widget_root,
                    "setBackgroundResource",
                    when {
                        soft -> R.drawable.pxlnet_widget_soft_background
                        monet -> R.drawable.pxlnet_widget_monet_background
                        else -> R.drawable.pxlnet_widget_background
                    },
                )
                setImageViewResource(
                    R.id.widget_brand_icon,
                    when {
                        soft -> R.drawable.ic_qs_pxlnet_soft
                        monet -> R.drawable.ic_qs_pxlnet_monet
                        else -> R.drawable.ic_qs_pxlnet
                    },
                )
                setImageViewResource(
                    R.id.widget_power,
                    if (soft) R.drawable.ic_power_widget_soft else R.drawable.ic_power_widget,
                )
                val onSurface = ContextCompat.getColor(context, when {
                    soft -> R.color.pxlnet_widget_soft_on_surface
                    monet -> R.color.pxlnet_widget_monet_on_surface
                    else -> R.color.pxlnet_widget_on_surface
                })
                setTextColor(R.id.widget_title, onSurface)
                setTextColor(R.id.widget_server, onSurface)
                setTextColor(R.id.widget_status, onSurface)
                setTextColor(
                    R.id.widget_expiry,
                    ContextCompat.getColor(context,
                        when {
                            soft -> R.color.pxlnet_widget_soft_muted
                            monet -> R.color.pxlnet_widget_monet_muted
                            else -> R.color.pxlnet_widget_muted
                        },
                    ),
                )
                setTextViewText(
                    R.id.widget_status,
                    context.getString(
                        when {
                            inactiveSubscription -> R.string.pxlnet_widget_subscription_inactive
                            switching -> R.string.pxlnet_widget_switching
                            active -> R.string.pxlnet_widget_protected
                            else -> R.string.pxlnet_widget_off
                        },
                    ),
                )
                setTextViewText(
                    R.id.widget_server,
                    if (inactiveSubscription) context.getString(R.string.pxlnet_widget_subscription_inactive)
                    else snapshot.serverName?.takeUnless { it.equals("AUTO", true) }
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
                    when {
                        soft -> R.drawable.pxlnet_widget_soft_power
                        monet && active -> R.drawable.pxlnet_widget_monet_power_active
                        monet -> R.drawable.pxlnet_widget_monet_power_idle
                        active -> R.drawable.pxlnet_widget_power_active
                        else -> R.drawable.pxlnet_widget_power_idle
                    },
                )
                setInt(
                    R.id.widget_state_dot,
                    "setBackgroundResource",
                    when {
                        soft && (switching || active) -> R.drawable.pxlnet_widget_soft_dot_active
                        soft -> R.drawable.pxlnet_widget_soft_dot_idle
                        monet && (switching || active) -> R.drawable.pxlnet_widget_monet_dot_active
                        monet -> R.drawable.pxlnet_widget_monet_dot_idle
                        switching -> R.drawable.pxlnet_widget_dot_switching
                        active -> R.drawable.pxlnet_widget_dot_active
                        else -> R.drawable.pxlnet_widget_dot_idle
                    },
                )
                // An inactive selected profile cannot be started; open Home so the owner can
                // choose another available profile instead of leaving the tap with no response.
                setOnClickPendingIntent(
                    R.id.widget_power,
                    if (inactiveSubscription) openAppPendingIntent(context) else togglePendingIntent(context),
                )
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
    val access: SubscriptionInfoStore.ConnectionAccess,
)
