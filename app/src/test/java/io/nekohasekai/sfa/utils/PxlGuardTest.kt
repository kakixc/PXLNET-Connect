package io.nekohasekai.sfa.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PxlGuardTest {
    @Test
    fun selectsFastestReachableAlternative() {
        val fallback = PxlGuard.selectFallback(
            selectedTag = "Germany VLESS",
            candidates = listOf(
                "Germany VLESS" to 0,
                "Finland Hysteria2" to 142,
                "Germany Hysteria2" to 89,
                "Offline" to -1,
            ),
        )

        assertEquals("Germany Hysteria2", fallback)
    }

    @Test
    fun returnsNullWhenNoAlternativeIsReachable() {
        assertNull(PxlGuard.selectFallback("A", listOf("A" to 0, "B" to null)))
    }
}
