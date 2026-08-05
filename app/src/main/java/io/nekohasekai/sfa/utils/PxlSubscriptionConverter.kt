package io.nekohasekai.sfa.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

/** Converts common share-link subscriptions into a sing-box configuration. */
object PxlSubscriptionConverter {
    data class Result(
        val config: String,
        val serverCount: Int,
    )

    data class ServerSelection(
        val tags: List<String>,
        val selectedTag: String,
    )

    private val json = Json { prettyPrint = true }

    fun convert(content: String, smartRouting: Boolean = true): Result {
        val trimmed = content.trim().removePrefix("\uFEFF")
        if (trimmed.startsWith("{")) {
            return Result(sanitizeConfig(trimmed), 0)
        }

        val decoded = decodeSubscription(trimmed)
        val links =
            decoded.lineSequence()
                .map(String::trim)
                .filter { it.startsWith("vless://", true) || it.startsWith("hysteria2://", true) || it.startsWith("hy2://", true) }
                .toList()

        require(links.isNotEmpty()) {
            "Подписка не содержит поддерживаемых серверов VLESS или Hysteria2"
        }

        val usedTags = mutableSetOf<String>()
        val nodes =
            links.map { link ->
                when {
                    link.startsWith("vless://", true) -> parseVless(link, usedTags)
                    else -> parseHysteria2(link, usedTags)
                }
            }
        val tags = nodes.map { it.getValue("tag").toString().trim('"') }

        val config =
            buildJsonObject {
                put(
                    "log",
                    buildJsonObject {
                        put("level", "info")
                        put("timestamp", true)
                    },
                )
                put(
                    "dns",
                    buildJsonObject {
                        put(
                            "servers",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("type", "local")
                                        put("tag", "local")
                                    },
                                )
                            },
                        )
                        put("final", "local")
                    },
                )
                put(
                    "inbounds",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("type", "tun")
                                put("tag", "tun-in")
                                put("address", JsonArray(listOf(JsonPrimitive("172.19.0.1/30"))))
                                put("auto_route", true)
                                put("strict_route", true)
                                put("stack", "mixed")
                            },
                        )
                    },
                )
                put(
                    "outbounds",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("type", "selector")
                                put("tag", "PXLNET")
                                put("outbounds", stringArray(listOf("AUTO") + tags))
                                put("default", "AUTO")
                                put("interrupt_exist_connections", true)
                            },
                        )
                        add(
                            buildJsonObject {
                                put("type", "urltest")
                                put("tag", "AUTO")
                                put("outbounds", stringArray(tags))
                                put("url", "https://www.gstatic.com/generate_204")
                                put("interval", "3m")
                                put("tolerance", 50)
                                put("interrupt_exist_connections", true)
                            },
                        )
                        nodes.forEach(::add)
                        add(
                            buildJsonObject {
                                put("type", "direct")
                                put("tag", "DIRECT")
                            },
                        )
                    },
                )
                put(
                    "route",
                    buildJsonObject {
                        put("auto_detect_interface", true)
                        put(
                            "rules",
                            buildJsonArray {
                                add(buildJsonObject { put("action", "sniff") })
                                add(
                                    buildJsonObject {
                                        put("protocol", "dns")
                                        put("action", "hijack-dns")
                                    },
                                )
                                add(
                                    buildJsonObject {
                                        put("ip_is_private", true)
                                        put("outbound", "DIRECT")
                                    },
                                )
                                if (smartRouting) {
                                    add(
                                        buildJsonObject {
                                            put("domain_suffix", stringArray(listOf("2ip.ru")))
                                            put("outbound", "PXLNET")
                                        },
                                    )
                                    add(
                                        buildJsonObject {
                                            put(
                                                "domain_suffix",
                                                stringArray(listOf("ru", "su", "xn--p1ai")),
                                            )
                                            put("outbound", "DIRECT")
                                        },
                                    )
                                }
                            },
                        )
                        put("final", "PXLNET")
                    },
                )
            }

        return Result(json.encodeToString(JsonObject.serializer(), config), nodes.size)
    }

    fun removeRemoteRoutingDependencies(content: String): String {
        val root = runCatching { json.parseToJsonElement(content).jsonObject }.getOrNull() ?: return content
        val route = root["route"]?.jsonObject ?: return content
        val hasRemoteRuleSets = "rule_set" in route
        val rules = route["rules"]?.jsonArray
        val hasRuleSetRules = rules?.any { "rule_set" in it.jsonObject } == true
        if (!hasRemoteRuleSets && !hasRuleSetRules) return content

        val cleanRoute = buildJsonObject {
            route.forEach { (key, value) ->
                when (key) {
                    "rule_set" -> Unit
                    "rules" -> put(
                        key,
                        JsonArray(rules.orEmpty().filterNot { "rule_set" in it.jsonObject }),
                    )
                    else -> put(key, value)
                }
            }
        }
        val cleanRoot = buildJsonObject {
            root.forEach { (key, value) -> put(key, if (key == "route") cleanRoute else value) }
        }
        return json.encodeToString(JsonObject.serializer(), cleanRoot)
    }

    /**
     * Migrates generated and native profiles to options supported by the bundled sing-box core.
     * Hysteria2 runs over QUIC and rejects the uTLS TCP fingerprint option with
     * "unsupported usage for uTLS" even though some subscription generators include it.
     */
    fun sanitizeConfig(content: String): String =
        removeUnsupportedHysteriaUtls(removeRemoteRoutingDependencies(content))

    private fun removeUnsupportedHysteriaUtls(content: String): String {
        val root = runCatching { json.parseToJsonElement(content).jsonObject }.getOrNull() ?: return content
        val outbounds = root["outbounds"]?.jsonArray ?: return content
        var changed = false
        val cleanOutbounds = JsonArray(
            outbounds.map { element ->
                val outbound = runCatching { element.jsonObject }.getOrNull() ?: return@map element
                if (outbound["type"]?.jsonPrimitive?.content != "hysteria2") return@map element
                val tls = outbound["tls"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: return@map element
                if ("utls" !in tls) return@map element
                changed = true
                val cleanTls = buildJsonObject {
                    tls.forEach { (key, value) -> if (key != "utls") put(key, value) }
                }
                buildJsonObject {
                    outbound.forEach { (key, value) -> put(key, if (key == "tls") cleanTls else value) }
                }
            },
        )
        if (!changed) return content
        val cleanRoot = buildJsonObject {
            root.forEach { (key, value) -> put(key, if (key == "outbounds") cleanOutbounds else value) }
        }
        return json.encodeToString(JsonObject.serializer(), cleanRoot)
    }

    fun serverSelection(content: String): ServerSelection {
        val root = runCatching { json.parseToJsonElement(content).jsonObject }.getOrNull()
            ?: return ServerSelection(emptyList(), "AUTO")
        val selector = root["outbounds"]?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull { it["tag"]?.jsonPrimitive?.content == "PXLNET" }
            ?: return ServerSelection(emptyList(), "AUTO")
        val tags = selector["outbounds"]?.jsonArray
            ?.map { it.jsonPrimitive.content }
            .orEmpty()
        val selected = selector["default"]?.jsonPrimitive?.content
            ?.takeIf(tags::contains)
            ?: tags.firstOrNull()
            ?: "AUTO"
        return ServerSelection(tags, selected)
    }

    fun selectServer(content: String, tag: String): String {
        val cleaned = sanitizeConfig(content)
        val root = json.parseToJsonElement(cleaned).jsonObject
        val outbounds = root["outbounds"]?.jsonArray ?: return cleaned
        var changed = false
        val updatedOutbounds = JsonArray(
            outbounds.map { element ->
                val outbound = element.jsonObject
                if (outbound["tag"]?.jsonPrimitive?.content != "PXLNET") return@map element
                val available = outbound["outbounds"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
                if (tag !in available) return@map element
                changed = true
                buildJsonObject {
                    outbound.forEach { (key, value) -> put(key, if (key == "default") JsonPrimitive(tag) else value) }
                }
            },
        )
        if (!changed) return cleaned
        val updatedRoot = buildJsonObject {
            root.forEach { (key, value) -> put(key, if (key == "outbounds") updatedOutbounds else value) }
        }
        return json.encodeToString(JsonObject.serializer(), updatedRoot)
    }

    private fun parseVless(link: String, usedTags: MutableSet<String>): JsonObject {
        val uri = URI(link.replace(" ", "%20"))
        val host = requireHost(uri)
        val port = requirePort(uri)
        val uuid = decode(uri.rawUserInfo.orEmpty())
        require(uuid.isNotBlank()) { "В VLESS-ссылке отсутствует UUID" }
        val query = queryParameters(uri.rawQuery)
        val tag = uniqueTag(serverTag(uri.rawFragment, host, "VLESS"), usedTags)

        return buildJsonObject {
            put("type", "vless")
            put("tag", tag)
            put("server", host)
            put("server_port", port)
            put("uuid", uuid)
            query["flow"]?.takeIf(String::isNotBlank)?.let { put("flow", it) }
            put("packet_encoding", query["packetEncoding"] ?: query["packet_encoding"] ?: "xudp")

            val security = query["security"]?.lowercase()
            if (security == "tls" || security == "reality") {
                put("tls", tlsOptions(query, reality = security == "reality"))
            }
            transportOptions(query)?.let { put("transport", it) }
        }
    }

    private fun parseHysteria2(link: String, usedTags: MutableSet<String>): JsonObject {
        val uri =
            URI(
                link.replaceFirst(Regex("^hy2://", RegexOption.IGNORE_CASE), "hysteria2://")
                    .replace(" ", "%20"),
            )
        val host = requireHost(uri)
        val port = requirePort(uri)
        val query = queryParameters(uri.rawQuery)
        val tag = uniqueTag(serverTag(uri.rawFragment, host, "Hysteria2"), usedTags)
        val password = decode(uri.rawUserInfo.orEmpty())

        return buildJsonObject {
            put("type", "hysteria2")
            put("tag", tag)
            put("server", host)
            put("server_port", port)
            if (password.isNotBlank()) put("password", password)
            query["upmbps"]?.toIntOrNull()?.let { put("up_mbps", it) }
            query["downmbps"]?.toIntOrNull()?.let { put("down_mbps", it) }
            val obfsType = query["obfs"]
            val obfsPassword = query["obfs-password"] ?: query["obfs_password"]
            if (!obfsType.isNullOrBlank() && !obfsPassword.isNullOrBlank()) {
                put(
                    "obfs",
                    buildJsonObject {
                        put("type", obfsType)
                        put("password", obfsPassword)
                    },
                )
            }
            put("tls", tlsOptions(query, reality = false, allowUtls = false))
        }
    }

    private fun tlsOptions(
        query: Map<String, String>,
        reality: Boolean,
        allowUtls: Boolean = true,
    ): JsonObject = buildJsonObject {
        put("enabled", true)
        (query["sni"] ?: query["server_name"] ?: query["peer"])
            ?.takeIf(String::isNotBlank)
            ?.let { put("server_name", it) }
        if (query["insecure"].isTrue() || query["allowInsecure"].isTrue()) put("insecure", true)
        query["alpn"]?.takeIf(String::isNotBlank)?.let { put("alpn", stringArray(it.split(','))) }
        query["fp"]?.takeIf { allowUtls && it.isNotBlank() }?.let { fingerprint ->
            put(
                "utls",
                buildJsonObject {
                    put("enabled", true)
                    put("fingerprint", fingerprint)
                },
            )
        }
        if (reality) {
            val publicKey = query["pbk"] ?: query["public_key"]
            require(!publicKey.isNullOrBlank()) { "В VLESS Reality-ссылке отсутствует public key" }
            put(
                "reality",
                buildJsonObject {
                    put("enabled", true)
                    put("public_key", publicKey)
                    (query["sid"] ?: query["short_id"])
                        ?.takeIf(String::isNotBlank)
                        ?.let { put("short_id", it) }
                },
            )
        }
    }

    private fun transportOptions(query: Map<String, String>): JsonObject? = when (query["type"]?.lowercase()) {
        "ws" ->
            buildJsonObject {
                put("type", "ws")
                query["path"]?.takeIf(String::isNotBlank)?.let { put("path", it) }
                query["host"]?.takeIf(String::isNotBlank)?.let {
                    put("headers", buildJsonObject { put("Host", it) })
                }
            }
        "grpc" ->
            buildJsonObject {
                put("type", "grpc")
                (query["serviceName"] ?: query["service_name"])
                    ?.takeIf(String::isNotBlank)
                    ?.let { put("service_name", it) }
            }
        "http", "h2" ->
            buildJsonObject {
                put("type", "http")
                query["host"]?.takeIf(String::isNotBlank)?.let { put("host", stringArray(listOf(it))) }
                query["path"]?.takeIf(String::isNotBlank)?.let { put("path", it) }
            }
        else -> null
    }

    private fun decodeSubscription(content: String): String {
        if (content.lineSequence().any { isSupportedLink(it.trim()) }) return content
        val compact = content.filterNot(Char::isWhitespace)
        val decoded =
            sequenceOf(
                { Base64.getDecoder().decode(padBase64(compact)) },
                { Base64.getUrlDecoder().decode(padBase64(compact)) },
            ).mapNotNull { decoder -> runCatching { decoder() }.getOrNull() }
                .map { String(it, StandardCharsets.UTF_8) }
                .firstOrNull { candidate -> candidate.lineSequence().any { isSupportedLink(it.trim()) } }
        return decoded ?: content
    }

    private fun isSupportedLink(value: String): Boolean = value.startsWith("vless://", true) || value.startsWith("hysteria2://", true) || value.startsWith("hy2://", true)

    private fun queryParameters(rawQuery: String?): Map<String, String> = rawQuery.orEmpty().split('&')
        .filter(String::isNotBlank)
        .associate { part ->
            val pair = part.split('=', limit = 2)
            decode(pair[0]) to decode(pair.getOrElse(1) { "" })
        }

    private fun serverTag(rawFragment: String?, host: String, protocol: String): String {
        val source = decode(rawFragment.orEmpty()).ifBlank { host }
        val lower = source.lowercase()
        val flag =
            when {
                "germany" in lower || "герман" in lower || Regex("(^|[^a-z])de([^a-z]|$)").containsMatchIn(lower) -> "🇩🇪"
                "finland" in lower || "финлянд" in lower || Regex("(^|[^a-z])fi([^a-z]|$)").containsMatchIn(lower) -> "🇫🇮"
                else -> "🌐"
            }
        val withoutFlag = source.replace(Regex("^[\\p{So}\\s]+"), "").trim()
        val withProtocol = if (withoutFlag.contains(protocol, true)) withoutFlag else "$withoutFlag · $protocol"
        return "$flag $withProtocol"
    }

    private fun uniqueTag(tag: String, usedTags: MutableSet<String>): String {
        var candidate = tag
        var suffix = 2
        while (!usedTags.add(candidate)) candidate = "$tag ($suffix++)"
        return candidate
    }

    private fun requireHost(uri: URI): String = requireNotNull(uri.host) { "В ссылке сервера отсутствует адрес" }.removePrefix("[").removeSuffix("]")

    private fun requirePort(uri: URI): Int {
        require(uri.port in 1..65535) { "В ссылке сервера отсутствует корректный порт" }
        return uri.port
    }

    private fun decode(value: String): String = URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())

    private fun padBase64(value: String): String = value + "=".repeat((4 - value.length % 4) % 4)

    private fun String?.isTrue(): Boolean = this == "1" || this.equals("true", true)

    private fun stringArray(values: List<String>): JsonArray = JsonArray(values.map(::JsonPrimitive))
}
