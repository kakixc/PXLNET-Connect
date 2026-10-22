package io.nekohasekai.sfa.xray

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.VpnService
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import io.nekohasekai.sfa.bg.VPNService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Main-process owner for the private Xray sidecar on Android 7+.
 *
 * No TUN is created here. Xray obtains network access only by asking this
 * callback to protect each socket through the active [VPNService]. A stale
 * callback, revoked VPN permission, Binder death, and every timeout all fail
 * closed. The sidecar service is bound-only and this controller never starts
 * it as a standalone service.
 */
class PxlXrayController(
    private val owner: VPNService,
    private val onUnexpectedDeath: () -> Unit,
    private val bindTimeoutMs: Long = 5_000,
    private val transactionTimeoutMs: Long = 10_000,
) : PxlXraySession {
    private val operationMutex = Mutex()
    private val stateLock = Any()

    private var activeSession: Session? = null
    private var runningSession: Session? = null
    private var activeConnection: XrayConnection? = null

    init {
        require(bindTimeoutMs > 0)
        require(transactionTimeoutMs > 0)
    }

    override suspend fun start(configJson: String): Boolean {
        // Disable an old callback before waiting for any Binder work.
        invalidate()
        operationMutex.lock()
        return try {
            startLocked(configJson)
        } finally {
            operationMutex.unlock()
        }
    }

    private suspend fun startLocked(configJson: String): Boolean {
        if (configJson.isBlank()) return false
        stopLocked()

        val session = Session()
        synchronized(stateLock) {
            activeSession = session
        }

        val connection = XrayConnection(session)
        synchronized(stateLock) {
            activeConnection = connection
        }

        if (!connection.bind()) {
            invalidateSession(session)
            detach(connection)
            return false
        }

        val remote = connection.awaitRemote(bindTimeoutMs)
        if (remote == null || !session.isUsable()) {
            invalidateSession(session)
            detach(connection)
            return false
        }

        // A timeout does not guarantee that the remote Binder transaction stopped.
        // The token check after it returns prevents any late result from becoming live.
        val started = invokeStart(remote, configJson, session.protector)
        if (started == true && session.isUsable()) {
            synchronized(stateLock) {
                if (activeSession === session && session.active.get()) {
                    runningSession = session
                    return true
                }
            }
        }

        invalidateSession(session)
        detach(connection)
        return false
    }

    override fun invalidate() {
        synchronized(stateLock) {
            activeSession?.active?.set(false)
            activeSession = null
            runningSession = null
        }
    }

    override suspend fun stop() {
        // This is intentionally before the first suspend point: onRevoke can fail
        // closed immediately, then let remote cleanup happen afterwards.
        invalidate()
        operationMutex.lock()
        try {
            stopLocked()
        } finally {
            operationMutex.unlock()
        }
    }

    override fun isRunning(): Boolean = synchronized(stateLock) {
        val session = runningSession
        session != null && session === activeSession && session.active.get()
    }

    override fun close() {
        invalidate()
        val connection = synchronized(stateLock) {
            activeConnection.also { activeConnection = null }
        }
        connection?.unbind()
    }

    private suspend fun stopLocked() {
        val connection = synchronized(stateLock) {
            activeConnection.also { activeConnection = null }
        }
        val remote = connection?.remote()
        if (remote != null) invokeStop(remote)
        connection?.unbind()
    }

    private suspend fun invokeStart(
        remote: IXrayCore,
        configJson: String,
        protector: IProtectSocket,
    ): Boolean? = invokeBound {
        try {
            remote.startCore(configJson, protector)
        } catch (_: RemoteException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    private suspend fun invokeStop(remote: IXrayCore) {
        invokeBound<Unit> {
            try {
                remote.stopCore()
            } catch (_: RemoteException) {
                // The process is already gone, which is equivalent to stopped.
            } catch (_: RuntimeException) {
                // Best effort only; local invalidation remains fail closed.
            }
        }
    }

    /**
     * A Binder transaction can ignore coroutine cancellation.  A dedicated
     * daemon worker plus Future.get(timeout) bounds how long lifecycle code
     * waits. Cancelling the Future is only a best-effort interrupt; callers
     * still invalidate and unbind after a timeout instead of assuming it won.
     */
    private suspend fun <T> invokeBound(block: () -> T): T? = withContext(Dispatchers.IO) {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "pxl-xray-binder").apply { isDaemon = true }
        }
        val transaction = executor.submit(Callable { block() })
        try {
            transaction.get(transactionTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            transaction.cancel(true)
            null
        } catch (_: ExecutionException) {
            null
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        } finally {
            executor.shutdown()
        }
    }

    private fun invalidateSession(session: Session) {
        synchronized(stateLock) {
            session.active.set(false)
            if (activeSession === session) activeSession = null
            if (runningSession === session) runningSession = null
        }
    }

    private fun detach(connection: XrayConnection) {
        synchronized(stateLock) {
            if (activeConnection === connection) activeConnection = null
        }
        connection.unbind()
    }

    private fun handleConnectionLoss(connection: XrayConnection) {
        val session = connection.session
        val shouldReport = synchronized(stateLock) {
            // Binder may die while startCore() is returning, before runningSession
            // is assigned. The active generation is still fatal to its VPN owner.
            if (activeSession !== session || !session.active.get()) {
                false
            } else {
                session.active.set(false)
                if (runningSession === session) runningSession = null
                activeSession = null
                if (activeConnection === connection) activeConnection = null
                true
            }
        }
        connection.unbind()
        if (shouldReport) onUnexpectedDeath()
    }

    private inner class Session {
        val active = AtomicBoolean(true)
        val protector = object : IProtectSocket.Stub() {
            override fun isReady(): Boolean = isUsable()

            override fun protectSocket(socket: ParcelFileDescriptor?): Boolean {
                socket ?: return false
                return socket.use { descriptor ->
                    if (!isUsable()) return@use false
                    try {
                        owner.protect(descriptor.fd)
                    } catch (_: RuntimeException) {
                        false
                    }
                }
            }
        }

        fun isUsable(): Boolean {
            if (!active.get()) return false
            if (synchronized(stateLock) { activeSession !== this@Session }) return false
            return VpnService.prepare(owner) == null
        }
    }

    private inner class XrayConnection(val session: Session) : ServiceConnection {
        private val bound = AtomicBoolean(false)
        private val connected = CompletableDeferred<IXrayCore?>()

        @Volatile
        private var core: IXrayCore? = null

        fun bind(): Boolean {
            val intent = Intent(owner, PxlXrayService::class.java)
            return try {
                owner.bindService(intent, this, Context.BIND_AUTO_CREATE).also { bound.set(it) }
            } catch (_: RuntimeException) {
                false
            }
        }

        suspend fun awaitRemote(timeoutMs: Long): IXrayCore? {
            return withTimeoutOrNull(timeoutMs) { connected.await() }
        }

        fun remote(): IXrayCore? = core

        fun unbind() {
            if (bound.compareAndSet(true, false)) {
                try {
                    owner.unbindService(this)
                } catch (_: IllegalArgumentException) {
                    // Android already disconnected this exact connection.
                }
            }
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            core = IXrayCore.Stub.asInterface(service)
            connected.complete(core)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            core = null
            connected.complete(null)
            handleConnectionLoss(this)
        }

        override fun onBindingDied(name: ComponentName) {
            core = null
            connected.complete(null)
            handleConnectionLoss(this)
        }

        override fun onNullBinding(name: ComponentName) {
            core = null
            connected.complete(null)
        }
    }
}
