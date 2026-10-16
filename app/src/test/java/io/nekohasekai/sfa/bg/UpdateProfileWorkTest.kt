package io.nekohasekai.sfa.bg

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateProfileWorkTest {
    @Test
    fun sixHourIntervalDoesNotRefreshEarly() {
        val now = 1_000_000_000L
        assertFalse(UpdateProfileWork.isDue(now - 5 * 60 * 60_000L, now, 360, force = false))
        assertTrue(UpdateProfileWork.isDue(now - 6 * 60 * 60_000L, now, 360, force = false))
    }

    @Test
    fun manualRefreshIgnoresSchedule() {
        val now = 1_000_000_000L
        assertTrue(UpdateProfileWork.isDue(now, now, 720, force = true))
    }
}
