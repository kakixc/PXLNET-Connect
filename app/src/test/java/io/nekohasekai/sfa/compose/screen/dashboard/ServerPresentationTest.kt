package io.nekohasekai.sfa.compose.screen.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerPresentationTest {
    @Test
    fun russianTagsUseVectorFlagAndNoEmojiLabel() {
        val tag = "🇩🇪 Германия · VLESS · Reality"
        assertEquals(ServerRegion.GERMANY, serverRegion(tag))
        assertEquals("Германия · VLESS · Reality", cleanServerTag(tag))
        assertEquals("VLESS · Reality", serverProtocol(tag))
    }

    @Test
    fun finlandAndAutoAreClassifiedWithoutChangingOutboundTag() {
        assertEquals(ServerRegion.FINLAND, serverRegion("🇫🇮 Финляндия · Hysteria2"))
        assertEquals(ServerRegion.AUTO, serverRegion("AUTO"))
        assertEquals("Hysteria2", serverProtocol("🇫🇮 Финляндия · Hysteria2"))
    }
}
