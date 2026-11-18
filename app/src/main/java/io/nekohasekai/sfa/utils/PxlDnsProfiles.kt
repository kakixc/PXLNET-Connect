package io.nekohasekai.sfa.utils

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.Inet6Address
import java.net.InetAddress
import java.util.UUID

/** Local DNS choices are independent of account, VPN subscription and profile storage. */
@Serializable
data class PxlDnsProfile(
    val id: String,
    val name: String,
    val address: String,
    val iconId: String = "public",
    val appPackages: List<String> = emptyList(),
    val domains: List<String> = emptyList(),
)

enum class PxlDnsTileMode { DNS_ONLY, DNS_AND_VPN }

enum class PxlRestartMode { VPN, DNS_ONLY, DNS_AND_VPN }

data class PxlDnsEndpoint(val type: String, val host: String, val port: Int, val path: String = "") {
    companion object {
        fun parse(raw: String): PxlDnsEndpoint {
            val value = raw.trim()
            require(value.isNotEmpty()) { "DNS server is empty" }
            val uri = URI(if ("://" in value) value else "udp://$value")
            require(uri.userInfo == null && uri.fragment == null && uri.query == null) { "DNS server URL contains unsupported fields" }
            val type = when (uri.scheme?.lowercase()) {
                "https" -> "https"
                "tls" -> "tls"
                "udp" -> "udp"
                else -> error("Use https://, tls:// or udp:// for DNS")
            }
            val host = uri.host?.trim()?.removePrefix("[")?.removeSuffix("]")?.lowercase().orEmpty()
            require(host.isNotBlank()) { "DNS server host is missing" }
            require(isValidHost(host)) { "Enter a full domain name or a valid IP address" }
            val port = if (uri.port >= 0) uri.port else when (type) {
                "https" -> 443
                "tls" -> 853
                else -> 53
            }
            require(port in 1..65535) { "Invalid DNS server port" }
            val path = if (type == "https") uri.rawPath?.takeIf { it.isNotBlank() } ?: "/dns-query" else ""
            require(type == "https" || uri.rawPath.isNullOrBlank()) { "Only HTTPS DNS accepts a path" }
            return PxlDnsEndpoint(type, host, port, path)
        }

        private fun isValidHost(host: String): Boolean {
            if (':' in host) {
                if (!host.matches(Regex("[0-9a-f:.]+"))) return false
                return runCatching { InetAddress.getByName(host) is Inet6Address }.getOrDefault(false)
            }
            if (host.matches(Regex("[0-9.]+"))) {
                val octets = host.split('.')
                return octets.size == 4 && octets.all {
                    it.isNotEmpty() && it.length <= 3 && (it.length == 1 || it[0] != '0') &&
                        it.all(Char::isDigit) && (it.toIntOrNull()?.let { value -> value in 0..255 } == true)
                }
            }
            if (host.length > 253 || '.' !in host) return false
            val label = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")
            return host.split('.').all { label.matches(it) }
        }
    }
}

object PxlDnsProfiles {
    private const val FILE_NAME = "pxlnet_expressive_dns"
    private const val PROFILES = "profiles"
    private const val SELECTED_ID = "selected_id"
    private const val TILE_MODE = "tile_mode"
    private const val START_WITH_VPN = "start_with_vpn"
    private const val ACTIVE_MODE = "active_mode"
    private const val FEATURE_ENABLED = "feature_enabled"
    private const val INTRO_SEEN = "intro_seen"
    private const val RESTART_MODE = "restart_mode"
    private const val RESTART_DNS_ID = "restart_dns_id"
    private const val ACTIVE_PROFILE_ID = "active_profile_id"
    private val json = Json { ignoreUnknownKeys = true }
    private var enabledSnapshot by mutableStateOf<Boolean?>(null)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean {
        if (enabledSnapshot == null) enabledSnapshot = prefs(context).getBoolean(FEATURE_ENABLED, true)
        return enabledSnapshot == true
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(FEATURE_ENABLED, enabled).apply()
        enabledSnapshot = enabled
    }

    fun introSeen(context: Context): Boolean = prefs(context).getBoolean(INTRO_SEEN, false)

    fun setIntroSeen(context: Context) {
        prefs(context).edit().putBoolean(INTRO_SEEN, true).apply()
    }

    fun all(context: Context): List<PxlDnsProfile> =
        runCatching { json.decodeFromString<List<PxlDnsProfile>>(prefs(context).getString(PROFILES, "[]") ?: "[]") }
            .getOrDefault(emptyList())

    fun selected(context: Context): PxlDnsProfile? =
        prefs(context).getString(SELECTED_ID, null)?.let { id -> all(context).firstOrNull { it.id == id } }

    fun save(context: Context, candidate: PxlDnsProfile): PxlDnsProfile {
        val name = candidate.name.trim()
        require(name.isNotEmpty() && name.length <= 60) { "DNS profile name must be 1–60 characters" }
        PxlDnsEndpoint.parse(candidate.address)
        require(candidate.appPackages.size <= 100 && candidate.domains.size <= 100) { "Too many DNS rules" }
        val profile = candidate.copy(
            id = candidate.id.ifBlank { UUID.randomUUID().toString() },
            name = name,
            address = candidate.address.trim(),
            appPackages = candidate.appPackages.distinct(),
            domains = candidate.domains.map { it.trim().trimStart('.') }.filter { it.isNotBlank() }.distinct(),
        )
        val current = all(context)
        val updated = if (current.any { it.id == profile.id }) current.map { if (it.id == profile.id) profile else it } else current + profile
        prefs(context).edit()
            .putString(PROFILES, json.encodeToString(updated))
            .also { if (selected(context) == null) it.putString(SELECTED_ID, profile.id) }
            .apply()
        return profile
    }

    fun select(context: Context, id: String) {
        require(all(context).any { it.id == id }) { "DNS profile not found" }
        prefs(context).edit().putString(SELECTED_ID, id).apply()
    }

    fun delete(context: Context, id: String) {
        val remaining = all(context).filterNot { it.id == id }
        prefs(context).edit()
            .putString(PROFILES, json.encodeToString(remaining))
            .also { if (prefs(context).getString(SELECTED_ID, null) == id) it.putString(SELECTED_ID, remaining.firstOrNull()?.id) }
            .apply()
    }

    fun tileMode(context: Context): PxlDnsTileMode =
        runCatching { PxlDnsTileMode.valueOf(prefs(context).getString(TILE_MODE, null) ?: "DNS_ONLY") }
            .getOrDefault(PxlDnsTileMode.DNS_ONLY)

    fun setTileMode(context: Context, mode: PxlDnsTileMode) {
        prefs(context).edit().putString(TILE_MODE, mode.name).apply()
    }

    fun startWithVpn(context: Context) = prefs(context).getBoolean(START_WITH_VPN, false)

    fun setStartWithVpn(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(START_WITH_VPN, enabled).apply()
    }

    /** Null means Expressive DNS is not currently running in this app's service. */
    fun activeMode(context: Context): PxlDnsTileMode? =
        prefs(context).getString(ACTIVE_MODE, null)?.let { runCatching { PxlDnsTileMode.valueOf(it) }.getOrNull() }

    fun setActiveMode(context: Context, mode: PxlDnsTileMode?) {
        prefs(context).edit().putString(ACTIVE_MODE, mode?.name)
            .also { if (mode == null) it.remove(ACTIVE_PROFILE_ID) }
            .apply()
    }

    fun activeProfileId(context: Context): String? = prefs(context).getString(ACTIVE_PROFILE_ID, null)

    fun setActiveProfileId(context: Context, id: String?) {
        prefs(context).edit().putString(ACTIVE_PROFILE_ID, id).apply()
    }

    fun restartMode(context: Context): PxlRestartMode? =
        prefs(context).getString(RESTART_MODE, null)?.let { runCatching { PxlRestartMode.valueOf(it) }.getOrNull() }

    fun restartDnsProfileId(context: Context): String? = prefs(context).getString(RESTART_DNS_ID, null)

    fun setRestartMode(context: Context, mode: PxlRestartMode?, dnsProfileId: String? = null) {
        prefs(context).edit()
            .putString(RESTART_MODE, mode?.name)
            .putString(RESTART_DNS_ID, if (mode == PxlRestartMode.DNS_ONLY) dnsProfileId else null)
            .apply()
    }
}
