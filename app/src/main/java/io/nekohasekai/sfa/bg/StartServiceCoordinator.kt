package io.nekohasekai.sfa.bg

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.compose.MainActivity
import io.nekohasekai.sfa.constant.ServiceMode
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keeps every entry point on the same safe path before it asks Android to start the VPN service.
 * A foreground service is not proof of a working tunnel: [Status.Started] is emitted only after
 * libbox has successfully started it.
 */
internal object StartServiceCoordinator {
    private const val TAG = "ServiceStart"
    const val EXTRA_START_AFTER_PREREQUISITES = "net.pxlnet.connect.extra.START_AFTER_PREREQUISITES"

    enum class Source { HOME, WIDGET, QUICK_SETTINGS, BOOT }

    enum class Decision {
        START,
        OPEN_APP_FOR_NOTIFICATION_PERMISSION,
        OPEN_APP_FOR_VPN_PERMISSION,
        ALREADY_IN_PROGRESS,
    }

    data class Prerequisites(
        val currentStatus: Status,
        val notificationPermissionGranted: Boolean,
        val vpnPermissionGranted: Boolean,
    )

    private data class PendingStart(
        val source: Source,
        val requestedAtElapsedMs: Long,
        var sawStarting: Boolean = false,
    )

    private val lock = Any()
    private var pendingStart: PendingStart? = null
    private var lastKnownStatus: Status? = null

    fun currentStatus(): Status? = synchronized(lock) { lastKnownStatus }

    /** Pure decision logic, kept separate so permission rules have unit tests. */
    fun decide(prerequisites: Prerequisites): Decision =
        when {
            prerequisites.currentStatus != Status.Stopped -> Decision.ALREADY_IN_PROGRESS
            !prerequisites.notificationPermissionGranted -> Decision.OPEN_APP_FOR_NOTIFICATION_PERMISSION
            !prerequisites.vpnPermissionGranted -> Decision.OPEN_APP_FOR_VPN_PERMISSION
            else -> Decision.START
        }

    /** Rebuilds the service type before checking VPN permission, like the Home path does. */
    suspend fun preflight(context: Context, currentStatus: Status): Decision {
        if (isStartPending()) return Decision.ALREADY_IN_PROGRESS
        withContext(Dispatchers.IO) { Settings.rebuildServiceMode() }
        val vpnPermissionGranted =
            if (Settings.serviceMode != ServiceMode.VPN) {
                true
            } else {
                withContext(Dispatchers.Main) { VpnService.prepare(context) == null }
            }
        return decide(
            Prerequisites(
                currentStatus = currentStatus,
                notificationPermissionGranted = ServiceNotification.checkPermission(),
                vpnPermissionGranted = vpnPermissionGranted,
            ),
        )
    }

    /** Starts only after [preflight] returned [Decision.START]. */
    fun start(source: Source): Boolean {
        val request = PendingStart(source, SystemClock.elapsedRealtime())
        if (!reserveStart(request)) {
            Log.d(TAG, "duplicate request ignored: source=$source status=${currentStatus()}")
            return false
        }
        return try {
            ContextCompat.startForegroundService(
                Application.application,
                Intent(Application.application, Settings.serviceClass()),
            )
            Log.i(TAG, "request source=$source at=${request.requestedAtElapsedMs}ms")
            true
        } catch (error: Exception) {
            finishPending("start request failed: ${error.javaClass.simpleName}: ${error.message}")
            false
        }
    }

    fun onServiceStatusChanged(status: Status) {
        val pending = synchronized(lock) {
            lastKnownStatus = status
            pendingStart
        } ?: return
        when (status) {
            Status.Starting -> {
                synchronized(lock) { pending.sawStarting = true }
                Log.i(TAG, "starting source=${pending.source} elapsed=${elapsedSince(pending)}ms")
            }

            Status.Started -> finishPending("started source=${pending.source} elapsed=${elapsedSince(pending)}ms")
            // A newly created BoxService publishes its initial Stopped value before onStartCommand.
            Status.Stopped -> if (pending.sawStarting) {
                finishPending("stopped-before-start source=${pending.source} elapsed=${elapsedSince(pending)}ms")
            }

            Status.Stopping -> Unit
        }
    }

    fun onStartFailed(reason: String) {
        val pending = synchronized(lock) { pendingStart } ?: return
        finishPending("failed source=${pending.source} elapsed=${elapsedSince(pending)}ms reason=$reason")
    }

    fun isStartPending(): Boolean = synchronized(lock) { pendingStart != null }

    private fun reserveStart(request: PendingStart): Boolean = synchronized(lock) {
        if (pendingStart != null || (lastKnownStatus != null && lastKnownStatus != Status.Stopped)) {
            false
        } else {
            pendingStart = request
            true
        }
    }

    /** Testable reservation without starting an Android foreground service. */
    internal fun reserveForTest(source: Source): Boolean = reserveStart(PendingStart(source, 0L))
    internal fun clearForTest() = synchronized(lock) { pendingStart = null }

    fun permissionIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_START_AFTER_PREREQUISITES, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    private fun elapsedSince(pending: PendingStart): Long = SystemClock.elapsedRealtime() - pending.requestedAtElapsedMs

    private fun finishPending(message: String) {
        synchronized(lock) { pendingStart = null }
        Log.i(TAG, message)
    }
}
