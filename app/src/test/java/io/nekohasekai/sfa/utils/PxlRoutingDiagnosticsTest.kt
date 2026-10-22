package io.nekohasekai.sfa.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class PxlRoutingDiagnosticsTest {
    @Test
    fun snapshotContainsOnlyFixedRoutingCategories() {
        assertEquals(
            "preset=smart, smart_rules=on, per_app=off, managed=off",
            PxlRoutingDiagnostics.snapshot(PxlRoutingPreset.Smart, true, false, false),
        )
        assertEquals(
            "preset=video, smart_rules=off, per_app=on, managed=on",
            PxlRoutingDiagnostics.snapshot(PxlRoutingPreset.Video, false, true, true),
        )
    }
}
