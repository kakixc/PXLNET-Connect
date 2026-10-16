package io.nekohasekai.sfa.utils

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PxlXhttpConfigTest {
    private val uuid = "00000000-0000-0000-0000-000000000001"

    @Test
    fun mapsRealityXhttpWithoutUsingPositionalTagOrExposingSecretInTag() {
        val link = "vless://$uuid@vpn.example:443?type=xhttp&security=reality&pbk=public-key&sid=ab&" +
            "sni=front.example&fp=chrome&path=%2Fvpn&host=cdn.example&mode=packet-up#Finland"
        val outbound = PxlXhttpConfig.outbound(link)
        val stream = outbound.getValue("streamSettings").jsonObject
        val next = outbound.getValue("settings").jsonObject.getValue("vnext").jsonArray.single().jsonObject

        assertEquals("vless", outbound.getValue("protocol").jsonPrimitive.content)
        assertEquals("vpn.example", next.getValue("address").jsonPrimitive.content)
        assertEquals("xhttp", stream.getValue("network").jsonPrimitive.content)
        assertEquals("/vpn", stream.getValue("xhttpSettings").jsonObject.getValue("path").jsonPrimitive.content)
        assertEquals("packet-up", stream.getValue("xhttpSettings").jsonObject.getValue("mode").jsonPrimitive.content)
        assertEquals("public-key", stream.getValue("realitySettings").jsonObject.getValue("password").jsonPrimitive.content)
        val tag = outbound.getValue("tag").jsonPrimitive.content
        assertEquals(tag, PxlXhttpConfig.outbound(link.replace("#Finland", "#Germany")).getValue("tag").jsonPrimitive.content)
        val reordered = link.replace("type=xhttp&security=reality", "security=reality&type=xhttp")
        assertEquals(tag, PxlXhttpConfig.outbound(reordered).getValue("tag").jsonPrimitive.content)
        assertNotEquals("01-vless", tag)
        assertEquals(false, tag.contains("public-key"))
    }

    @Test
    fun rejectsUnsupportedOrIncompleteLinks() {
        assertThrows(IllegalArgumentException::class.java) {
            PxlXhttpConfig.outbound("vless://$uuid@vpn.example:443?type=ws&security=tls")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PxlXhttpConfig.outbound("vless://$uuid@vpn.example:443?type=xhttp&security=reality")
        }
        val malformed = assertThrows(IllegalArgumentException::class.java) {
            PxlXhttpConfig.outbound("vless://private-credential@vpn.example:443?type=xhttp&security=tls")
        }
        assertEquals(false, malformed.message.orEmpty().contains("private-credential"))
    }

    @Test
    fun mapsTlsXhttpWithoutRealitySettings() {
        val outbound = PxlXhttpConfig.outbound(
            "vless://$uuid@vpn.example:443?type=xhttp&security=tls&sni=front.example&path=%2Fconnect",
        )
        val stream = outbound.getValue("streamSettings").jsonObject
        assertEquals("tls", stream.getValue("security").jsonPrimitive.content)
        assertEquals("front.example", stream.getValue("tlsSettings").jsonObject.getValue("serverName").jsonPrimitive.content)
        assertEquals(false, "realitySettings" in stream)
    }

    @Test
    fun buildsOneAuthenticatedLoopbackWithFailClosedRoutesForTwoNodes() {
        val de = "vless://$uuid@de.example:443?type=xhttp&security=tls&sni=de.example"
        val fi = "vless://$uuid@fi.example:443?type=xhttp&security=tls&sni=fi.example"
        val deTag = PxlXhttpConfig.outbound(de).getValue("tag").jsonPrimitive.content
        val fiTag = PxlXhttpConfig.outbound(fi).getValue("tag").jsonPrimitive.content
        val runtime = PxlXhttpConfig.runtime(listOf(de, fi), 19100, mapOf(deTag to "test-de", fiTag to "test-fi"))
        val inbound = runtime.xray.getValue("inbounds").jsonArray.single().jsonObject
        val routes = runtime.xray.getValue("routing").jsonObject.getValue("rules").jsonArray
        val accounts = inbound.getValue("settings").jsonObject.getValue("accounts").jsonArray

        assertEquals("127.0.0.1", inbound.getValue("listen").jsonPrimitive.content)
        assertEquals("password", inbound.getValue("settings").jsonObject.getValue("auth").jsonPrimitive.content)
        assertEquals(2, accounts.size)
        assertEquals(listOf(deTag, fiTag), runtime.singboxOutbounds.map { it.getValue("username").jsonPrimitive.content })
        assertEquals(listOf(deTag, fiTag), routes.dropLast(1).map {
            it.jsonObject.getValue("outboundTag").jsonPrimitive.content
        })
        assertEquals("pxlnet-xhttp-reject", routes.last().jsonObject.getValue("outboundTag").jsonPrimitive.content)
        assertTrue(runtime.singboxOutbounds.all { it.getValue("server_port").jsonPrimitive.content == "19100" })
        assertEquals(false, runtime.toString().contains("test-de"))
    }

    @Test
    fun refusesMissingLocalAuthentication() {
        val link = "vless://$uuid@vpn.example:443?type=xhttp&security=tls"
        assertThrows(IllegalArgumentException::class.java) { PxlXhttpConfig.runtime(listOf(link), 19100, emptyMap()) }
    }

    @Test
    fun addsEphemeralXhttpToSmartManualAndAutoWithoutChangingOtherGroups() {
        val link = "vless://$uuid@vpn.example:443?type=xhttp&security=tls"
        val tag = PxlXhttpConfig.outbound(link).getValue("tag").jsonPrimitive.content
        val runtime = PxlXhttpConfig.runtime(listOf(link), 19100, mapOf(tag to "local-only-password"))
        val original = """{"outbounds":[{"type":"selector","tag":"PXLNET","outbounds":["AUTO","01-hysteria2"]},{"type":"selector","tag":"AUTO","outbounds":["Stable","01-hysteria2"]},{"type":"selector","tag":"Stable","outbounds":["01-hysteria2"]},{"type":"hysteria2","tag":"01-hysteria2"}],"route":{"final":"PXLNET"}}"""

        val updated = Json.parseToJsonElement(PxlXhttpConfig.augmentSmartConfig(original, runtime)).jsonObject
        val outbounds = updated.getValue("outbounds").jsonArray.map { it.jsonObject }
        fun members(name: String) = outbounds.single { it.getValue("tag").jsonPrimitive.content == name }
            .getValue("outbounds").jsonArray.map { it.jsonPrimitive.content }

        assertEquals(listOf("AUTO", "01-hysteria2", tag), members("PXLNET"))
        assertEquals(listOf("Stable", "01-hysteria2", tag), members("AUTO"))
        assertEquals(listOf("01-hysteria2"), members("Stable"))
        assertEquals("socks", outbounds.single { it.getValue("tag").jsonPrimitive.content == tag }
            .getValue("type").jsonPrimitive.content)
        assertEquals(false, original.contains(tag))
    }

    @Test
    fun legacyManualGetsXhttpButLegacyAutoKeepsExactlyTheSameMembers() {
        val link = "vless://$uuid@vpn.example:443?type=xhttp&security=tls"
        val tag = PxlXhttpConfig.outbound(link).getValue("tag").jsonPrimitive.content
        val runtime = PxlXhttpConfig.runtime(listOf(link), 19100, mapOf(tag to "local-only-password"))
        val original = """{"outbounds":[{"type":"selector","tag":"PXLNET","outbounds":["AUTO","Germany"]},{"type":"urltest","tag":"AUTO","outbounds":["Germany"]},{"type":"hysteria2","tag":"Germany"}],"route":{"final":"PXLNET"}}"""

        val updated = Json.parseToJsonElement(PxlXhttpConfig.augmentLegacyConfig(original, runtime)).jsonObject
        val outbounds = updated.getValue("outbounds").jsonArray.map { it.jsonObject }
        fun members(name: String) = outbounds.single { it.getValue("tag").jsonPrimitive.content == name }
            .getValue("outbounds").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("AUTO", "Germany", tag), members("PXLNET"))
        assertEquals(listOf("Germany"), members("AUTO"))
    }

    @Test
    fun presentsStableXhttpTagWithDecodedCountryAndProtocol() {
        val link = "vless://$uuid@vpn.example:443?type=xhttp&security=reality&pbk=test-key#%F0%9F%87%A9%F0%9F%87%AA%20Germany"
        val node = PxlXhttpConfig.presentation(link)
        assertEquals(PxlXhttpConfig.outbound(link).getValue("tag").jsonPrimitive.content, node.tag)
        assertEquals("🇩🇪 Germany", node.label)
        assertEquals("vless reality", node.type)
    }

    @Test
    fun rejectsNonSmartOrAmbiguousSelectorBeforeAddingProxy() {
        val link = "vless://$uuid@vpn.example:443?type=xhttp&security=tls"
        val tag = PxlXhttpConfig.outbound(link).getValue("tag").jsonPrimitive.content
        val runtime = PxlXhttpConfig.runtime(listOf(link), 19100, mapOf(tag to "local-only-password"))
        val legacy = """{"outbounds":[{"type":"direct","tag":"DIRECT"}],"route":{"final":"DIRECT"}}"""
        assertThrows(IllegalArgumentException::class.java) { PxlXhttpConfig.augmentSmartConfig(legacy, runtime) }
        val collision = """{"outbounds":[{"type":"selector","tag":"PXLNET","outbounds":["AUTO"]},{"type":"selector","tag":"AUTO","outbounds":["$tag"]},{"type":"direct","tag":"$tag"}],"route":{"final":"PXLNET"}}"""
        assertThrows(IllegalArgumentException::class.java) { PxlXhttpConfig.augmentSmartConfig(collision, runtime) }
    }
}
