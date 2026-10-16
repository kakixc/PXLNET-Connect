package io.nekohasekai.sfa

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PxlProcessRoleTest {
    @Test fun identifiesOnlyExactXrayProcess() {
        assertTrue(PxlProcessRole.isXraySidecar("net.pxlnet.connect:pxlxray", "net.pxlnet.connect"))
        assertFalse(PxlProcessRole.isXraySidecar("net.pxlnet.connect", "net.pxlnet.connect"))
        assertFalse(PxlProcessRole.isXraySidecar("net.pxlnet.connect:pxlxray-other", "net.pxlnet.connect"))
        assertFalse(PxlProcessRole.isXraySidecar("other.app:pxlxray", "net.pxlnet.connect"))
        assertFalse(PxlProcessRole.isXraySidecar(null, "net.pxlnet.connect"))
        assertTrue(PxlProcessRole.isXrayProbe("net.pxlnet.connect:pxlxrayprobe", "net.pxlnet.connect"))
        assertFalse(PxlProcessRole.isXrayProbe("net.pxlnet.connect:pxlxray", "net.pxlnet.connect"))
        assertFalse(PxlProcessRole.isXrayProbe("other.app:pxlxrayprobe", "net.pxlnet.connect"))
    }
}
