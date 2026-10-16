package io.nekohasekai.sfa.utils

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PxlXhttpServerProbeTest {
    private val link = "vless://11111111-1111-4111-8111-111111111111@example.com:443" +
        "?type=xhttp&security=reality&pbk=5b5J3-FtVeyXc5vrQmI-6YTK8hoKRkRsJRD0s8YAlng" +
        "&sni=example.com&path=%2Ftest#Synthetic"

    @Test fun onlyExactXhttpTagCanBeProbed() {
        val tag = PxlXhttpConfig.outbound(link).getValue("tag").jsonPrimitive.content
        assertNotNull(PxlXhttpServerProbe.configForTag(listOf(link), tag))
        assertNull(PxlXhttpServerProbe.configForTag(listOf(link), "another-node"))
        assertNull(PxlXhttpServerProbe.configForTag(emptyList(), tag))
    }
}
