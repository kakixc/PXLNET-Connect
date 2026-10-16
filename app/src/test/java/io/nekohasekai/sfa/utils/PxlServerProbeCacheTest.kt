package io.nekohasekai.sfa.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PxlServerProbeCacheTest {
    @Test
    fun reusesRecentMeasurementOnlyForSameProfileNetworkAndServiceState() {
        val cache = PxlServerProbeCache(ttlMillis = 120_000)
        val key = PxlServerProbeCache.Key(1, "Germany", false, "wifi")
        cache.record(key, 220, 1_000)

        assertEquals(220, cache.get(key, 120_999)?.milliseconds)
        assertNull(cache.get(key.copy(profileId = 2), 2_000))
        assertNull(cache.get(key.copy(networkId = "mobile"), 2_000))
        assertNull(cache.get(key.copy(serviceStarted = true), 2_000))
        assertNull(cache.get(key.copy(profileUpdatedAtMillis = 3_000), 2_000))
        assertNull(cache.get(key, 121_000))
    }

    @Test
    fun unavailableResultIsCachedAndExplicitRefreshClearsIt() {
        val cache = PxlServerProbeCache()
        val key = PxlServerProbeCache.Key(1, "Finland", true, "wifi")
        cache.record(key, null, 1_000)
        assertNull(cache.get(key, 2_000)?.milliseconds)
        cache.clear()
        assertNull(cache.get(key, 2_000))
    }
}
