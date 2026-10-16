package io.nekohasekai.sfa.utils

import io.nekohasekai.sfa.xray.PxlXraySession
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Activates an optional XHTTP companion only after the ordinary sing-box VPN is running.
 * The caller owns serialization with service stop/reload and must stop the VPN on [Result.Fatal].
 */
internal class PxlXhttpActivation(
    private val session: PxlXraySession,
    private val reloadSingbox: suspend (String) -> Unit,
    private val choosePort: () -> Int = ::availableLoopbackPort,
    private val listenerReady: suspend (Int) -> Boolean = ::waitForLoopbackListener,
    private val canContinue: () -> Boolean = { true },
) {
    enum class Result { Unavailable, Activated, BaseOnly, Fatal }

    suspend fun activate(configFile: File, attempts: Int = 3): Result {
        require(attempts in 1..10)
        var prepared: PxlXhttpRuntimePreparer.Prepared? = null
        repeat(attempts) {
            if (!canContinue()) return Result.BaseOnly
            val port = runCatching(choosePort).getOrNull() ?: return Result.BaseOnly
            val candidate = PxlXhttpRuntimePreparer.prepare(configFile, port)
                ?: return if (prepared == null) Result.Unavailable else Result.BaseOnly
            prepared = candidate
            val started = runCatching { session.start(candidate.xrayJson) }.getOrDefault(false)
            val ready = started && session.isRunning() &&
                runCatching { listenerReady(port) }.getOrDefault(false) && session.isRunning()
            if (ready && canContinue()) {
                return publish(candidate)
            }
            session.invalidate()
            runCatching { session.stop() }
        }
        return Result.BaseOnly
    }

    private suspend fun publish(prepared: PxlXhttpRuntimePreparer.Prepared): Result {
        return try {
            if (!canContinue()) {
                session.invalidate()
                session.stop()
                return Result.BaseOnly
            }
            reloadSingbox(prepared.augmentedSingboxJson)
            // An external stop may have arrived while the native reload was running.
            // The owner must close sing-box before disposing of this listener.
            if (!canContinue()) return Result.Fatal
            if (session.isRunning()) Result.Activated else restoreBase(prepared.baseSingboxJson)
        } catch (_: Exception) {
            restoreBase(prepared.baseSingboxJson)
        }
    }

    private suspend fun restoreBase(base: String): Result {
        session.invalidate()
        // Sing-box closes its old instance during reload. Keep the local listener
        // alive until that reload has finished; invalidation already blocks new dials.
        val restored = try {
            reloadSingbox(base)
            true
        } catch (_: Exception) {
            false
        }
        if (restored) runCatching { session.stop() }
        return if (restored) Result.BaseOnly else Result.Fatal
    }

    companion object {
        /** The port is only a candidate; Xray bind can still race, so [activate] retries. */
        private fun availableLoopbackPort(): Int =
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }

        /** A running native core is not proof that its local SOCKS listener is ready. */
        private suspend fun waitForLoopbackListener(port: Int): Boolean = withContext(Dispatchers.IO) {
            repeat(5) { attempt ->
                val accepting = runCatching {
                    Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 200) }
                    true
                }.getOrDefault(false)
                if (accepting) return@withContext true
                if (attempt < 4) delay(100)
            }
            false
        }
    }
}
