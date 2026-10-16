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

    @Test
    fun xhttpOnlySubscriptionHasFailClosedBaseWithoutFakeAuto() {
        val link =
            "vless://11111111-1111-1111-1111-111111111111@example.invalid:443?type=xhttp&security=tls&sni=example.invalid&path=%2Ftest#XHTTP"

        val result = PxlSubscriptionConverter.convert(link)
        assertEquals(1, result.serverCount)
        assertEquals(1, result.skippedXhttpCount)
        assertFalse(result.config.contains("example.invalid"))
        assertFalse(result.config.contains("\"tag\": \"AUTO\""))
        assertTrue(result.config.contains("\"type\": \"block\""))
        assertEquals(emptyList<String>(), PxlSubscriptionConverter.serverSelection(result.config).tags)
    }

    @Test
    fun keepsCompatibleNodesWhenSubscriptionAlsoContainsXhttp() {
        val links = listOf(
            "vless://11111111-1111-1111-1111-111111111111@de.example.com:443?type=xhttp&security=tls#Germany",
            "hysteria2://secret@fi.example.com:8443?insecure=1&sni=fi.example.com#Finland",
        ).joinToString("\n")

        val result = PxlSubscriptionConverter.convert(links)

        assertEquals(1, result.serverCount)
        assertEquals(1, result.skippedXhttpCount)
        assertFalse(result.config.contains("de.example.com"))
        assertTrue(result.config.contains("fi.example.com"))
    }

    @Test
    fun smartRawPreviewCountsOmittedXhttpWithoutExposingLinks() {
        val raw = listOf(
            "vless://11111111-1111-1111-1111-111111111111@de.example.com:443?type=xhttp#Germany",
            "hysteria2://secret@fi.example.com:443?insecure=1#Finland",
        ).joinToString("\n")
        assertEquals(1, PxlSubscriptionConverter.countXhttpLinks(raw))
        assertEquals(0, PxlSubscriptionConverter.countXhttpLinks("hysteria2://secret@fi.example.com:443#Finland"))
    }

    @Test
    fun rejectsInactiveSmartSubscriptionBeforeConvertingContent() {
        val link = "hysteria2://secret@fi.example.com:8443?insecure=1#Finland"
        val expected = mapOf(
            "expired" to "expired",
            "paused" to "paused",
            "blocked" to "blocked",
            "revoked" to "revoked",
        )

        expected.forEach { (status, detail) ->
            val failure = runCatching {
                PxlSubscriptionConverter.convert(link, subscriptionStatus = status)
            }.exceptionOrNull()
            assertTrue("status=$status", failure is IllegalArgumentException)
            assertTrue("status=$status", failure?.message.orEmpty().contains(detail))
        }
        assertEquals(1, PxlSubscriptionConverter.convert(link, subscriptionStatus = "active").serverCount)
        assertEquals(1, PxlSubscriptionConverter.convert(link).serverCount)
    }

    @Test
    fun wrapsSmartSingboxGroupsWithoutChangingLegacyAuto() {
        val smart = """{
            "outbounds": [
                {"type":"hysteria2","tag":"01-hysteria2","server":"fi.example.com","server_port":443,"password":"secret","tls":{"enabled":true,"server_name":"fi.example.com"}},
                {"type":"vless","tag":"02-vless","server":"de.example.com","server_port":443,"uuid":"11111111-1111-1111-1111-111111111111","tls":{"enabled":true,"server_name":"de.example.com"}},
                {"type":"selector","tag":"Stable","outbounds":["01-hysteria2"],"default":"01-hysteria2"},
                {"type":"urltest","tag":"Fast","outbounds":["01-hysteria2","02-vless"],"url":"https://www.gstatic.com/generate_204"},
                {"type":"selector","tag":"Anti-Block","outbounds":["02-vless"],"default":"02-vless"},
                {"type":"selector","tag":"AUTO","outbounds":["Stable","Fast","Anti-Block","01-hysteria2"],"default":"Stable"}
            ],
            "route":{"final":"AUTO"}
        }""".trimIndent()

        val result = PxlSubscriptionConverter.convert(
            smart,
            subscriptionStatus = "active",
            subscriptionFormat = "singbox",
            omittedXhttpCount = 2,
        )
        val config = Json.parseToJsonElement(result.config).jsonObject
        val selection = PxlSubscriptionConverter.serverSelection(result.config)

        assertEquals(2, result.serverCount)
        assertEquals(2, result.skippedXhttpCount)
        assertEquals("PXLNET", config.getValue("route").jsonObject.getValue("final").jsonPrimitive.content)
        assertEquals("tun", config.getValue("inbounds").jsonArray[0].jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals(listOf("AUTO", "Fast", "Stable", "Anti-Block", "01-hysteria2", "02-vless"), selection.tags)
        assertEquals("AUTO", selection.selectedTag)
        assertEquals("01-hysteria2", selection.singleChoiceTargets["Stable"])
        assertEquals("02-vless", selection.singleChoiceTargets["Anti-Block"])
        assertTrue(result.config.contains("hijack-dns"))
        assertTrue(result.config.contains("fi.example.com"))
    }

    @Test
    fun rejectsInactiveOrInformationalSmartSingboxProfile() {
        val informational = """{
            "outbounds":[
                {"type":"vless","tag":"PXLNET informational","server":"192.0.2.1","server_port":9,"uuid":"00000000-0000-0000-0000-000000000000"},
                {"type":"block","tag":"REJECT"}
            ],
            "route":{"final":"REJECT"}
        }""".trimIndent()

        assertTrue(runCatching {
            PxlSubscriptionConverter.convert(informational, subscriptionStatus = "active", subscriptionFormat = "singbox")
        }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching {
            PxlSubscriptionConverter.convert(informational, subscriptionStatus = "expired", subscriptionFormat = "singbox")
        }.exceptionOrNull()?.message.orEmpty().contains("expired"))
    }

    @Test
    fun keepsManualChoiceOnlyWhenNewSubscriptionStillContainsIt() {
        val old = PxlSubscriptionConverter.convert(
            listOf(
                "hysteria2://secret@fi.example.com:8443?insecure=1#Finland",
                "hysteria2://secret@de.example.com:8443?insecure=1#Germany",
            ).joinToString("\n"),
        ).config
        val germany = PxlSubscriptionConverter.serverSelection(old).tags.last()
        val selected = PxlSubscriptionConverter.selectServer(old, germany)
        val refreshed = PxlSubscriptionConverter.convert(
            listOf(
                "hysteria2://secret@fi.example.com:8443?insecure=1#Finland",
                "hysteria2://secret@de.example.com:8443?insecure=1#Germany",
            ).joinToString("\n"),
        ).config
        val withoutGermany = PxlSubscriptionConverter.convert(
            "hysteria2://secret@fi.example.com:8443?insecure=1#Finland",
        ).config

        assertEquals(germany, PxlSubscriptionConverter.serverSelection(
            PxlSubscriptionConverter.preserveSelectedServer(refreshed, selected),
        ).selectedTag)
        assertEquals("AUTO", PxlSubscriptionConverter.serverSelection(
            PxlSubscriptionConverter.preserveSelectedServer(withoutGermany, selected),
        ).selectedTag)
    }

    @Test
    fun smartRefreshTracksManualEndpointInsteadOfReusedPositionalTag() {
        fun smart(first: String, second: String) = """{
            "outbounds":[
                {"type":"selector","tag":"PXLNET","outbounds":["AUTO","01-hysteria2","02-hysteria2"],"default":"AUTO"},
                {"type":"selector","tag":"Stable","outbounds":["01-hysteria2"],"default":"01-hysteria2"},
                {"type":"selector","tag":"AUTO","outbounds":["Stable"],"default":"Stable"},
                {"type":"hysteria2","tag":"01-hysteria2","server":"$first","server_port":443,"password":"secret"},
                {"type":"hysteria2","tag":"02-hysteria2","server":"$second","server_port":443,"password":"secret"}
            ]
        }""".trimIndent()
        val old = PxlSubscriptionConverter.selectServer(smart("de.example.com", "fi.example.com"), "01-hysteria2")
        val reordered = smart("fi.example.com", "de.example.com")
        val changed = smart("new.example.com", "fi.example.com")

        assertEquals("02-hysteria2", PxlSubscriptionConverter.serverSelection(
            PxlSubscriptionConverter.preserveSelectedServer(reordered, old),
        ).selectedTag)
        assertEquals("AUTO", PxlSubscriptionConverter.serverSelection(
            PxlSubscriptionConverter.preserveSelectedServer(changed, old),
        ).selectedTag)
    }

    @Test
    fun smartProfileCannotBeSilentlyReplacedByLegacyRawRefresh() {
        val old = """{"outbounds":[{"type":"selector","tag":"PXLNET","outbounds":["AUTO"]},{"type":"selector","tag":"Stable","outbounds":["server"]},{"type":"selector","tag":"AUTO","outbounds":["Stable"]},{"type":"hysteria2","tag":"server","server":"fi.example.com","server_port":443,"password":"secret"}]}"""
        val raw = PxlSubscriptionConverter.convert("hysteria2://secret@de.example.com:443?insecure=1#Germany").config

        assertTrue(runCatching { PxlSubscriptionConverter.preserveSelectedServer(raw, old) }
            .exceptionOrNull() is IllegalArgumentException)
    }
}
