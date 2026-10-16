package io.nekohasekai.sfa.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PxlDiagnosticRedactorTest {
    @Test
    fun keepsKnownLogTimestampsButStillRedactsIpv6Addresses() {
        val source = "2026-10-07 20:09:11 preconnect: dns\n10-07 20:17:23.456 E Connect: failed at 2001:0db8:0000:0000:0000:0000:0000:0001"

        val redacted = PxlDiagnosticRedactor.redact(source)

        assertTrue(redacted.contains("2026-10-07 20:09:11"))
        assertTrue(redacted.contains("10-07 20:17:23.456"))
        assertTrue(redacted.contains("[IPV6]"))
        assertFalse(redacted.contains("2001:0db8"))
    }

    @Test
    fun removesCredentialsSubscriptionAndServerAddresses() {
        val source = """
            vless://11111111-1111-4111-8111-111111111111@vpn.example.com:443?security=reality
            update https://pxlnet.example.org:2096/sub/b24558ecc8252d77abcd1234
            authorization: Bearer abcdefghijklmnopqrstuvwxyz123456
            connect 192.0.2.44:443 through edge.example.net
        """.trimIndent()

        val redacted = PxlDiagnosticRedactor.redact(source)

        assertFalse(redacted.contains("11111111-1111-4111-8111-111111111111"))
        assertFalse(redacted.contains("b24558ecc8252d77abcd1234"))
        assertFalse(redacted.contains("abcdefghijklmnopqrstuvwxyz123456"))
        assertFalse(redacted.contains("192.0.2.44"))
        assertFalse(redacted.contains("edge.example.net"))
        assertTrue(redacted.contains("[REDACTED]"))
        assertTrue(redacted.contains("[IP]"))
        assertTrue(redacted.contains("[HOST]"))
    }
}
