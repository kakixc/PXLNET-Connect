package io.nekohasekai.sfa.xray;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PxlXrayOwnerGateTest {
    @Test
    public void delayedDeathOfPreviousOwnerCannotStopReplacement() {
        Object previous = new Object();
        Object replacement = new Object();

        assertFalse(PxlXrayOwnerGate.isCurrent(previous, replacement));
        assertTrue(PxlXrayOwnerGate.isCurrent(replacement, replacement));
        assertFalse(PxlXrayOwnerGate.isCurrent(previous, null));
    }
}
