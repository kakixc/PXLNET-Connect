package io.nekohasekai.sfa.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class PxlCountryFlagTest {
    @Test fun recognizesImportedCountryNamesAndCodes() {
        assertEquals("🇵🇱", pxlCountryFlag("Польша · VLESS"))
        assertEquals("🇵🇱", pxlCountryFlag("PL-01 Hysteria2"))
        assertEquals("🇳🇱", pxlCountryFlag("Netherlands 10 bit"))
        assertEquals("🇩🇪", pxlCountryFlag("Germany · VLESS"))
        assertEquals("🇫🇮", pxlCountryFlag("Finland · Hysteria2"))
    }

    @Test fun preservesExistingFlagAndAvoidsUnknownGuess() {
        assertEquals("🇺🇦", pxlCountryFlag("🇺🇦 Kyiv · VLESS"))
        assertEquals(null, pxlCountryFlag("alpha-vless.example"))
    }
}
