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

    @Test
    fun backgroundFallbackRequiresFreshSuccessfulMeasurement() {
        val candidates = listOf(
            PxlGuard.Candidate("failed", 0, 500),
            PxlGuard.Candidate("stale fast", 25, 400),
            PxlGuard.Candidate("fresh", 85, 500),
            PxlGuard.Candidate("fresh failed", 0, 500),
        )
        assertEquals("fresh", PxlGuard.selectFreshFallback("failed", candidates, 499))
        assertNull(PxlGuard.selectFreshFallback("failed", candidates, 501))
    }

    @Test
    fun resolvesNestedAutoToTheActuallySelectedNode() {
        assertEquals(
            "Finland Hysteria2",
            PxlGuard.resolveActiveTag(
                "AUTO",
                mapOf("AUTO" to "Fast", "Fast" to "Finland Hysteria2"),
                setOf("AUTO", "Fast"),
            ),
        )
        assertNull(PxlGuard.resolveActiveTag("AUTO", emptyMap(), setOf("AUTO")))
        assertNull(PxlGuard.resolveActiveTag("AUTO", mapOf("AUTO" to "AUTO"), setOf("AUTO")))
    }
}
