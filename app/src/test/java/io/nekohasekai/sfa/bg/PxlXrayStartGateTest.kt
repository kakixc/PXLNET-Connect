package io.nekohasekai.sfa.bg

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PxlXrayStartGateTest {
    @Test
    fun deathBeforeStatusPublicationRejectsStarted() {
        val gate = PxlXrayStartGate()
        val generation = gate.begin()
        var published = false

        assertFalse(gate.reportDeath())
        assertFalse(gate.publish(generation) { published = true })
        assertFalse(published)
    }

    @Test
    fun deathAfterPublicationRequiresStop() {
        val gate = PxlXrayStartGate()
        val generation = gate.begin()

        assertTrue(gate.publish(generation) {})
        assertTrue(gate.reportDeath())
        assertFalse(gate.reportDeath())
    }

    @Test
    fun previousStartCannotPublishIntoNextSession() {
        val gate = PxlXrayStartGate()
        val oldGeneration = gate.begin()
        gate.close()
        val newGeneration = gate.begin()

        assertFalse(gate.publish(oldGeneration) {})
        assertTrue(gate.publish(newGeneration) {})
    }

    @Test
    fun synchronousStopDuringPublicationCannotResurrectStarted() {
        val gate = PxlXrayStartGate()
        val generation = gate.begin()

        assertFalse(gate.publish(generation) { gate.close() })
        assertFalse(gate.reportDeath())
    }

    @Test
    fun cancelledStartIsNotCurrentAfterCloseOrDeath() {
        val gate = PxlXrayStartGate()
        val first = gate.begin()
        assertTrue(gate.isStarting(first))
        gate.close()
        assertFalse(gate.isStarting(first))
        val second = gate.begin()
        assertFalse(gate.isStarting(first))
        assertTrue(gate.isStarting(second))
        gate.reportDeath()
        assertFalse(gate.isStarting(second))
    }
}
