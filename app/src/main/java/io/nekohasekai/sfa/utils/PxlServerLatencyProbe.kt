package io.nekohasekai.sfa.utils

import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.database.ProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress

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
            val config = resolveServerForPreconnect(file.readText(), outboundTag) { server ->
                val addresses = InetAddress.getAllByName(server)
                (addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull())?.hostAddress
            }
            Libbox.probeServerBeforeStart(config, outboundTag).takeIf { it > 0 }
        } catch (error: Exception) {
            PxlProbeDiagnostics.record(Application.application, "preconnect", error.message.orEmpty())
            null
        }
    }

    /** Resolve only the temporary probe config through Android's DNS, not sing-box's /etc/resolv.conf. */
    internal fun resolveServerForPreconnect(
        config: String,
        outboundTag: String,
        resolve: (String) -> String?,
    ): String {
        val root = Json.parseToJsonElement(config).jsonObject
        val outbounds = root["outbounds"]?.jsonArray ?: return config
        var changed = false
        val resolvedOutbounds = outbounds.map { element ->
            val outbound = element.jsonObject
            if (outbound["tag"]?.jsonPrimitive?.contentOrNull != outboundTag ||
                outbound["type"]?.jsonPrimitive?.contentOrNull !in setOf("vless", "hysteria2")) {
                return@map element
            }
            val server = outbound["server"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (server.isBlank()) return@map element
            val address = resolve(server)?.takeIf { it.isNotBlank() } ?: return@map element
            if (address == server) return@map element
            val updated = outbound.toMutableMap()
            updated["server"] = JsonPrimitive(address)
            // Replacing the dial address must not change certificate verification or SNI.
            outbound["tls"]?.jsonObject?.let { tls ->
                if (tls["enabled"]?.jsonPrimitive?.contentOrNull == "true" &&
                    tls["server_name"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) {
                    updated["tls"] = JsonObject(tls + ("server_name" to JsonPrimitive(server)))
                }
            }
            changed = true
            JsonObject(updated)
        }
        return if (changed) JsonObject(root + ("outbounds" to kotlinx.serialization.json.JsonArray(resolvedOutbounds))).toString()
        else config
    }

    fun median(samples: List<Int>): Int? {
        if (samples.size != SAMPLE_COUNT || samples.any { it <= 0 }) return null
        return samples.sorted()[1]
    }
}
