package io.nekohasekai.sfa.utils

import org.junit.Assert.assertTrue
import org.junit.Test

class PxlDiagnosticsTest {
    @Test
    fun detectsSubscriptionFormatError() {
        val result = PxlDiagnostics.detectProblem(
            "decode config: invalid character 'd' looking for beginning of value",
        )

        assertTrue(result.contains("неподдерживаемом формате"))
    }

    @Test
    fun detectsCertificateError() {
        val result = PxlDiagnostics.detectProblem("x509: certificate is valid for another host")

        assertTrue(result.contains("TLS-сертификата"))
    }
}
