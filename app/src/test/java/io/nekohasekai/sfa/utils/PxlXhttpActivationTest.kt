package io.nekohasekai.sfa.utils

import io.nekohasekai.sfa.xray.PxlXraySession
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PxlXhttpActivationTest {
    private val link = "vless://00000000-0000-0000-0000-000000000001@vpn.example:443?type=xhttp&security=tls"
    private val base = """{"outbounds":[{"type":"selector","tag":"PXLNET","outbounds":["AUTO"]},{"type":"selector","tag":"AUTO","outbounds":["DIRECT"]},{"type":"direct","tag":"DIRECT"}],"route":{"final":"PXLNET"}}"""
    private val revisions = HTTPClient.SmartRevisions("c", "h", "p", "x")

    @Test
    fun missingSidecarDoesNotStartOrReload() = withConfig(false) { file -> runBlocking {
        val session = FakeSession()
        val loads = mutableListOf<String>()
        assertEquals(PxlXhttpActivation.Result.Unavailable,
            PxlXhttpActivation(session, loads::add, { 19001 }, { true }).activate(file))
        assertEquals(0, session.starts)
        assertEquals(emptyList<String>(), loads)
    } }

    @Test
    fun bindFailureRetriesWithNewPortAndKeepsBase() = withConfig(true) { file -> runBlocking {
        val session = FakeSession(startSucceeds = false)
        val loads = mutableListOf<String>()
        var port = 19000
        assertEquals(PxlXhttpActivation.Result.BaseOnly,
            PxlXhttpActivation(session, loads::add, { ++port }, { true }).activate(file))
        assertEquals(3, session.starts)
        assertEquals(19003, port)
        assertEquals(emptyList<String>(), loads)
        assertFalse(session.isRunning())
    } }

    @Test
    fun augmentedReloadFailureRestoresBaseAndStopsSidecar() = withConfig(true) { file -> runBlocking {
        val events = mutableListOf<String>()
        val session = FakeSession(events = events)
        val loads = mutableListOf<String>()
        val activation = PxlXhttpActivation(session, { json ->
            loads += json
            events += if (loads.size == 1) "reload-augmented" else "reload-base"
            if (loads.size == 1) error("reload failed")
        }, { 19001 }, { true })
        assertEquals(PxlXhttpActivation.Result.BaseOnly, activation.activate(file))
        assertEquals(base, loads.last())
        assertFalse(session.isRunning())
        assertTrue(events.indexOf("reload-base") < events.lastIndexOf("stop"))
    } }

    @Test
    fun failedRollbackIsFatal() = withConfig(true) { file -> runBlocking {
        val session = FakeSession()
        val activation = PxlXhttpActivation(session, { error("reload failed") }, { 19001 }, { true })
        assertEquals(PxlXhttpActivation.Result.Fatal, activation.activate(file))
        assertFalse(session.isRunning())
    } }

    @Test
    fun sidecarDeathDuringAugmentedReloadRestoresBase() = withConfig(true) { file -> runBlocking {
        val session = FakeSession()
        val loads = mutableListOf<String>()
        val activation = PxlXhttpActivation(session, { json ->
            loads += json
            if (loads.size == 1) session.invalidate()
        }, { 19001 }, { true })
        assertEquals(PxlXhttpActivation.Result.BaseOnly, activation.activate(file))
        assertEquals(base, loads.last())
        assertFalse(session.isRunning())
    } }

    @Test
    fun healthySidecarPublishesAugmentedProfile() = withConfig(true) { file -> runBlocking {
        val session = FakeSession()
        val loads = mutableListOf<String>()
        assertEquals(PxlXhttpActivation.Result.Activated,
            PxlXhttpActivation(session, loads::add, { 19001 }, { true }).activate(file))
        assertEquals(1, loads.size)
        assertEquals(1, session.starts)
        assertEquals(true, session.isRunning())
    } }

    @Test
    fun unreadyListenerNeverPublishesAugmentedProfile() = withConfig(true) { file -> runBlocking {
        val session = FakeSession()
        val loads = mutableListOf<String>()
        assertEquals(PxlXhttpActivation.Result.BaseOnly,
            PxlXhttpActivation(session, loads::add, { 19001 }, { false }).activate(file))
        assertEquals(emptyList<String>(), loads)
        assertFalse(session.isRunning())
    } }

    @Test
    fun cancelledBeforeReloadStopsSidecarWithoutPublishing() = withConfig(true) { file -> runBlocking {
        val session = FakeSession()
        val loads = mutableListOf<String>()
        var checks = 0
        val activation = PxlXhttpActivation(session, loads::add, { 19001 }, { true }) { ++checks < 3 }

        assertEquals(PxlXhttpActivation.Result.BaseOnly, activation.activate(file))
        assertEquals(emptyList<String>(), loads)
        assertFalse(session.isRunning())
    } }

    @Test
    fun cancelledDuringAugmentedReloadLeavesListenerUntilOwnerClosesSingbox() = withConfig(true) { file -> runBlocking {
        val events = mutableListOf<String>()
        val session = FakeSession(events = events)
        var current = true
        val activation = PxlXhttpActivation(session, {
            events += "reload-augmented"
            current = false
        }, { 19001 }, { true }) { current }

        assertEquals(PxlXhttpActivation.Result.Fatal, activation.activate(file))
        assertTrue(session.isRunning())
        events += "close-singbox"
        session.invalidate()
        session.stop()
        assertTrue(events.indexOf("close-singbox") < events.indexOf("stop"))
    } }

    private inline fun withConfig(sidecar: Boolean, block: (File) -> Unit) {
        val directory = Files.createTempDirectory("pxl-xhttp-activation-").toFile()
        try {
            val file = File(directory, "profile.json")
            file.writeText(base)
            if (sidecar) check(PxlXhttpSidecar.write(file, listOf(link), revisions))
            block(file)
        } finally {
            directory.deleteRecursively()
        }
    }

    private class FakeSession(
        private val startSucceeds: Boolean = true,
        private val events: MutableList<String>? = null,
    ) : PxlXraySession {
        var starts = 0
        private var running = false
        override suspend fun start(configJson: String): Boolean {
            starts++
            running = startSucceeds
            return running
        }
        override fun invalidate() { running = false }
        override suspend fun stop() {
            events?.add("stop")
            running = false
        }
        override fun isRunning() = running
        override fun close() { running = false }
    }
}
