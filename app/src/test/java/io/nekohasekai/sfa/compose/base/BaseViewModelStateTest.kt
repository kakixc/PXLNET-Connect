package io.nekohasekai.sfa.compose.base

import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Test

class BaseViewModelStateTest {
    @Test
    fun concurrentReducersPreserveBothStateChanges() {
        val model = object : BaseViewModel<Int, Unit>() {
            override fun createInitialState() = 0

            fun increment() = updateState { this + 1 }
        }
        val start = CountDownLatch(1)
        val writers = List(4) {
            thread {
                start.await()
                repeat(2_000) { model.increment() }
            }
        }
        start.countDown()
        writers.forEach { it.join() }

        assertEquals(8_000, model.uiState.value)
    }
}
