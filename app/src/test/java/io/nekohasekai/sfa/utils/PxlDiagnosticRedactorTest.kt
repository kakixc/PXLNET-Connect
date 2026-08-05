package io.nekohasekai.sfa.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PxlDiagnosticRedactorTest {
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
