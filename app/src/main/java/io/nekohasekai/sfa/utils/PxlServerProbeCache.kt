package io.nekohasekai.sfa.utils

/** Short-lived UI cache: opening a sheet or switching tabs is not a new latency measurement. */
class PxlServerProbeCache(private val ttlMillis: Long = 120_000) {
    data class Key(
        val profileId: Long,
        val tag: String,
        val serviceStarted: Boolean,
        val networkId: String,
        val profileUpdatedAtMillis: Long = 0,
    )

    data class Entry(val milliseconds: Int?, val checkedAtMillis: Long)

    private val entries = mutableMapOf<Key, Entry>()

    fun get(key: Key, nowMillis: Long): Entry? = entries[key]
        ?.takeIf { nowMillis >= it.checkedAtMillis && nowMillis - it.checkedAtMillis < ttlMillis }

    fun record(key: Key, milliseconds: Int?, nowMillis: Long) {
        entries[key] = Entry(milliseconds, nowMillis)
    }

    fun clear() = entries.clear()
}
