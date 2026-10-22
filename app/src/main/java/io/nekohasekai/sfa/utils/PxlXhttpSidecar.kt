package io.nekohasekai.sfa.utils

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Private companion data for XHTTP nodes omitted from the persisted sing-box config.
 *
 * A sidecar is useful only for the exact config file it was written alongside.  This
 * prevents an old subscription's XHTTP credentials from being attached to a later
 * config, even when a profile keeps the same file name.
 */
internal object PxlXhttpSidecar {
    private const val VERSION = 1
    private const val SUFFIX = ".pxlnet-xhttp"
    private const val TEMP_SUFFIX = ".new"
    private const val BACKUP_SUFFIX = ".bak"
    private const val CONFIG_TEMP_SUFFIX = ".pxlnet-config.new"
    private const val CONFIG_BACKUP_SUFFIX = ".pxlnet-config.bak"
    private val json = Json

    class Snapshot(
        val links: List<String>,
        val smartRevisions: HTTPClient.SmartRevisions?,
        val preferredTag: String?,
    ) {
        override fun toString(): String =
            "PxlXhttpSidecar.Snapshot(linkCount=${links.size}, hasSmartRevisions=${smartRevisions != null})"
    }

    /**
     * Atomically replaces the sidecar after [configFile] itself has been persisted.
     * It deliberately returns false instead of logging data or surfacing link contents.
     */
    fun write(
        configFile: File,
        links: List<String>,
        smartRevisions: HTTPClient.SmartRevisions?,
        preferredTag: String? = null,
    ): Boolean = synchronized(this) {
        runCatching {
            require(configFile.isFile)
            require(links.all { it.isNotBlank() })
            require(smartRevisions == null || revisionsAreValid(smartRevisions))
            require(preferredTag == null || preferredTag in nodeTags(links))

            val sidecar = sidecarFile(configFile)
            val configHash = sha256(configFile.readBytes())
            val content = json.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(),
                buildJsonObject {
                    put("version", if (smartRevisions == null) VERSION + 1 else VERSION)
                    put("config_sha256", configHash)
                    if (smartRevisions == null) {
                        put("format", "legacy")
                    } else {
                        put("smart_revisions", buildJsonObject {
                            put("config", smartRevisions.config)
                            put("health", smartRevisions.health)
                            put("policy", smartRevisions.policy)
                            put("compatibility", smartRevisions.compatibility)
                        })
                    }
                    put("links", buildJsonArray { links.forEach { add(JsonPrimitive(it)) } })
                    preferredTag?.let { put("preferred_tag", it) }
                },
            )
            atomicWrite(sidecar, content.toByteArray(StandardCharsets.UTF_8))
            true
        }.getOrDefault(false)
    }

    /** Invalidates any prior snapshot before a profile update, including when config bytes stay the same. */
    fun invalidate(configFile: File): Boolean = synchronized(this) {
        runCatching {
            atomicWrite(sidecarFile(configFile), "{\"version\":$VERSION,\"invalidated\":true}".toByteArray(StandardCharsets.UTF_8))
            true
        }.getOrDefault(false)
    }

    /**
     * Replaces a persisted profile configuration without ever pairing it with the previous
     * profile's XHTTP snapshot.  If publishing the config fails after staging its predecessor,
     * the predecessor is restored before reporting failure.  A missing sidecar is deliberately
     * safer than preserving credentials for an uncertain config write.
     */
    fun replaceConfig(
        configFile: File,
        content: String,
        response: HTTPClient.Result? = null,
    ): Boolean = synchronized(this) {
        runCatching {
            val previousTag = if (response != null) readInternal(configFile, null)?.preferredTag else null
            invalidateInternal(configFile)
            atomicWriteConfig(configFile, content.toByteArray(StandardCharsets.UTF_8))
            if (response != null) {
                check(writeResponse(configFile, response, previousTag)) { "Cannot store XHTTP profile snapshot" }
            }
            true
        }.getOrDefault(false)
    }

    /**
     * Removes every private artifact belonging to [configFile] before the profile can be deleted.
     *
     * Profile file IDs can be reused after a deletion.  Leaving either the published sidecar or a
     * staged file behind would allow credentials from the deleted profile to be considered during
     * a later import that happens to receive the same path.
     */
    fun remove(configFile: File): Boolean = synchronized(this) {
        runCatching {
            val sidecar = sidecarFile(configFile)
            val temporary = File(requireNotNull(sidecar.parentFile), sidecar.name + TEMP_SUFFIX)
            val backup = File(requireNotNull(sidecar.parentFile), sidecar.name + BACKUP_SUFFIX)
            listOf(sidecar, temporary, backup).forEach { artifact ->
                check(!artifact.exists() || artifact.delete()) { "Could not remove XHTTP profile snapshot" }
            }
            true
        }.getOrDefault(false)
    }

    /** Called after persisting a response's config; unknown Smart formats fail closed. */
    fun writeResponse(configFile: File, response: HTTPClient.Result, previousTag: String? = null): Boolean {
        if (response.subscriptionStatus == null && response.subscriptionFormat == null &&
            response.smartRevisions == null && response.xhttpLinks.isNotEmpty()
        ) {
            val retained = previousTag?.takeIf { it in nodeTags(response.xhttpLinks) }
            val selected = retained ?: if (PxlSubscriptionConverter.serverSelection(configFile.readText()).tags.isEmpty()) {
                PxlXhttpConfig.outbound(response.xhttpLinks.first()).getValue("tag").jsonPrimitive.content
            } else null
            return write(configFile, response.xhttpLinks, null,
                selected)
        }
        if (!response.subscriptionStatus.equals("active", ignoreCase = true) ||
            !response.subscriptionFormat.equals("singbox", ignoreCase = true)
        ) return true
        val revisions = response.smartRevisions ?: return false
        return write(configFile, response.xhttpLinks, revisions,
            previousTag?.takeIf { it in nodeTags(response.xhttpLinks) })
    }

    /** A manual XHTTP selection is valid only for the exact saved Smart snapshot. */
    fun select(configFile: File, tag: String?): Boolean = synchronized(this) {
        val snapshot = readInternal(configFile, null) ?: return false
        write(configFile, snapshot.links, snapshot.smartRevisions, tag)
    }

    /** Returns null for a missing, corrupt, stale, or mismatched private sidecar. */
    fun read(configFile: File): Snapshot? = readInternal(configFile, expectedSmartRevisions = null)

    /**
     * Same as [read], additionally rejecting data from a different Smart response snapshot.
     */
    fun read(configFile: File, expectedSmartRevisions: HTTPClient.SmartRevisions): Snapshot? =
        readInternal(configFile, expectedSmartRevisions)

    private fun readInternal(configFile: File, expectedSmartRevisions: HTTPClient.SmartRevisions?): Snapshot? = synchronized(this) {
        runCatching {
            require(configFile.isFile)
            val sidecar = sidecarFile(configFile)
            require(sidecar.isFile)

            val root = json.parseToJsonElement(sidecar.readText(StandardCharsets.UTF_8)).jsonObject
            val version = root["version"]?.jsonPrimitive?.content
            require(version == VERSION.toString() || version == (VERSION + 1).toString())
            require(root["config_sha256"]?.jsonPrimitive?.content == sha256(configFile.readBytes()))
            val smartRevisions = if (version == VERSION.toString()) {
                val revisions = root["smart_revisions"]?.jsonObject ?: error("Missing Smart revisions")
                HTTPClient.SmartRevisions(
                    config = revisions["config"]?.jsonPrimitive?.content ?: error("Missing config revision"),
                    health = revisions["health"]?.jsonPrimitive?.content ?: error("Missing health revision"),
                    policy = revisions["policy"]?.jsonPrimitive?.content ?: error("Missing policy revision"),
                    compatibility = revisions["compatibility"]?.jsonPrimitive?.content ?: error("Missing compatibility revision"),
                ).also { require(revisionsAreValid(it)) }
            } else {
                require(root["format"]?.jsonPrimitive?.content == "legacy")
                require("smart_revisions" !in root)
                null
            }
            require(expectedSmartRevisions == null || smartRevisions == expectedSmartRevisions)
            val links = root["links"]?.jsonArray?.map { it.jsonPrimitive.content } ?: error("Missing XHTTP links")
            require(links.all { it.isNotBlank() })
            val preferredTag = root["preferred_tag"]?.jsonPrimitive?.content
            require(preferredTag == null || preferredTag in nodeTags(links))
            Snapshot(links, smartRevisions, preferredTag)
        }.getOrNull()
    }

    internal fun sidecarFile(configFile: File): File = File(configFile.parentFile, configFile.name + SUFFIX)

    private fun revisionsAreValid(revisions: HTTPClient.SmartRevisions): Boolean =
        listOf(revisions.config, revisions.health, revisions.policy, revisions.compatibility).all(String::isNotBlank)

    private fun nodeTags(links: List<String>): Set<String> =
        links.map { PxlXhttpConfig.outbound(it).getValue("tag").jsonPrimitive.content }.toSet()

    private fun atomicWrite(target: File, content: ByteArray) {
        val directory = requireNotNull(target.parentFile)
        require(directory.isDirectory || directory.mkdirs())
        val temporary = File(directory, target.name + TEMP_SUFFIX)
        val backup = File(directory, target.name + BACKUP_SUFFIX)
        check(!backup.exists() || backup.delete()) { "Could not discard stale XHTTP backup" }
        check(!temporary.exists() || temporary.delete()) { "Could not discard stale XHTTP temporary file" }
        try {
            FileOutputStream(temporary).use { output ->
                output.write(content)
                output.fd.sync()
            }
            if (target.exists()) {
                check(target.renameTo(backup)) { "Could not stage previous XHTTP sidecar" }
            }
            check(temporary.renameTo(target)) { "Could not publish XHTTP sidecar" }
            backup.delete()
        } finally {
            temporary.delete()
            // A failed invalidation must not resurrect old credentials. Missing
            // sidecars are intentionally treated as XHTTP unavailable.
        }
    }

    private fun invalidateInternal(configFile: File) {
        atomicWrite(sidecarFile(configFile), "{\"version\":$VERSION,\"invalidated\":true}".toByteArray(StandardCharsets.UTF_8))
    }

    private fun atomicWriteConfig(target: File, content: ByteArray) {
        val directory = requireNotNull(target.parentFile)
        require(directory.isDirectory || directory.mkdirs())
        val temporary = File(directory, target.name + CONFIG_TEMP_SUFFIX)
        val backup = File(directory, target.name + CONFIG_BACKUP_SUFFIX)
        check(!temporary.exists() || temporary.delete()) { "Could not discard staged profile config" }
        if (!target.exists() && backup.exists()) {
            // A process interruption after target -> backup must not turn the next update into
            // data loss. The sidecar has already been invalidated, so restoring only the config
            // cannot revive XHTTP credentials.
            check(backup.renameTo(target)) { "Could not restore staged profile config" }
        } else {
            check(!backup.exists() || backup.delete()) { "Could not discard stale profile config backup" }
        }
        var previousStaged = false
        try {
            FileOutputStream(temporary).use { output ->
                output.write(content)
                output.fd.sync()
            }
            if (target.exists()) {
                check(target.renameTo(backup)) { "Could not stage previous profile config" }
                previousStaged = true
            }
            check(temporary.renameTo(target)) { "Could not publish profile config" }
            // The new target is already durable. Retaining an orphaned config backup is harmless
            // and is preferable to reporting a failed commit after its new config was published.
            backup.delete()
        } catch (error: Throwable) {
            if (previousStaged && !target.exists() && backup.exists()) {
                backup.renameTo(target)
            }
            throw error
        } finally {
            temporary.delete()
        }
    }

    private fun sha256(content: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
