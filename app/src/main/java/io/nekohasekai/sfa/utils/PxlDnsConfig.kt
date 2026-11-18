package io.nekohasekai.sfa.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Creates sing-box DNS config without modifying the saved VPN subscription JSON. */
object PxlDnsConfig {
    private const val BOOTSTRAP = "pxl-dns-bootstrap"
    private const val EXPRESSIVE = "pxl-dns-expressive"
    private const val EXPRESSIVE_DIRECT = "pxl-dns-expressive-direct"
    private const val APP_DIRECT = "pxl-dns-app-direct"

    fun withVpn(content: String, profile: PxlDnsProfile, bootstrapIp: String? = null): String {
        val root = Json.parseToJsonElement(content).jsonObject
        val existingDns = root["dns"]?.jsonObject ?: JsonObject(emptyMap())
        val existingServers = existingDns["servers"]?.jsonArray ?: JsonArray(emptyList())
        require(existingServers.none { it.jsonObject["tag"] in setOf(JsonPrimitive(EXPRESSIVE), JsonPrimitive(EXPRESSIVE_DIRECT), JsonPrimitive(BOOTSTRAP)) }) {
            "DNS tag is already used by the VPN profile"
        }
        val existingOutbounds = root["outbounds"]?.jsonArray ?: JsonArray(emptyList())
        require(existingOutbounds.none { it.jsonObject["tag"] == JsonPrimitive(APP_DIRECT) }) {
            "Direct app routing tag is already used by the VPN profile"
        }
        val bootstrapTag = BOOTSTRAP
        val servers = buildJsonArray {
            existingServers.forEach(::add)
            add(bootstrapServer(bootstrapIp))
            add(remoteServer(profile, bootstrapTag, detour = if (existingOutbounds.any {
                it.jsonObject["tag"] == JsonPrimitive("PXLNET")
            } == true) "PXLNET" else null))
            if (profile.appPackages.isNotEmpty()) add(remoteServer(profile, bootstrapTag, tag = EXPRESSIVE_DIRECT, detour = APP_DIRECT))
        }
        val oldRules = existingDns["rules"]?.jsonArray ?: JsonArray(emptyList())
        val rules = buildJsonArray {
            if (profile.appPackages.isNotEmpty()) {
                add(buildJsonObject {
                    put("package_name", strings(profile.appPackages))
                    put("action", "route")
                    put("server", EXPRESSIVE_DIRECT)
                })
            }
            if (profile.domains.isNotEmpty()) add(buildJsonObject {
                put("domain_suffix", strings(profile.domains))
                put("action", "route")
                put("server", EXPRESSIVE)
            })
            oldRules.forEach(::add)
        }
        val dns = JsonObject(existingDns + mapOf(
            "servers" to servers,
            "rules" to rules,
            "final" to JsonPrimitive(if (profile.appPackages.isEmpty() && profile.domains.isEmpty()) EXPRESSIVE else existingDns["final"]?.toString()?.trim('"') ?: bootstrapTag),
        ))
        val outbounds = existingOutbounds.map { element ->
            val outbound = element.jsonObject
            val type = outbound["type"]?.toString()?.trim('"')
            // Proxy endpoint names must use bootstrap DNS; user DNS may be unavailable until VPN is up.
            if ("server" in outbound && type !in setOf("direct", "block")) {
                JsonObject(outbound + ("domain_resolver" to JsonPrimitive(bootstrapTag)))
            } else element
        }
        val routedOutbounds = if (profile.appPackages.isNotEmpty()) {
            outbounds + buildJsonObject { put("type", "direct"); put("tag", APP_DIRECT) }
        } else outbounds
        val route = if (profile.appPackages.isNotEmpty()) {
            val existingRoute = root["route"]?.jsonObject ?: JsonObject(emptyMap())
            val existingRules = existingRoute["rules"]?.jsonArray ?: JsonArray(emptyList())
            val lastDnsHijack = existingRules.indexOfLast {
                it.jsonObject["action"] == JsonPrimitive("hijack-dns")
            }
            val directAppRule = buildJsonObject {
                put("package_name", strings(profile.appPackages))
                put("action", "route")
                put("outbound", APP_DIRECT)
            }
            JsonObject(existingRoute + ("rules" to buildJsonArray {
                if (lastDnsHijack < 0) {
                    add(buildJsonObject { put("protocol", "dns"); put("action", "hijack-dns") })
                    add(directAppRule)
                }
                existingRules.forEachIndexed { index, rule ->
                    add(rule)
                    if (index == lastDnsHijack) add(directAppRule)
                }
            }))
        } else root["route"]
        val updatedRoot = root + mapOf("dns" to dns, "outbounds" to JsonArray(routedOutbounds)) +
            (if (route == null) emptyMap() else mapOf("route" to route))
        return Json.encodeToString(JsonObject.serializer(), JsonObject(updatedRoot))
    }

    fun dnsOnly(profile: PxlDnsProfile, bootstrapIp: String? = null): String {
        val bootstrap = bootstrapServer(bootstrapIp)
        val remote = remoteServer(profile, BOOTSTRAP)
        val root = buildJsonObject {
            put("log", buildJsonObject { put("level", "warn") })
            put("dns", buildJsonObject {
                put("servers", buildJsonArray { add(bootstrap); add(remote) })
                if (profile.domains.isNotEmpty() || profile.appPackages.isNotEmpty()) {
                    put("rules", buildJsonArray {
                        if (profile.appPackages.isNotEmpty()) add(buildJsonObject {
                            put("package_name", strings(profile.appPackages))
                            put("action", "route")
                            put("server", EXPRESSIVE)
                        })
                        if (profile.domains.isNotEmpty()) add(buildJsonObject {
                            put("domain_suffix", strings(profile.domains))
                            put("action", "route")
                            put("server", EXPRESSIVE)
                        })
                    })
                }
                put("final", if (profile.domains.isEmpty() && profile.appPackages.isEmpty()) EXPRESSIVE else BOOTSTRAP)
            })
            put("inbounds", buildJsonArray {
                add(buildJsonObject {
                    put("type", "tun")
                    put("tag", "tun-in")
                    put("address", strings(listOf("172.19.0.1/30")))
                    put("auto_route", true)
                    put("strict_route", true)
                    put("stack", "mixed")
                })
            })
            put("outbounds", buildJsonArray { add(buildJsonObject { put("type", "direct"); put("tag", "DIRECT") }) })
            put("route", buildJsonObject {
                put("auto_detect_interface", true)
                put("default_domain_resolver", BOOTSTRAP)
                put("rules", buildJsonArray {
                    add(buildJsonObject { put("protocol", "dns"); put("action", "hijack-dns") })
                })
                put("final", "DIRECT")
            })
        }
        return Json.encodeToString(JsonObject.serializer(), root)
    }

    private fun bootstrapServer(ip: String?) = buildJsonObject {
        put("type", if (ip.isNullOrBlank()) "local" else "udp")
        put("tag", BOOTSTRAP)
        if (!ip.isNullOrBlank()) put("server", ip)
    }

    private fun remoteServer(profile: PxlDnsProfile, bootstrapTag: String, tag: String = EXPRESSIVE, detour: String? = null): JsonObject {
        val endpoint = PxlDnsEndpoint.parse(profile.address)
        return buildJsonObject {
            put("type", endpoint.type)
            put("tag", tag)
            put("server", endpoint.host)
            put("server_port", endpoint.port)
            if (endpoint.type == "https") put("path", endpoint.path)
            if (detour != null) put("detour", detour)
            if (':' !in endpoint.host && endpoint.host.any { it.isLetter() }) put("domain_resolver", bootstrapTag)
        }
    }

    private fun strings(values: List<String>) = buildJsonArray { values.forEach { add(it) } }
}
