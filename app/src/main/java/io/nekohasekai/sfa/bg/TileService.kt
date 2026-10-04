package io.nekohasekai.sfa.bg

import android.app.PendingIntent
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.utils.PxlLocalPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@RequiresApi(24)
class TileService :
    TileService(),
    ServiceConnection.Callback {
    private val connection = ServiceConnection(this, this)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onServiceStatusChanged(status: Status) {
        qsTile?.apply {
            label = getString(R.string.pxlnet_tile_label)
            state =
                when (status) {
                    Status.Started -> Tile.STATE_ACTIVE
                    Status.Stopped -> Tile.STATE_INACTIVE
                    else -> Tile.STATE_UNAVAILABLE
                }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = when (status) {
                    Status.Started -> getString(R.string.pxlnet_tile_on)
                    Status.Stopped -> getString(R.string.pxlnet_tile_off)
                    else -> getString(R.string.pxlnet_tile_switching)
                }
            }
            updateTile()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        runCatching { PxlLocalPreferences.setQuickTileAdded(this, true) }
        connection.connect()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        runCatching { PxlLocalPreferences.setQuickTileAdded(this, true) }
    }

    override fun onTileRemoved() {
        runCatching { PxlLocalPreferences.setQuickTileAdded(this, false) }
        super.onTileRemoved()
    }

    override fun onStopListening() {
        connection.disconnect()
        super.onStopListening()
    }

    override fun onClick() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguardManager.isKeyguardLocked) {
            unlockAndRun {
                toggleService()
            }
        } else {
            toggleService()
        }
    }

    private fun toggleService() {
        when (StartServiceCoordinator.currentStatus() ?: connection.status) {
            Status.Stopped -> startService()
            Status.Started -> BoxService.stop()
            else -> {}
        }
    }

    private fun startService() {
        scope.launch {
            when (StartServiceCoordinator.preflight(this@TileService, StartServiceCoordinator.currentStatus() ?: connection.status)) {
                StartServiceCoordinator.Decision.START -> {
                    BoxService.start(StartServiceCoordinator.Source.QUICK_SETTINGS)
                }

                StartServiceCoordinator.Decision.OPEN_APP_FOR_NOTIFICATION_PERMISSION,
                StartServiceCoordinator.Decision.OPEN_APP_FOR_VPN_PERMISSION ->
                    openPermissionActivity()

                StartServiceCoordinator.Decision.ALREADY_IN_PROGRESS -> Unit
            }
        }
    }

    private fun openPermissionActivity() {
        val intent = StartServiceCoordinator.permissionIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
