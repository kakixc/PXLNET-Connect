package io.nekohasekai.sfa.compose.screen.dashboard

import io.nekohasekai.sfa.compose.model.Group
import io.nekohasekai.sfa.compose.model.GroupItem
import io.nekohasekai.sfa.utils.PxlServerProbeCache
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerPresentationTest {
    @Test
    fun russianTagsUseVectorFlagAndNoEmojiLabel() {
        val tag = "🇩🇪 Германия · VLESS · Reality"
        assertEquals(ServerRegion.GERMANY, serverRegion(tag))
        assertEquals("Германия · VLESS · Reality", cleanServerTag(tag))
        assertEquals("VLESS · Reality", serverProtocol(tag))
    }

    @Test
    fun finlandAndAutoAreClassifiedWithoutChangingOutboundTag() {
        assertEquals(ServerRegion.FINLAND, serverRegion("🇫🇮 Финляндия · Hysteria2"))
        assertEquals(ServerRegion.AUTO, serverRegion("AUTO"))
        assertEquals("Hysteria2", serverProtocol("🇫🇮 Финляндия · Hysteria2"))
        assertEquals(ServerRegion.AUTO, serverRegion("Fast"))
        assertEquals(ServerRegion.AUTO, serverRegion("Stable"))
        assertEquals(ServerRegion.AUTO, serverRegion("Anti-Block"))
        assertEquals("", serverProtocol("Fast", "urltest"))
        assertEquals("", serverProtocol("Stable", "selector"))
    }

    @Test
    fun resolvesLiveLegacyAndNestedSmartAutoToRealNode() {
        fun item(tag: String, type: String) = GroupItem(tag, type, 0L, 0)
        val groups = listOf(
            Group("PXLNET", "selector", true, "AUTO", false, listOf(
                item("AUTO", "selector"), item("DE", "hysteria2"), item("FI", "vless"),
            )),
            Group("AUTO", "selector", true, "Stable", false, listOf(
                item("Stable", "selector"), item("Fast", "urltest"),
            )),
        )
        assertEquals("DE", selectedVpnServerNode(groups, "DE"))
        assertEquals("FI", selectedVpnServerNode(groups, "AUTO", mapOf("Stable" to "FI")))
        assertEquals("FI", selectedVpnServerNode(groups, "PXLNET", mapOf("Stable" to "FI")))
        assertEquals(null, selectedVpnServerNode(groups, "AUTO"))
    }

    @Test
    fun unresolvedOrCyclicAutoHasNoFakePing() {
        fun item(tag: String, type: String) = GroupItem(tag, type, 0L, 0)
        val groups = listOf(
            Group("PXLNET", "selector", true, "AUTO", false, listOf(item("AUTO", "urltest"), item("DE", "hysteria2"))),
            Group("AUTO", "urltest", false, "DE", false, listOf(item("DE", "hysteria2"))),
        )
        assertEquals("DE", selectedVpnServerNode(groups, "AUTO"))
        assertEquals(null, selectedVpnServerNode(groups, "missing"))
        assertEquals(null, selectedVpnServerNode(emptyList(), "AUTO", mapOf("AUTO" to "AUTO")))
    }

    @Test
    fun resolvesLiveAutoToKnownXhttpNodeEvenWhenSingboxReportsSocks() {
        fun item(tag: String, type: String) = GroupItem(tag, type, 0L, 0)
        val groups = listOf(
            Group("AUTO", "selector", true, "xhttp-node", false, listOf(item("xhttp-node", "socks"))),
        )
        assertEquals(null, selectedVpnServerNode(groups, "AUTO"))
        assertEquals("xhttp-node", selectedVpnServerNode(groups, "AUTO", extraNodeTags = setOf("xhttp-node")))
        assertEquals(null, selectedVpnServerNode(emptyList(), "xhttp-node", extraNodeTags = setOf("xhttp-node")))
    }

    @Test
    fun autoBestUsesOnlySuccessfulMeasuredManualNodes() {
        assertEquals(
            72,
            bestMeasuredServerDelay(
                listOf("vless-de", "hy2-fi", "xhttp-de"),
                mapOf("vless-de" to 190, "hy2-fi" to 72, "xhttp-de" to null, "AUTO" to 1),
            ),
        )
        assertEquals(null, bestMeasuredServerDelay(listOf("xhttp-de", "hy2-fi"), mapOf("xhttp-de" to null, "hy2-fi" to 0)))
    }

    @Test
    fun openingPickerDoesNotStartBulkMeasurement() {
        val manual = listOf("vless-de", "hy2-fi")
        val auto = listOf("vless-de")
        assertEquals(emptyList<String>(), serverProbeTargets(true, false, true, manual, auto, "vless-de"))
        assertEquals(manual, serverProbeTargets(true, true, false, manual, auto, "vless-de"))
        assertEquals(listOf("vless-de"), serverProbeTargets(false, false, true, manual, auto, "vless-de"))
        assertEquals(emptyList<String>(), serverProbeTargets(false, false, false, manual, auto, "vless-de"))
    }

    @Test
    fun reopeningPickerRestoresOnlyCurrentCachedServerMeasurements() {
        val cache = PxlServerProbeCache(ttlMillis = 120_000)
        val key = PxlServerProbeCache.Key(1, "", true, "wifi", 20)
        cache.record(key.copy(tag = "de"), 87, 1_000)
        cache.record(key.copy(tag = "fi"), null, 1_000)

        val restored = cachedPickerMeasurements(cache, key, listOf("de", "fi", "other"), 2_000)
        assertEquals(87, restored["de"]?.milliseconds)
        assertEquals(setOf("de", "fi"), restored.keys)
        assertEquals(null, restored["fi"]?.milliseconds)
        assertEquals(restored, cachedPickerMeasurements(cache, key, listOf("de", "fi"), 3_000))
        assertEquals(emptyMap<String, PxlServerProbeCache.Entry>(), cachedPickerMeasurements(cache, key.copy(networkId = "mobile"), listOf("de"), 3_000))
        assertEquals(emptyMap<String, PxlServerProbeCache.Entry>(), cachedPickerMeasurements(cache, key.copy(profileId = 2), listOf("de"), 3_000))
        assertEquals(emptyMap<String, PxlServerProbeCache.Entry>(), cachedPickerMeasurements(cache, key.copy(serviceStarted = false), listOf("de"), 3_000))
        assertEquals(emptyMap<String, PxlServerProbeCache.Entry>(), cachedPickerMeasurements(cache, key.copy(profileUpdatedAtMillis = 21), listOf("de"), 3_000))
        assertEquals(emptyMap<String, PxlServerProbeCache.Entry>(), cachedPickerMeasurements(cache, key, listOf("de"), 121_000))
        cache.clear()
        assertEquals(emptyMap<String, PxlServerProbeCache.Entry>(), cachedPickerMeasurements(cache, key, listOf("de"), 3_000))
    }

    @Test
    fun autoShowsLoadingOnlyUntilARealCandidateOrAllFailuresArrive() {
        assertEquals(true, autoBestMeasurementLoading(true, true, null, true))
        assertEquals(false, autoBestMeasurementLoading(true, true, null, false))
        assertEquals(false, autoBestMeasurementLoading(true, true, 87, true))
        assertEquals(false, autoBestMeasurementLoading(false, true, null, true))
        assertEquals(false, autoBestMeasurementLoading(true, false, null, true))
    }
}
