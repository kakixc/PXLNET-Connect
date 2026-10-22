package io.nekohasekai.sfa.utils

import java.io.File
import java.security.SecureRandom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Prepares, but never starts, the XHTTP companion for one exact saved profile. */
internal object PxlXhttpRuntimePreparer {
    class Prepared(
        val baseSingboxJson: String,
        val augmentedSingboxJson: String,
        val xrayJson: String,
        val nodeTags: List<String>,
        val smartRevisions: HTTPClient.SmartRevisions?,
    ) {
        override fun toString(): String = "PxlXhttpRuntimePrepared(nodes=${nodeTags.size})"
    }

    /** A missing, changed, or invalid sidecar leaves the current sing-box config untouched. */
    fun prepare(configFile: File, localPort: Int, random: SecureRandom = SecureRandom()): Prepared? {
        if (localPort !in 1..65535) return null
        return try {
            val base = configFile.readText()
            val snapshot = PxlXhttpSidecar.read(configFile) ?: return null
            if (snapshot.links.isEmpty() || configFile.readText() != base) return null

            val tags = snapshot.links.map { link ->
                PxlXhttpConfig.outbound(link).getValue("tag").jsonPrimitive.content
            }.distinct()
            val passwords = tags.associateWith {
                ByteArray(32).also(random::nextBytes).joinToString("") { byte ->
                    "%02x".format(byte.toInt() and 0xff)
                }
            }
            val runtime = PxlXhttpConfig.runtime(snapshot.links, localPort, passwords)
            val augmented = (if (snapshot.smartRevisions == null) {
                PxlXhttpConfig.augmentLegacyConfig(base, runtime)
            } else {
                PxlXhttpConfig.augmentSmartConfig(base, runtime)
            }).let { config ->
                snapshot.preferredTag?.takeIf(tags::contains)?.let { tag ->
                    PxlSubscriptionConverter.selectServer(config, tag)
                } ?: config
            }
            Prepared(
                baseSingboxJson = base,
                augmentedSingboxJson = augmented,
                xrayJson = Json.encodeToString(JsonObject.serializer(), runtime.xray),
                nodeTags = tags,
                smartRevisions = snapshot.smartRevisions,
            )
        } catch (_: Exception) {
            null
        }
    }
}
