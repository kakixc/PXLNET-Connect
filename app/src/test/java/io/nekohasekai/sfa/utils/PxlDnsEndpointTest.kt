package io.nekohasekai.sfa.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PxlDnsEndpointTest {
    @Test fun rejectsWordsAndMalformedAddresses() {
        listOf("yh", "привет мир", "1.2.3.999", "127.0.0.01", "udp://dns", "https://dns.example:0/dns-query", "ftp://dns.example")
            .forEach { value ->
                assertThrows("Accepted $value", Exception::class.java) { PxlDnsEndpoint.parse(value) }
            }
    }

    @Test fun acceptsSupportedEndpoints() {
        assertEquals("udp", PxlDnsEndpoint.parse("dns.comss.one").type)
        assertEquals("1.1.1.1", PxlDnsEndpoint.parse("1.1.1.1").host)
        assertEquals("::1", PxlDnsEndpoint.parse("udp://[::1]").host)
        assertEquals(853, PxlDnsEndpoint.parse("tls://dns.example").port)
        assertEquals("/query", PxlDnsEndpoint.parse("https://dns.example/query").path)
    }
}
