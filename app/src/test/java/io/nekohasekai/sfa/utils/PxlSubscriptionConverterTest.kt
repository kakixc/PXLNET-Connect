package io.nekohasekai.sfa.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PxlSubscriptionConverterTest {
    @Test
    fun convertsBase64VlessAndHysteria2Subscription() {
        val links =
            listOf(
                "vless://11111111-1111-1111-1111-111111111111@de.example.com:443?type=tcp&security=reality&pbk=test-key&fp=chrome&sni=www.microsoft.com&sid=abcd&flow=xtls-rprx-vision#Germany",
                "hysteria2://secret@fi.example.com:8443?insecure=1&sni=fi.example.com&fp=chrome#Finland",
            ).joinToString("\n")
        val encoded = Base64.getEncoder().encodeToString(links.toByteArray())

        val result = PxlSubscriptionConverter.convert(encoded)
        val root = Json.parseToJsonElement(result.config).jsonObject
        val outbounds = root.getValue("outbounds").jsonArray

        assertEquals(2, result.serverCount)
        assertEquals("PXLNET", outbounds[0].jsonObject.getValue("tag").jsonPrimitive.content)
        assertTrue(result.config.contains("🇩🇪 Germany · VLESS"))
        assertTrue(result.config.contains("🇫🇮 Finland · Hysteria2"))
        assertTrue(result.config.contains("test-key"))
        assertTrue(result.config.contains("ip_is_private"))
        assertTrue(result.config.contains("xn--p1ai"))
        assertTrue(result.config.contains("hijack-dns"))
        assertFalse(result.config.contains("raw.githubusercontent.com"))
        val vless = outbounds.first { it.jsonObject["type"]?.jsonPrimitive?.content == "vless" }.jsonObject
        val hysteria2 = outbounds.first { it.jsonObject["type"]?.jsonPrimitive?.content == "hysteria2" }.jsonObject
        assertTrue("utls" in vless.getValue("tls").jsonObject)
        assertFalse("utls" in hysteria2.getValue("tls").jsonObject)
        assertEquals(
            listOf("AUTO", "🇩🇪 Germany · VLESS", "🇫🇮 Finland · Hysteria2"),
            PxlSubscriptionConverter.serverSelection(result.config).tags,
        )
    }

    @Test
    fun fullTunnelKeepsLanDirectButDoesNotBypassRussianDomains() {
        val link = "hysteria2://secret@fi.example.com:8443?insecure=1&sni=fi.example.com#Finland"

        val result = PxlSubscriptionConverter.convert(link, smartRouting = false)

        assertTrue(result.config.contains("ip_is_private"))
        assertFalse(result.config.contains("xn--p1ai"))
        assertFalse(result.config.contains("2ip.ru"))
        assertFalse(result.config.contains("raw.githubusercontent.com"))
    }

    @Test
    fun migratesRemoteRuleSetsAndChangesPreferredServer() {
        val legacy =
            """{
                "outbounds":[
                    {"type":"selector","tag":"PXLNET","outbounds":["AUTO","Germany","Finland"],"default":"AUTO"},
                    {"type":"urltest","tag":"AUTO","outbounds":["Germany","Finland"]},
                    {"type":"hysteria2","tag":"Finland","tls":{"enabled":true,"server_name":"fi.example.com","utls":{"enabled":true,"fingerprint":"chrome"}}}
                ],
                "route":{
                    "rule_set":[{"type":"remote","tag":"geoip-ru","url":"https://raw.githubusercontent.com/test.srs"}],
                    "rules":[
                        {"action":"sniff"},
                        {"rule_set":["geoip-ru"],"outbound":"DIRECT"},
                        {"ip_is_private":true,"outbound":"DIRECT"}
                    ],
                    "final":"PXLNET"
                }
            }""".trimIndent()

        val migrated = PxlSubscriptionConverter.sanitizeConfig(legacy)
        val selected = PxlSubscriptionConverter.selectServer(migrated, "Finland")

        assertFalse(migrated.contains("raw.githubusercontent.com"))
        assertFalse(migrated.contains("geoip-ru"))
        assertFalse(migrated.contains("utls"))
        assertTrue(migrated.contains("ip_is_private"))
        assertEquals("Finland", PxlSubscriptionConverter.serverSelection(selected).selectedTag)
    }

    @Test
    fun leavesNativeSingBoxJsonUntouched() {
        val original = "{\"outbounds\":[]}"
        val result = PxlSubscriptionConverter.convert(original)
        assertEquals(original, result.config)
        assertEquals(0, result.serverCount)
    }
}
