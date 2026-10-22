package io.nekohasekai.sfa.utils

import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PxlXhttpSidecarTest {
    private val revisions = HTTPClient.SmartRevisions("config-1", "health-1", "policy-1", "compatibility-1")
    private val secretLink = "vless://00000000-0000-0000-0000-000000000001@vpn.example:443?type=xhttp&security=tls#private"

    @Test
    fun keepsManualXhttpChoiceOnlyWhileTheSameNodeRemains() = withConfig("{}") { config ->
        val tag = PxlXhttpConfig.outbound(secretLink).getValue("tag").jsonPrimitive.content
        assertTrue(PxlXhttpSidecar.write(config, listOf(secretLink), revisions))
        assertTrue(PxlXhttpSidecar.select(config, tag))
        assertEquals(tag, PxlXhttpSidecar.read(config)?.preferredTag)
        assertFalse(PxlXhttpSidecar.select(config, "unknown-node"))
        assertEquals(tag, PxlXhttpSidecar.read(config)?.preferredTag)

        val response = HTTPClient.Result("{}", null, "active", "singbox", xhttpLinks = listOf(secretLink), smartRevisions = revisions)
        assertTrue(PxlXhttpSidecar.replaceConfig(config, "{\"new\":true}", response))
        assertEquals(tag, PxlXhttpSidecar.read(config)?.preferredTag)

        val anotherLink = secretLink.replace("vpn.example", "another.example")
        val nextResponse = response.copy(xhttpLinks = listOf(anotherLink))
        assertTrue(PxlXhttpSidecar.replaceConfig(config, "{\"new\":false}", nextResponse))
        assertNull(PxlXhttpSidecar.read(config)?.preferredTag)
    }

    @Test
    fun legacySnapshotIsBoundToConfigWithoutInventingSmartRevisions() = withConfig("{}") { config ->
        val response = HTTPClient.Result(secretLink, null, null, null, xhttpLinks = listOf(secretLink))
        assertTrue(PxlXhttpSidecar.replaceConfig(config, "{\"legacy\":true}", response))
        val snapshot = requireNotNull(PxlXhttpSidecar.read(config))
        assertNull(snapshot.smartRevisions)
        assertEquals(listOf(secretLink), snapshot.links)
        assertNull(PxlXhttpSidecar.read(config, revisions))

        val tag = PxlXhttpConfig.outbound(secretLink).getValue("tag").jsonPrimitive.content
        assertTrue(PxlXhttpSidecar.select(config, tag))
        assertEquals(tag, PxlXhttpSidecar.read(config)?.preferredTag)
        config.writeText("{\"legacy\":false}")
        assertNull(PxlXhttpSidecar.read(config))
    }

    @Test
    fun restoresLinksOnlyForTheExactPersistedConfigAndSmartSnapshot() = withConfig("{\"outbounds\":[]}") { config ->
        assertTrue(PxlXhttpSidecar.write(config, listOf(secretLink), revisions))

        val snapshot = requireNotNull(PxlXhttpSidecar.read(config))
        assertEquals(listOf(secretLink), snapshot.links)
        assertEquals(revisions, snapshot.smartRevisions)
        assertFalse(snapshot.toString().contains(secretLink))
        assertNull(PxlXhttpSidecar.read(config, revisions.copy(policy = "policy-2")))

        config.writeText("{ \"outbounds\": [] }")
        assertNull(PxlXhttpSidecar.read(config))
    }

    @Test
    fun failsClosedForCorruptOrMissingConfig() = withConfig("{\"outbounds\":[]}") { config ->
        assertTrue(PxlXhttpSidecar.write(config, listOf(secretLink), revisions))
        PxlXhttpSidecar.sidecarFile(config).writeText("not json")
        assertNull(PxlXhttpSidecar.read(config))

        assertTrue(PxlXhttpSidecar.write(config, listOf(secretLink), revisions))
        assertTrue(config.delete())
        assertNull(PxlXhttpSidecar.read(config))
    }

    @Test
    fun rejectsIncompleteSmartSnapshotWithoutWritingCredentials() = withConfig("{}") { config ->
        val incomplete = revisions.copy(policy = "")
        assertFalse(PxlXhttpSidecar.write(config, listOf(secretLink), incomplete))
        assertFalse(PxlXhttpSidecar.sidecarFile(config).exists())
    }

    @Test
    fun invalidatesOldLinksEvenWhenConfigBytesHaveNotChanged() = withConfig("{}") { config ->
        assertTrue(PxlXhttpSidecar.write(config, listOf(secretLink), revisions))
        val sidecar = PxlXhttpSidecar.sidecarFile(config)
        val staleBackup = File(sidecar.parentFile, sidecar.name + ".bak")
        assertTrue(sidecar.copyTo(staleBackup).isFile)
        assertTrue(PxlXhttpSidecar.invalidate(config))
        // A leftover backup from a crashed write must not resurrect old credentials.
        if (!staleBackup.exists()) assertTrue(sidecar.copyTo(staleBackup).isFile)
        assertNull(PxlXhttpSidecar.read(config))
        assertTrue(PxlXhttpSidecar.write(config, emptyList(), revisions.copy(config = "config-2")))
        assertEquals(emptyList<String>(), PxlXhttpSidecar.read(config)?.links)
    }

    @Test
    fun neverRecoversCredentialsFromBackupWhenInvalidationWasInterrupted() = withConfig("{}") { config ->
        assertTrue(PxlXhttpSidecar.write(config, listOf(secretLink), revisions))
        val sidecar = PxlXhttpSidecar.sidecarFile(config)
        val backup = File(sidecar.parentFile, sidecar.name + ".bak")
        assertTrue(sidecar.renameTo(backup))
        assertNull(PxlXhttpSidecar.read(config))
        assertTrue(PxlXhttpSidecar.invalidate(config))
        assertNull(PxlXhttpSidecar.read(config))
    }

    @Test
    fun removesAllArtifactsBeforeAProfilePathCanBeReused() = withConfig("{}") { config ->
        assertTrue(PxlXhttpSidecar.write(config, listOf(secretLink), revisions))
        val sidecar = PxlXhttpSidecar.sidecarFile(config)
        val backup = File(sidecar.parentFile, sidecar.name + ".bak")
        val temporary = File(sidecar.parentFile, sidecar.name + ".new")
        assertTrue(sidecar.copyTo(backup).isFile)
        temporary.writeText("staged")

        assertTrue(PxlXhttpSidecar.remove(config))
        assertFalse(sidecar.exists())
        assertFalse(backup.exists())
        assertFalse(temporary.exists())

        // The next profile using this same file path cannot inherit old credentials.
        config.writeText("{}")
        assertNull(PxlXhttpSidecar.read(config))
    }

    @Test
    fun configReplacementInvalidatesCredentialsEvenForTheSameConfigBytes() = withConfig("{}") { config ->
        assertTrue(PxlXhttpSidecar.write(config, listOf(secretLink), revisions))

        assertTrue(PxlXhttpSidecar.replaceConfig(config, "{}"))
        assertEquals("{}", config.readText())
        assertNull(PxlXhttpSidecar.read(config))
    }

    @Test
    fun editorStyleReplacementDoesNotResurrectCredentialsAfterRestoringOriginalBytes() = withConfig("original") { config ->
        assertTrue(PxlXhttpSidecar.write(config, listOf(secretLink), revisions))
        assertTrue(PxlXhttpSidecar.replaceConfig(config, "edited"))
        assertTrue(PxlXhttpSidecar.replaceConfig(config, "original"))
        assertEquals("original", config.readText())
        assertNull(PxlXhttpSidecar.read(config))
    }

    @Test
    fun restoresInterruptedConfigStageBeforeReplacingIt() = withConfig("old-config") { config ->
        val backup = File(config.parentFile, config.name + ".pxlnet-config.bak")
        assertTrue(config.renameTo(backup))

        assertTrue(PxlXhttpSidecar.replaceConfig(config, "new-config"))
        assertEquals("new-config", config.readText())
        assertFalse(backup.exists())
    }

    private fun withConfig(content: String, block: (File) -> Unit) {
        val directory = Files.createTempDirectory("pxlnet-xhttp-sidecar").toFile()
        try {
            val config = File(directory, "profile.json").apply { writeText(content) }
            block(config)
        } finally {
            directory.deleteRecursively()
        }
    }
}
