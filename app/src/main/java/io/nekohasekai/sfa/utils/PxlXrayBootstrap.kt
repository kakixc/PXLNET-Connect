package io.nekohasekai.sfa.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Resolves XHTTP dial hosts on the underlying network while preserving TLS/REALITY serverName. */
internal object PxlXrayBootstrap {
    fun resolveOutboundAddresses(content: String, resolver: (String) -> String?): String {
        val root = Json.parseToJsonElement(content).jsonObject
        val outbounds = root["outbounds"]?.jsonArray ?: return content
        var changed = false
        val updated = outbounds.map outboundMap@{ element ->
            val outbound = element.jsonObject
            if (outbound["protocol"]?.jsonPrimitive?.content != "vless") return@outboundMap element
            val settings = outbound["settings"]?.jsonObject ?: return@outboundMap element
            val servers = settings["vnext"]?.jsonArray ?: return@outboundMap element
            val resolvedServers = servers.map serverMap@{ serverElement ->
                val server = serverElement.jsonObject
                val host = server["address"]?.jsonPrimitive?.content ?: return@serverMap serverElement
                val address = resolver(host)?.takeIf(String::isNotBlank) ?: return@serverMap serverElement
                if (address == host) return@serverMap serverElement
                changed = true
                JsonObject(server + ("address" to JsonPrimitive(address)))
            }
            JsonObject(outbound + ("settings" to JsonObject(settings + ("vnext" to JsonArray(resolvedServers)))))
        }
        return if (changed) JsonObject(root + ("outbounds" to JsonArray(updated))).toString() else content
    }
}
