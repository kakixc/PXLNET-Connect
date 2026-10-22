package io.nekohasekai.sfa.utils

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Builds one Xray outbound from a Smart VLESS/XHTTP link; it does not start a VPN engine. */
internal object PxlXhttpConfig {
    data class Presentation(val tag: String, val label: String, val type: String)

    fun presentation(link: String): Presentation {
        val outbound = outbound(link)
        val uri = URI(link.trim().replace(" ", "%20"))
        val security = outbound.getValue("streamSettings").jsonObject.getValue("security").jsonPrimitive.content
        return Presentation(
            tag = outbound.getValue("tag").jsonPrimitive.content,
            label = uri.rawFragment?.let(::decode)?.trim().orEmpty().ifBlank { requireNotNull(uri.host) },
            type = if (security == "reality") "vless reality" else "vless",
        )
    }

    class RuntimeConfig(val xray: JsonObject, val singboxOutbounds: List<JsonObject>) {
        override fun toString(): String = "PxlXhttpRuntimeConfig(nodes=${singboxOutbounds.size})"
    }

    /** The caller supplies fresh local SOCKS passwords; never use subscription credentials for them. */
    fun runtime(links: List<String>, localPort: Int, passwords: Map<String, String>): RuntimeConfig {
        require(localPort in 1..65535) { "Invalid local SOCKS port" }
        val nodes = links.map(::outbound).distinctBy { it.getValue("tag").jsonPrimitive.content }
        require(nodes.isNotEmpty()) { "No XHTTP servers" }
        val tags = nodes.map { it.getValue("tag").jsonPrimitive.content }
        require(tags.all { !passwords[it].isNullOrBlank() }) { "Missing local SOCKS password" }
        val xray = buildJsonObject {
            put("inbounds", buildJsonArray {
                add(buildJsonObject {
                    put("tag", "pxlnet-xhttp-socks")
                    put("listen", "127.0.0.1")
                    put("port", localPort)
                    put("protocol", "socks")
                    put("settings", buildJsonObject {
                        put("auth", "password")
                        put("accounts", buildJsonArray {
                            tags.forEach { tag ->
                                add(buildJsonObject {
                                    put("user", tag)
                                    put("pass", passwords.getValue(tag))
                                })
                            }
                        })
                        put("udp", false)
                    })
                })
            })
            put("outbounds", buildJsonArray {
                nodes.forEach(::add)
                add(buildJsonObject { put("tag", "pxlnet-xhttp-reject"); put("protocol", "blackhole") })
            })
            put("routing", buildJsonObject {
                put("rules", buildJsonArray {
                    tags.forEach { tag ->
                        add(buildJsonObject {
                            put("type", "field")
                            put("user", buildJsonArray { add(JsonPrimitive(tag)) })
                            put("outboundTag", tag)
                        })
                    }
                    add(buildJsonObject {
                        put("type", "field")
                        put("network", "tcp,udp")
                        put("outboundTag", "pxlnet-xhttp-reject")
                    })
                })
            })
        }
        val singboxOutbounds = tags.map { tag ->
            buildJsonObject {
                put("type", "socks")
                put("tag", tag)
                put("server", "127.0.0.1")
                put("server_port", localPort)
                put("version", "5")
                put("username", tag)
                put("password", passwords.getValue(tag))
            }
        }
        return RuntimeConfig(xray, singboxOutbounds)
    }

    /** Adds XHTTP proxies only to an in-memory Smart config, after its Xray listener is ready. */
    fun augmentSmartConfig(content: String, runtime: RuntimeConfig): String {
        val root = Json.parseToJsonElement(content).jsonObject
        val original = root["outbounds"]?.jsonArray ?: error("Missing Smart outbounds")
        val existing = original.map { it.jsonObject }
        val existingTags = existing.map { it["tag"]?.jsonPrimitive?.content ?: error("Untagged Smart outbound") }
        require(existingTags.size == existingTags.toSet().size) { "Duplicate Smart outbound tag" }
        val newTags = runtime.singboxOutbounds.map { it.getValue("tag").jsonPrimitive.content }
        require(newTags.isNotEmpty() && newTags.size == newTags.toSet().size) { "Invalid XHTTP outbound tags" }
        require(newTags.none(existingTags::contains)) { "XHTTP outbound tag collision" }
        require(root["route"]?.jsonObject?.get("final")?.jsonPrimitive?.content == "PXLNET") {
            "Not a Smart VPN configuration"
        }
        val selector = existing.singleOrNull { it["tag"]?.jsonPrimitive?.content == "PXLNET" }
            ?: error("Missing Smart server selector")
        val automatic = existing.singleOrNull { it["tag"]?.jsonPrimitive?.content == "AUTO" }
            ?: error("Missing Smart automatic selector")
        require(selector["type"]?.jsonPrimitive?.content == "selector" &&
            automatic["type"]?.jsonPrimitive?.content == "selector"
        ) { "Unsupported Smart selector layout" }
        require(selector["outbounds"]?.jsonArray?.any { it.jsonPrimitive.content == "AUTO" } == true) {
            "Smart server selector does not contain AUTO"
        }
        val updated = JsonArray(existing.map { outbound ->
            if (outbound !== selector && outbound !== automatic) return@map outbound
            val members = outbound["outbounds"]?.jsonArray ?: error("Missing Smart selector members")
            require(newTags.none { tag -> members.any { it.jsonPrimitive.content == tag } }) {
                "XHTTP selector member collision"
            }
            JsonObject(outbound + ("outbounds" to JsonArray(members + newTags.map(::JsonPrimitive))))
        } + runtime.singboxOutbounds)
        return Json.encodeToString(JsonObject.serializer(), JsonObject(root + ("outbounds" to updated)))
    }

    /** Adds manual XHTTP choices to Legacy without changing its existing AUTO urltest. */
    fun augmentLegacyConfig(content: String, runtime: RuntimeConfig): String {
        val root = Json.parseToJsonElement(content).jsonObject
        require(root["route"]?.jsonObject?.get("final")?.jsonPrimitive?.content == "PXLNET") {
            "Not a Legacy VPN configuration"
        }
        val existing = root["outbounds"]?.jsonArray?.map { it.jsonObject } ?: error("Missing Legacy outbounds")
        val existingTags = existing.map { it["tag"]?.jsonPrimitive?.content ?: error("Untagged Legacy outbound") }
        require(existingTags.size == existingTags.toSet().size) { "Duplicate Legacy outbound tag" }
        val newTags = runtime.singboxOutbounds.map { it.getValue("tag").jsonPrimitive.content }
        require(newTags.isNotEmpty() && newTags.size == newTags.toSet().size) { "Invalid XHTTP outbound tags" }
        require(newTags.none(existingTags::contains)) { "XHTTP outbound tag collision" }
        val selector = existing.singleOrNull { it["tag"]?.jsonPrimitive?.content == "PXLNET" }
            ?: error("Missing Legacy server selector")
        require(selector["type"]?.jsonPrimitive?.content == "selector") { "Unsupported Legacy selector" }
        val members = selector["outbounds"]?.jsonArray ?: error("Missing Legacy selector members")
        val auto = existing.singleOrNull { it["tag"]?.jsonPrimitive?.content == "AUTO" }
        if (auto != null) {
            require(auto["type"]?.jsonPrimitive?.content == "urltest" &&
                members.any { it.jsonPrimitive.content == "AUTO" }) { "Unsupported Legacy AUTO" }
        } else {
            require(members.any { it.jsonPrimitive.content == PxlSubscriptionConverter.XHTTP_PENDING_TAG }) {
                "Missing Legacy XHTTP-only placeholder"
            }
        }
        val updatedSelector = JsonObject(selector +
            ("outbounds" to JsonArray(members + newTags.map(::JsonPrimitive))))
        val updated = JsonArray(existing.map { if (it === selector) updatedSelector else it } + runtime.singboxOutbounds)
        return Json.encodeToString(JsonObject.serializer(), JsonObject(root + ("outbounds" to updated)))
    }

    fun outbound(link: String): JsonObject {
        val uri = runCatching { URI(link.trim().replace(" ", "%20")) }
            .getOrElse { throw IllegalArgumentException("Invalid XHTTP link") }
        require(uri.scheme.equals("vless", ignoreCase = true)) { "Not a VLESS link" }
        val server = requireNotNull(uri.host) { "XHTTP server is missing" }
        val port = uri.port.takeIf { it in 1..65535 } ?: error("XHTTP port is missing")
        val uuid = decode(uri.rawUserInfo.orEmpty())
        require(runCatching { UUID.fromString(uuid) }.isSuccess) { "Invalid VLESS identity" }
        val query = uri.rawQuery.orEmpty().split('&').filter(String::isNotBlank).associate { part ->
            val pair = part.split('=', limit = 2)
            decode(pair[0]) to decode(pair.getOrElse(1) { "" })
        }
        require(query["type"].equals("xhttp", ignoreCase = true)) { "Not an XHTTP link" }
        val security = query["security"]?.lowercase()
        require(security == "reality" || security == "tls") { "XHTTP security is unsupported" }

        val stableFields = listOf(server, port.toString(), uuid) + query.toSortedMap().map { (key, value) -> "$key=$value" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(stableFields.joinToString("\u0000").toByteArray(StandardCharsets.UTF_8))
            .take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val stream = buildJsonObject {
            put("network", "xhttp")
            put("security", security)
            put("xhttpSettings", buildJsonObject {
                listOf("path", "host", "mode").forEach { key ->
                    query[key]?.takeIf(String::isNotBlank)?.let { put(key, it) }
                }
            })
            val serverName = query["sni"].orEmpty().ifBlank { server }
            val fingerprint = query["fp"].orEmpty().ifBlank { "chrome" }
            if (security == "reality") {
                val publicKey = query["pbk"] ?: query["public_key"]
                require(!publicKey.isNullOrBlank()) { "REALITY public key is missing" }
                put("realitySettings", buildJsonObject {
                    put("serverName", serverName)
                    put("fingerprint", fingerprint)
                    put("password", publicKey)
                    put("shortId", query["sid"] ?: query["short_id"] ?: "")
                    put("spiderX", query["spx"] ?: "")
                })
            } else {
                put("tlsSettings", buildJsonObject {
                    put("serverName", serverName)
                    put("fingerprint", fingerprint)
                })
            }
        }
        return buildJsonObject {
            put("protocol", "vless")
            put("tag", "xhttp-$digest")
            put("settings", buildJsonObject {
                put("vnext", buildJsonArray {
                    add(buildJsonObject {
                        put("address", server)
                        put("port", port)
                        put("users", buildJsonArray {
                            add(buildJsonObject {
                                put("id", uuid)
                                put("encryption", "none")
                                query["flow"]?.takeIf(String::isNotBlank)?.let { put("flow", it) }
                            })
                        })
                    })
                })
            })
            put("streamSettings", stream)
        }
    }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }
            .getOrElse { throw IllegalArgumentException("Invalid XHTTP encoding") }
}
