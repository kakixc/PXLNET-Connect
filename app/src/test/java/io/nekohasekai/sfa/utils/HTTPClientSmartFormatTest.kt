package io.nekohasekai.sfa.utils

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HTTPClientSmartFormatTest {
    @Test
    fun extractsXhttpLinksFromRawAndBase64WithoutIncludingOtherServers() {
        val xhttp = "vless://test-id@vpn.example:443?type=xhttp&security=reality#Finland"
        val hysteria = "hysteria2://password@vpn.example:443#Legacy"
        val raw = listOf(xhttp, hysteria, xhttp).joinToString("\n")
        val base64 = Base64.getEncoder().encodeToString(raw.toByteArray())

        assertEquals(listOf(xhttp), PxlSubscriptionConverter.xhttpLinks(raw))
        assertEquals(listOf(xhttp), PxlSubscriptionConverter.xhttpLinks("\uFEFF$raw"))
        assertEquals(listOf(xhttp), PxlSubscriptionConverter.xhttpLinks(base64))
        assertEquals(1, PxlSubscriptionConverter.countXhttpLinks(raw))
        assertEquals(emptyList<String>(), PxlSubscriptionConverter.xhttpLinks("not a subscription"))
    }

    @Test
    fun requestsStructuredFormatOnlyForActiveSmartResponse() {
        assertEquals(
            "https://example.test/s/token?format=singbox",
            HTTPClient.smartFormatUrl("https://example.test/s/token", "active", "raw"),
        )
        assertEquals(
            "https://example.test/s/token?name=pxl&format=singbox#section",
            HTTPClient.smartFormatUrl("https://example.test/s/token?name=pxl#section", "active", "base64"),
        )
        assertNull(HTTPClient.smartFormatUrl("https://example.test/s/token", null, "raw"))
        assertNull(HTTPClient.smartFormatUrl("https://example.test/s/token", "paused", "raw"))
        assertEquals(
            "https://example.test/s/token?format=singbox",
            HTTPClient.smartFormatUrl("https://example.test/s/token?format=raw", "active", "raw"),
        )
        assertEquals(
            "https://example.test/s/token?access=keep&format=singbox#part",
            HTTPClient.smartFormatUrl("https://example.test/s/token?access=keep&FORMAT=base64#part", "active", "base64"),
        )
        assertNull(HTTPClient.smartFormatUrl("https://example.test/s/token?format=raw&format=base64", "active", "raw"))
    }

    @Test
    fun requestsRawCompanionWhenSubscriptionAlreadyReturnsSingbox() {
        assertEquals(
            "https://example.test/s/token?format=raw",
            HTTPClient.rawFormatUrl("https://example.test/s/token", "active", "singbox"),
        )
        assertEquals(
            "https://example.test/s/token?access=keep&format=raw#part",
            HTTPClient.rawFormatUrl("https://example.test/s/token?access=keep&FORMAT=singbox#part", "active", "singbox"),
        )
        assertNull(HTTPClient.rawFormatUrl("https://example.test/s/token", "paused", "singbox"))
        assertNull(HTTPClient.rawFormatUrl("https://example.test/s/token?format=raw&format=singbox", "active", "singbox"))
    }

    @Test
    fun detectsStructuredResultAndAllowsLegacyModeFallback() {
        assertFalse(HTTPClient.isStructuredSmartResponse(null))
        assertFalse(HTTPClient.isStructuredSmartResponse(HTTPClient.Result("raw", null, "active", "raw")))
        assertFalse(HTTPClient.isStructuredSmartResponse(HTTPClient.Result("{}", null, null, "singbox")))
        assertTrue(HTTPClient.isStructuredSmartResponse(HTTPClient.Result("{}", null, "active", "singbox")))
    }

    @Test
    fun neverPairsXhttpLinksWithDifferentSmartSnapshot() {
        val old = HTTPClient.SmartRevisions("1", "1", "1", "1")
        val new = old.copy(health = "2")
        val raw = HTTPClient.Result("raw", null, "active", "raw", smartRevisions = old)
        val structured = HTTPClient.Result("{}", null, "active", "singbox", smartRevisions = old)
        assertTrue(HTTPClient.canPairSmartResponses(raw, structured))
        assertFalse(HTTPClient.canPairSmartResponses(raw, structured.copy(smartRevisions = new)))
        assertFalse(HTTPClient.canPairSmartResponses(raw, structured.copy(smartRevisions = null)))
        assertFalse(HTTPClient.canPairSmartResponses(raw.copy(smartRevisions = null), structured.copy(smartRevisions = null)))
        assertFalse(HTTPClient.canPairSmartResponses(raw.copy(subscriptionStatus = "expired"), structured))
    }

    @Test
    fun structuredFirstPairsOnlyMatchingRawXhttpLinks() {
        val revisions = HTTPClient.SmartRevisions("1", "2", "3", "4")
        val link = "vless://test-id@vpn.example:443?type=xhttp&security=reality#Finland"
        val structured = HTTPClient.Result("{}", null, "active", "singbox", smartRevisions = revisions)
        val raw = HTTPClient.Result(link, null, "active", "raw", smartRevisions = revisions)
        assertEquals(listOf(link), HTTPClient.withPairedXhttpLinks(structured, raw).xhttpLinks)
        assertEquals(1, HTTPClient.withPairedXhttpLinks(structured, raw).omittedXhttpCount)
        assertEquals(emptyList<String>(), HTTPClient.withPairedXhttpLinks(structured, raw.copy(smartRevisions = revisions.copy(health = "5"))).xhttpLinks)
    }

    @Test
    fun legacyRawKeepsItsXhttpLinksWithoutPretendingToBeSmart() {
        val link = "vless://00000000-0000-0000-0000-000000000001@vpn.example:443?type=xhttp&security=tls#Legacy"
        val legacy = HTTPClient.Result(link, null, null, null)
        val enriched = HTTPClient.withLegacyXhttpLinks(legacy)
        assertEquals(listOf(link), enriched.xhttpLinks)
        assertEquals(1, enriched.omittedXhttpCount)
        assertNull(enriched.smartRevisions)

        val mismatchedSmart = legacy.copy(subscriptionStatus = "active", subscriptionFormat = "raw")
        assertEquals(emptyList<String>(), HTTPClient.withLegacyXhttpLinks(mismatchedSmart).xhttpLinks)
    }

    @Test
    fun resultDebugStringDoesNotExposeSubscriptionCredentials() {
        val secret = "vless://private-credential@example.test:443?type=xhttp"
        val result = HTTPClient.Result(secret, "expire=123", "active", "raw", xhttpLinks = listOf(secret))
        assertFalse(result.toString().contains(secret))
        assertFalse(result.toString().contains("expire=123"))
    }

    @Test
    fun countsOneNodeWhenOnlyDisplayNameOrQueryOrderChanges() {
        val uuid = "00000000-0000-0000-0000-000000000001"
        val one = "vless://$uuid@vpn.example:443?type=xhttp&security=tls#Germany"
        val same = "vless://$uuid@vpn.example:443?security=tls&type=xhttp#Finland"
        assertEquals(1, PxlSubscriptionConverter.countXhttpLinks("$one\n$same"))
    }
}
