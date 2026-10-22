package io.nekohasekai.sfa.bg

/** Prevents a dead XHTTP companion or stale start from publishing VPN Started. */
internal class PxlXrayStartGate {
    private enum class Stage { Closed, Starting, Started, Failed }

    private val lock = Any()
    private var generation = 0L
    private var stage = Stage.Closed

    fun begin(): Long = synchronized(lock) {
        generation++
        stage = Stage.Starting
        generation
    }

    fun isStarting(startGeneration: Long): Boolean = synchronized(lock) {
        startGeneration == generation && stage == Stage.Starting
    }

    /** Returns true only when an already-published VPN needs to be stopped. */
    fun reportDeath(): Boolean = synchronized(lock) {
        val wasStarted = stage == Stage.Started
        if (stage == Stage.Starting || wasStarted) stage = Stage.Failed
        wasStarted
    }

    /** The publication itself is inside the same lock as [reportDeath]. */
    fun publish(startGeneration: Long, action: () -> Unit): Boolean = synchronized(lock) {
        if (startGeneration != generation || stage != Stage.Starting) return false
        stage = Stage.Started
        try {
            action()
        } catch (error: Throwable) {
            if (startGeneration == generation && stage == Stage.Started) stage = Stage.Failed
            throw error
        }
        startGeneration == generation && stage == Stage.Started
    }

    fun close() = synchronized(lock) {
        generation++
        stage = Stage.Closed
    }
}
