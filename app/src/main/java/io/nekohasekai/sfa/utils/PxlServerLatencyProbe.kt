package io.nekohasekai.sfa.utils

import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.database.ProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class PxlLatencySource(val persistedValue: String) {
    VPN_SERVER("vpn_server"),
    WEBSITE_THROUGH_VPN("website_through_vpn"),
    ;

    companion object {
        fun fromPersisted(value: String?): PxlLatencySource =
            entries.firstOrNull { it.persistedValue == value } ?: WEBSITE_THROUGH_VPN
    }
}

/** One sample opens a fresh native connection to the selected outbound, not to a website. */
object PxlServerLatencyProbe {
    const val SAMPLE_COUNT = 3

    suspend fun measure(outboundTag: String): Int? = withContext(Dispatchers.IO) {
        if (outboundTag.isBlank() || CommandTarget.isRemote) return@withContext null
        val samples = ArrayList<Int>(SAMPLE_COUNT)
        repeat(SAMPLE_COUNT) {
            val sample = runCatching {
                Libbox.newStandaloneCommandClient().probeServer(outboundTag)
            }.getOrNull()?.takeIf { it > 0 } ?: return@withContext null
            samples += sample
        }
        median(samples)
    }

    /** Uses an outbound-only temporary core; it never starts the VPN tunnel. */
    suspend fun measureBeforeStart(profileId: Long, outboundTag: String): Int? = withContext(Dispatchers.IO) {
        if (profileId <= 0 || outboundTag.isBlank() || CommandTarget.isRemote) {
            PxlProbeDiagnostics.record(Application.application, "preconnect", "unsupported state")
            return@withContext null
        }
        val profile = ProfileManager.get(profileId) ?: run {
            PxlProbeDiagnostics.record(Application.application, "preconnect", "outbound not found")
            return@withContext null
        }
        val file = File(profile.typed.path)
        if (!file.isFile) {
            PxlProbeDiagnostics.record(Application.application, "preconnect", "decode config")
            return@withContext null
        }
        try {
            Libbox.probeServerBeforeStart(file.readText(), outboundTag).takeIf { it > 0 }
        } catch (error: Exception) {
            PxlProbeDiagnostics.record(Application.application, "preconnect", error.message.orEmpty())
            null
        }
    }

    fun median(samples: List<Int>): Int? {
        if (samples.size != SAMPLE_COUNT || samples.any { it <= 0 }) return null
        return samples.sorted()[1]
    }
}
