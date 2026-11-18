package io.nekohasekai.sfa.utils

import org.junit.Assert.assertThrows
import org.junit.Test

class PxlDnsProbeTest {
    @Test fun acceptsMatchingSuccessfulDnsAnswer() {
        val answer = byteArrayOf(0x12, 0x34, 0x81.toByte(), 0x80.toByte(), 0, 1, 0, 1, 0, 0, 0, 0) + ByteArray(23)
        PxlDnsProbe.validateResponse(answer, 0x1234)
    }

    @Test fun rejectsMismatchedAndFailedDnsAnswers() {
        val answer = byteArrayOf(0x12, 0x34, 0x81.toByte(), 0x80.toByte(), 0, 1, 0, 1, 0, 0, 0, 0) + ByteArray(23)
        assertThrows(IllegalArgumentException::class.java) { PxlDnsProbe.validateResponse(answer, 0x1235) }
        answer[3] = 0x83.toByte()
        assertThrows(IllegalArgumentException::class.java) { PxlDnsProbe.validateResponse(answer, 0x1234) }
    }
}
