package io.nekohasekai.sfa.bg

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.MutableLiveData
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OutboundGroup
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.MainActivity
import io.nekohasekai.sfa.compose.screen.dashboard.ServerRegion
import io.nekohasekai.sfa.compose.screen.dashboard.cleanServerTag
import io.nekohasekai.sfa.compose.screen.dashboard.serverProtocol
import io.nekohasekai.sfa.compose.screen.dashboard.serverRegion
import io.nekohasekai.sfa.constant.Action
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.utils.CommandClient
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.withContext

class ServiceNotification(private val status: MutableLiveData<Status>, private val service: Service) :
    BroadcastReceiver(),
    CommandClient.Handler {
    companion object {
        private const val notificationId = 1
        private const val notificationChannel = "service"
        val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

        fun checkPermission(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                return true
            }
            return Application.notification.areNotificationsEnabled()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private val commandClient =
        CommandClient(
            GlobalScope,
            listOfNotNull(
                CommandClient.ConnectionType.Groups,
                CommandClient.ConnectionType.Status.takeIf { Settings.dynamicNotification },
            ),
            this,
            localOnly = true,
        )
    private var receiverRegistered = false
    private var isShowing = false
    private var profileName = ""
    private var statusTextId = R.string.status_starting
    private var selectedServerTag: String? = null
    private var trafficText: String? = null

    private val notificationBuilder by lazy {
        NotificationCompat.Builder(service, notificationChannel).setShowWhen(false).setOngoing(true)
            .setContentTitle(service.getString(R.string.app_name)).setOnlyAlertOnce(true)
            .setSmallIcon(R.drawable.ic_qs_pxlnet)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(
                PendingIntent.getActivity(
                    service,
                    0,
                    Intent(
                        service,
                        MainActivity::class.java,
                    ).setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                    flags,
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_LOW).apply {
                addAction(
                    NotificationCompat.Action.Builder(
                        0,
                        service.getText(R.string.stop),
                        PendingIntent.getBroadcast(
                            service,
                            0,
                            Intent(Action.SERVICE_CLOSE).setPackage(service.packageName),
                            flags,
                        ),
                    ).build(),
                )
            }
    }

    @Synchronized
    fun show(lastProfileName: String, @StringRes contentTextId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Application.notification.createNotificationChannel(
                NotificationChannel(
                    notificationChannel,
                    service.getString(R.string.pxlnet_vpn_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        profileName = lastProfileName
        statusTextId = contentTextId
        if (contentTextId == R.string.status_starting) {
            selectedServerTag = null
            trafficText = null
        }
        isShowing = true
        service.startForeground(
            notificationId,
            notificationBuilder
                .setContentTitle(lastProfileName.takeIf { it.isNotBlank() } ?: service.getString(R.string.app_name))
                .setContentText(contentText())
                .setSubText(trafficText)
                .build(),
        )
    }

    suspend fun start() {
        if (checkPermission()) {
            commandClient.connect()
            withContext(Dispatchers.Main) {
                registerReceiver()
            }
        }
    }

    private fun registerReceiver() {
        service.registerReceiver(
            this,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
        )
        receiverRegistered = true
    }

    @Synchronized
    override fun updateStatus(status: StatusMessage) {
        trafficText =
            Libbox.formatBytes(status.uplink) + "/s ↑\t" + Libbox.formatBytes(status.downlink) + "/s ↓"
        publish()
    }

    @Synchronized
    override fun updateGroups(newGroups: MutableList<OutboundGroup>) {
        selectedServerTag = newGroups.firstOrNull { it.tag == "PXLNET" }?.selected?.takeIf(String::isNotBlank)
        publish()
    }

    private fun contentText(): String {
        val statusText = service.getString(statusTextId)
        val tag = selectedServerTag ?: return statusText
        if (statusTextId != R.string.status_started) return statusText
        val serverName = when (serverRegion(tag)) {
            ServerRegion.AUTO -> service.getString(R.string.pxlnet_server_auto)
            ServerRegion.GERMANY -> service.getString(R.string.pxlnet_country_germany)
            ServerRegion.FINLAND -> service.getString(R.string.pxlnet_country_finland)
            ServerRegion.OTHER -> cleanServerTag(tag).take(48).ifBlank { profileName }
        }
        val protocol = serverProtocol(tag)
        val label = if (protocol.isBlank() || protocol in serverName) serverName else "$serverName · $protocol"
        return service.getString(R.string.pxlnet_notification_status_server, statusText, label)
    }

    private fun publish() {
        if (!isShowing) return
        Application.notificationManager.notify(
            notificationId,
            notificationBuilder
                .setContentText(contentText())
                .setSubText(trafficText)
                .build(),
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                commandClient.connect()
            }

            Intent.ACTION_SCREEN_OFF -> {
                commandClient.disconnect()
            }
        }
    }

    @Synchronized
    fun close() {
        isShowing = false
        commandClient.disconnect()
        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (receiverRegistered) {
            service.unregisterReceiver(this)
            receiverRegistered = false
        }
    }
}
