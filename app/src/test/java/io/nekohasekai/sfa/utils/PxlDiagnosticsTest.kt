package io.nekohasekai.sfa.utils

import io.nekohasekai.sfa.R
import org.junit.Assert.assertEquals
import org.junit.Test

class PxlDiagnosticsTest {
    @Test
    fun detectsSubscriptionFormatError() {
        val result = PxlDiagnostics.detectProblemResource(
            "decode config: invalid character 'd' looking for beginning of value",
        )

        assertEquals(R.string.pxlnet_diagnostics_bad_subscription, result)
    }

    @Test
    fun detectsCertificateError() {
        val result = PxlDiagnostics.detectProblemResource("x509: certificate is valid for another host")

        assertEquals(R.string.pxlnet_diagnostics_tls, result)
    }

    @Test
    fun logcatHeaderAloneIsNotTreatedAsEvidence() {
        assertEquals(
            R.string.pxlnet_diagnostics_no_logs,
            PxlDiagnostics.detectProblemResource("--------- beginning of main"),
        )
    }
}
