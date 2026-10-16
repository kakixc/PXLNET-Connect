package io.nekohasekai.sfa.xray

import io.nekohasekai.sfa.bg.VPNService

/** Android 5 compatibility artifact: the Xray sidecar is intentionally unavailable. */
class PxlXrayController(
    @Suppress("UNUSED_PARAMETER") owner: VPNService,
    @Suppress("UNUSED_PARAMETER") onUnexpectedDeath: () -> Unit,
    @Suppress("UNUSED_PARAMETER") bindTimeoutMs: Long = 5_000,
    @Suppress("UNUSED_PARAMETER") transactionTimeoutMs: Long = 10_000,
) : PxlXraySession {
    override suspend fun start(configJson: String): Boolean = false

    override fun invalidate() = Unit

    override suspend fun stop() = Unit

    override fun isRunning(): Boolean = false

    override fun close() = Unit
}
