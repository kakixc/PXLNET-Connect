package io.nekohasekai.sfa.utils

import io.nekohasekai.sfa.database.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PxlRoutingPresetsTest {
    @Test
    fun smartPresetDisablesPerAppProxyAndEnablesSmartRouting() {
        val current = PxlRoutingPresetState(
            preset = PxlRoutingPreset.SelectedApps,
            smartRoutingEnabled = false,
            perAppProxyEnabled = true,
            perAppProxyManagedMode = false,
            perAppProxyMode = Settings.PER_APP_PROXY_INCLUDE,
            perAppProxyManagedModeType = Settings.PER_APP_PROXY_EXCLUDE,
            perAppProxyManagedList = emptySet(),
        )

        val next = PxlRoutingPresets.stateFor(current, PxlRoutingPreset.Smart)

        assertEquals(PxlRoutingPreset.Smart, next.preset)
        assertTrue(next.smartRoutingEnabled)
        assertFalse(next.perAppProxyEnabled)
        assertFalse(next.perAppProxyManagedMode)
    }

    @Test
    fun gamesPresetUsesManagedIncludeWithoutOverwritingManualSelection() {
        val current = PxlRoutingPresetState(
            preset = PxlRoutingPreset.SelectedApps,
            smartRoutingEnabled = false,
            perAppProxyEnabled = true,
            perAppProxyManagedMode = false,
            perAppProxyMode = Settings.PER_APP_PROXY_INCLUDE,
            perAppProxyManagedModeType = Settings.PER_APP_PROXY_EXCLUDE,
            perAppProxyManagedList = emptySet(),
        )
        val games = setOf("com.example.game")

        val next = PxlRoutingPresets.stateFor(current, PxlRoutingPreset.Games, games)

        assertEquals(PxlRoutingPreset.Games, next.preset)
        assertFalse(next.smartRoutingEnabled)
        assertTrue(next.perAppProxyEnabled)
        assertTrue(next.perAppProxyManagedMode)
        assertEquals(Settings.PER_APP_PROXY_INCLUDE, next.perAppProxyManagedModeType)
        assertEquals(games, next.perAppProxyManagedList)
        assertEquals(Settings.PER_APP_PROXY_INCLUDE, next.perAppProxyMode)
    }
}
