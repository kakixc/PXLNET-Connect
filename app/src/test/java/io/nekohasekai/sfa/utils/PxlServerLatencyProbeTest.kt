package io.nekohasekai.sfa.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PxlServerLatencyProbeTest {
    @Test
    fun returnsMiddleOfThreeSuccessfulSamples() {
        assertEquals(82, PxlServerLatencyProbe.median(listOf(700, 82, 71)))
    }

    @Test
    fun neverTurnsMissingOrFailedSampleIntoPing() {
        assertNull(PxlServerLatencyProbe.median(listOf(40, 50)))
        assertNull(PxlServerLatencyProbe.median(listOf(40, 0, 50)))
    }

    @Test
    fun unknownPreferenceFallsBackToExistingWebsiteTest() {
        assertEquals(PxlLatencySource.WEBSITE_THROUGH_VPN, PxlLatencySource.fromPersisted(null))
        assertEquals(PxlLatencySource.WEBSITE_THROUGH_VPN, PxlLatencySource.fromPersisted("unknown"))
    }

    @Test
    fun preconnectResolvesOnlySelectedServerAndKeepsTlsIdentity() {
        val config = """{"outbounds":[{"tag":"first","type":"hysteria2","server":"first.example","tls":{"enabled":true}},
            {"tag":"second","type":"vless","server":"second.example","tls":{"enabled":true,"server_name":"custom.example"}}]}"""
        val result = PxlServerLatencyProbe.resolveServerForPreconnect(config, "first") { "192.0.2.1" }
        val outbounds = Json.parseToJsonElement(result).jsonObject["outbounds"]!!.jsonArray
        assertEquals("192.0.2.1", outbounds[0].jsonObject["server"]!!.jsonPrimitive.content)
        assertEquals("first.example", outbounds[0].jsonObject["tls"]!!.jsonObject["server_name"]!!.jsonPrimitive.content)
        assertEquals("second.example", outbounds[1].jsonObject["server"]!!.jsonPrimitive.content)
        assertEquals("custom.example", outbounds[1].jsonObject["tls"]!!.jsonObject["server_name"]!!.jsonPrimitive.content)
    }

    @Test
    fun preconnectLeavesConfigUntouchedWhenResolutionIsUnavailable() {
        val config = """{"outbounds":[{"tag":"first","type":"hysteria2","server":"first.example"}]}"""
        assertEquals(config, PxlServerLatencyProbe.resolveServerForPreconnect(config, "first") { null })
    }
}
