package io.nekohasekai.sfa.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class PxlProbeDiagnosticsTest {
    @Test
    fun classifiesFailuresWithoutReturningSensitiveDetails() {
        assertEquals("timeout", PxlProbeDiagnostics.category("dial 192.0.2.1:443: context deadline exceeded"))
        assertEquals("authentication", PxlProbeDiagnostics.category("auth failed for secret-password"))
        assertEquals("unsupported", PxlProbeDiagnostics.category("server probe is not supported with detour"))
    }
}
