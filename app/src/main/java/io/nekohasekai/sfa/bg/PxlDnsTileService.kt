package io.nekohasekai.sfa.bg

import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.MainActivity
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.constant.ServiceMode
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.utils.PxlDnsProfiles
import io.nekohasekai.sfa.utils.PxlDnsTileMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@RequiresApi(24)
class PxlDnsTileService : TileService(), ServiceConnection.Callback {
    companion object {
        const val EXTRA_OPEN_DNS_SETTINGS = "net.pxlnet.connect.extra.OPEN_DNS_SETTINGS"
        const val EXTRA_CONFIRM_DNS_ONLY = "net.pxlnet.connect.extra.CONFIRM_DNS_ONLY"
        const val EXTRA_START_DNS_MODE = "net.pxlnet.connect.extra.START_DNS_MODE"
    }

    private val connection = ServiceConnection(this, this)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        connection.connect()
        onServiceStatusChanged(StartServiceCoordinator.currentStatus() ?: connection.status)
    }

    override fun onStopListening() {
        connection.disconnect()
        super.onStopListening()
    }

    override fun onServiceStatusChanged(status: Status) {
        val enabled = PxlDnsProfiles.enabled(this)
        val active = PxlDnsProfiles.activeMode(this)
        qsTile?.apply {
            label = getString(R.string.pxlnet_dns_tile_label)
            state = if (!enabled) Tile.STATE_UNAVAILABLE else if (status == Status.Started && active != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = when {
                    status == Status.Started && active == PxlDnsTileMode.DNS_ONLY -> getString(R.string.pxlnet_dns_only)
                    status == Status.Started && active == PxlDnsTileMode.DNS_AND_VPN -> getString(R.string.pxlnet_dns_with_vpn)
                    else -> getString(if (enabled) R.string.pxlnet_dns_off else R.string.pxlnet_dns_disabled)
                }
            }
            updateTile()
        }
    }

    override fun onClick() {
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguard.isKeyguardLocked) unlockAndRun { toggle() } else toggle()
    }

    private fun toggle() {
        if (!PxlDnsProfiles.enabled(this)) {
            onServiceStatusChanged(StartServiceCoordinator.currentStatus() ?: connection.status)
            return
        }
        val profile = PxlDnsProfiles.selected(this) ?: run { openDnsSettings(); return }
        val mode = if (PxlDnsProfiles.startWithVpn(this)) PxlDnsTileMode.DNS_AND_VPN else PxlDnsProfiles.tileMode(this)
        val status = StartServiceCoordinator.currentStatus() ?: connection.status
        val active = PxlDnsProfiles.activeMode(this)
        if (status == Status.Started && active == mode) {
            BoxService.stop()
            return
        }
        if (status !in setOf(Status.Started, Status.Stopped)) return
        if (mode == PxlDnsTileMode.DNS_ONLY && status == Status.Started && Settings.serviceMode == ServiceMode.VPN) {
            BoxService.setIntegratedDnsEnabled(active != PxlDnsTileMode.DNS_AND_VPN)
            return
        }
        if (mode == PxlDnsTileMode.DNS_AND_VPN && status == Status.Started) {
            if (active == PxlDnsTileMode.DNS_ONLY || Settings.serviceMode != ServiceMode.VPN) openDnsSettings(startMode = mode)
            else BoxService.setIntegratedDnsEnabled(true)
            return
        }
        if (mode == PxlDnsTileMode.DNS_ONLY && (status == Status.Started || otherVpnActive())) {
            openDnsSettings(confirm = true)
            return
        }
        scope.launch {
            if (mode == PxlDnsTileMode.DNS_ONLY) {
                when (StartServiceCoordinator.preflightDnsOnly(this@PxlDnsTileService)) {
                    StartServiceCoordinator.DnsDecision.START -> {
                        if (StartServiceCoordinator.startDnsOnly(StartServiceCoordinator.Source.DNS_TILE)) {
                            // The tile was bound to the normal service before DNS-only became active.
                            connection.reconnect()
                        }
                    }
                    else -> openDnsSettings(startMode = mode)
                }
            } else {
                when (StartServiceCoordinator.preflight(this@PxlDnsTileService, status)) {
                    StartServiceCoordinator.Decision.START -> {
                        if (Settings.serviceMode == ServiceMode.VPN) StartServiceCoordinator.start(StartServiceCoordinator.Source.DNS_TILE, mode)
                        else openDnsSettings()
                    }
                    else -> openDnsSettings(startMode = mode)
                }
            }
        }
    }

    private fun otherVpnActive(): Boolean {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return false
        return manager.getNetworkCapabilities(manager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }

    private fun openDnsSettings(confirm: Boolean = false, startMode: PxlDnsTileMode? = null) {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN_DNS_SETTINGS, true)
            .putExtra(EXTRA_CONFIRM_DNS_ONLY, confirm)
            .putExtra(EXTRA_START_DNS_MODE, startMode?.name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
