package io.nekohasekai.sfa.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PxlCrashSupportReportTest {
    @Test
    fun startsWithSupportInstructionsAndRedactsCredentials() {
        val report = PxlCrashSupportReport.format(
            version = "0.6.8-beta-test",
            android = "15",
            device = "test device",
            metadata = "source=Application",
            crashLog = "failed for hysteria2://password@example.org:443; token=private123",
        )

        assertTrue(report.startsWith("Отправьте @kaktusi_top\nЛог: PXLNET Connect\nВерсия: 0.6.8-beta-test\n"))
        assertTrue("Технические подробности" in report)
        assertFalse("password@example.org" in report)
        assertFalse("private123" in report)
    }
}
