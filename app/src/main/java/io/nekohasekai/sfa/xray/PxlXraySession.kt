package io.nekohasekai.sfa.xray

/**
 * Lifecycle boundary for the optional XHTTP sidecar.
 *
 * The caller must call [invalidate] before its VPN owner can be revoked. This
 * is synchronous and immediately makes every future sidecar socket protection
 * fail closed; [stop] then performs the slower remote cleanup.
 */
interface PxlXraySession {
    suspend fun start(configJson: String): Boolean

    fun invalidate()

    suspend fun stop()

    fun isRunning(): Boolean

    fun close()
}
