package io.nekohasekai.sfa.utils

import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PxlXhttpRuntimePreparerTest {
    private val revisions = HTTPClient.SmartRevisions("config-1", "health-1", "policy-1", "compatibility-1")
    private val link = "vless://00000000-0000-0000-0000-000000000001@vpn.example:443?type=xhttp&security=tls#private"
    private val smart = """{"outbounds":[{"type":"selector","tag":"PXLNET","outbounds":["AUTO"]},{"type":"selector","tag":"AUTO","outbounds":["DIRECT"]},{"type":"direct","tag":"DIRECT"}],"route":{"final":"PXLNET"}}"""

    @Test
    fun preparesOnlyMatchingSmartConfigWithFreshLocalCredentials() = withConfig(smart) { config ->
        assertTrue(PxlXhttpSidecar.write(config, listOf(link), revisions))
        val plan = requireNotNull(PxlXhttpRuntimePreparer.prepare(config, 19001))
        val tag = plan.nodeTags.single()
        val xray = Json.parseToJsonElement(plan.xrayJson).jsonObject
        val account = xray.getValue("inbounds").jsonArray.single().jsonObject
            .getValue("settings").jsonObject.getValue("accounts").jsonArray.single().jsonObject
        val password = account.getValue("pass").jsonPrimitive.content

        assertEquals(smart, plan.baseSingboxJson)
        assertEquals(revisions, plan.smartRevisions)
        assertEquals(tag, account.getValue("user").jsonPrimitive.content)
        assertEquals(64, password.length)
        assertTrue(plan.augmentedSingboxJson.contains(tag))
        assertFalse(plan.toString().contains(password))
        assertFalse(plan.toString().contains(link))
    }

    @Test
    fun refusesStaleOrNonSmartProfileWithoutPublishingProxy() = withConfig(smart) { config ->
        assertTrue(PxlXhttpSidecar.write(config, listOf(link), revisions))
        config.writeText(smart + " ")
        assertNull(PxlXhttpRuntimePreparer.prepare(config, 19001))

        config.writeText("""{"outbounds":[],"route":{"final":"DIRECT"}}""")
        assertTrue(PxlXhttpSidecar.write(config, listOf(link), revisions))
        assertNull(PxlXhttpRuntimePreparer.prepare(config, 19001))
        assertNull(PxlXhttpRuntimePreparer.prepare(config, 0))
    }

    @Test
    fun manualXhttpChoiceBecomesTheRuntimeSelectorDefaultOnly() = withConfig(smart) { config ->
        assertTrue(PxlXhttpSidecar.write(config, listOf(link), revisions))
        val tag = PxlXhttpConfig.outbound(link).getValue("tag").jsonPrimitive.content
        assertTrue(PxlXhttpSidecar.select(config, tag))
        assertEquals(tag, PxlXhttpSidecar.read(config)?.preferredTag)

        val plan = requireNotNull(PxlXhttpRuntimePreparer.prepare(config, 19001))
        assertEquals("AUTO", PxlSubscriptionConverter.serverSelection(plan.baseSingboxJson).selectedTag)
        assertTrue(PxlSubscriptionConverter.serverSelection(plan.augmentedSingboxJson).tags.contains(tag))
        assertEquals(tag, PxlSubscriptionConverter.serverSelection(
            PxlSubscriptionConverter.selectServer(plan.augmentedSingboxJson, tag),
        ).selectedTag)
        assertEquals(tag, PxlSubscriptionConverter.serverSelection(plan.augmentedSingboxJson).selectedTag)
        assertEquals(smart, config.readText())
    }

    @Test
    fun preparesLegacyManualChoiceWithoutChangingLegacyAuto() = withConfig(
        """{"outbounds":[{"type":"selector","tag":"PXLNET","outbounds":["AUTO","Germany"],"default":"AUTO"},{"type":"urltest","tag":"AUTO","outbounds":["Germany"]},{"type":"hysteria2","tag":"Germany"}],"route":{"final":"PXLNET"}}""",
    ) { config ->
        assertTrue(PxlXhttpSidecar.write(config, listOf(link), null))
        val tag = PxlXhttpConfig.outbound(link).getValue("tag").jsonPrimitive.content
        assertTrue(PxlXhttpSidecar.select(config, tag))
        val plan = requireNotNull(PxlXhttpRuntimePreparer.prepare(config, 19001))
        assertNull(plan.smartRevisions)
        assertEquals(tag, PxlSubscriptionConverter.serverSelection(plan.augmentedSingboxJson).selectedTag)
        val outbounds = Json.parseToJsonElement(plan.augmentedSingboxJson).jsonObject.getValue("outbounds").jsonArray
        val auto = outbounds.map { it.jsonObject }.single { it.getValue("tag").jsonPrimitive.content == "AUTO" }
        assertEquals(listOf("Germany"), auto.getValue("outbounds").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun xhttpOnlyLegacyProfileSelectsItsOnlyUsableServerAndNeverInventsAuto() {
        val base = PxlSubscriptionConverter.convert(link).config
        withConfig(base) { config ->
            val response = HTTPClient.Result(link, null, null, null, xhttpLinks = listOf(link))
            assertTrue(PxlXhttpSidecar.replaceConfig(config, base, response))
            val tag = PxlXhttpConfig.outbound(link).getValue("tag").jsonPrimitive.content
            assertEquals(tag, PxlXhttpSidecar.read(config)?.preferredTag)
            assertEquals(emptyList<String>(), PxlSubscriptionConverter.serverSelection(base).tags)
            val plan = requireNotNull(PxlXhttpRuntimePreparer.prepare(config, 19001))
            val selection = PxlSubscriptionConverter.serverSelection(plan.augmentedSingboxJson)
            assertEquals(tag, selection.selectedTag)
            assertEquals(false, "AUTO" in selection.tags)
        }
    }

    private inline fun withConfig(content: String, block: (File) -> Unit) {
        val directory = Files.createTempDirectory("pxl-xhttp-prep-").toFile()
        try {
            val config = File(directory, "profile.json")
            config.writeText(content)
            block(config)
        } finally {
            directory.deleteRecursively()
        }
    }
}
