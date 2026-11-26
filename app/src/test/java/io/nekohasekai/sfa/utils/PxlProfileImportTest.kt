package io.nekohasekai.sfa.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PxlProfileImportTest {
    @Test
    fun acceptsSubscriptionAndSupportedServerLinks() {
        assertEquals(ProfileLinkKind.Subscription, profileLinkKind(" https://example.test/sub "))
        assertEquals(ProfileLinkKind.Server, profileLinkKind("vless://test@example.test:443#Test node"))
        assertEquals(ProfileLinkKind.Server, profileLinkKind("hy2://test@example.test:443"))
        assertEquals(ProfileLinkKind.Server, profileLinkKind("hysteria2://test@example.test:443"))
        assertNull(profileLinkKind("https://"))
        assertNull(profileLinkKind("file:///tmp/profile.json"))
        assertNull(profileLinkKind("ordinary text"))
    }

    @Test
    fun prefersCustomNameOverServiceTitleAndImportHint() {
        assertEquals("My name", importedProfileName(" My name ", "Provider", "From QR", "https://example.test/sub", "Subscription"))
    }

    @Test
    fun decodesTheProviderTitleAndFallsBackWhenItIsInvalid() {
        val link = "https://example.test/sub#From%20link"
        assertEquals("Provider", importedProfileName("", "base64:UHJvdmlkZXI=", "From QR", link, "Subscription"))
        assertEquals("From QR", importedProfileName("", "base64:???", "From QR", link, "Subscription"))
        assertEquals("From link", importedProfileName("", null, null, link, "Subscription"))
        assertEquals("Subscription", importedProfileName("", null, null, "https://example.test/sub", "Subscription"))
    }
}
