package io.nekohasekai.sfa.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PxlServerLatencyProbeTest {
    @Test
    fun returnsMiddleOfThreeSuccessfulSamples() {
        assertEquals(82, PxlServerLatencyProbe.median(listOf(700, 82, 71)))
    }

    @Test
    fun neverTurnsMissingOrFailedSampleIntoPing() {
        assertNull(PxlServerLatencyProbe.median(listOf(40, 50)))
        assertNull(PxlServerLatencyProbe.median(listOf(40, 0, 50)))
    }

    @Test
    fun unknownPreferenceFallsBackToExistingWebsiteTest() {
        assertEquals(PxlLatencySource.WEBSITE_THROUGH_VPN, PxlLatencySource.fromPersisted(null))
        assertEquals(PxlLatencySource.WEBSITE_THROUGH_VPN, PxlLatencySource.fromPersisted("unknown"))
    }
}
