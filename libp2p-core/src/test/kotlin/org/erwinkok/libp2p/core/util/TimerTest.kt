package org.erwinkok.libp2p.core.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class TimerTest {
    @Test
    fun start() = runTest {
        val timer = Timer(this, 60.seconds)
        assertTrue(timer.isRunning)
        timer.wait()
        assertFalse(timer.isRunning)
    }

    @Test
    fun multipleStart() = runTest {
        val timer = Timer(this, 60.seconds, false)
        assertFalse(timer.isRunning)
        assertTrue(timer.start())
        assertFalse(timer.start())
        assertTrue(timer.isRunning)
        timer.wait()
        assertFalse(timer.isRunning)
    }

    @Test
    fun stop() = runTest {
        val timer = Timer(this, 60.seconds)
        assertTrue(timer.isRunning)
        timer.stop()
        assertFalse(timer.isRunning)
    }

    @Test
    fun restart() = runTest {
        val timer = Timer(this, 60.seconds)
        assertTrue(timer.isRunning)
        timer.restart()
        assertTrue(timer.isRunning)
        timer.stop()
        assertFalse(timer.isRunning)
    }

    @Test
    fun realWait() = runTest {
        withContext(Dispatchers.Default) {
            val timer = Timer(this, 1.seconds)
            assertTrue(timer.isRunning)
            delay(2.seconds)
            assertFalse(timer.isRunning)
        }
    }

    @Test
    fun realWaitRestart() = runTest {
        withContext(Dispatchers.Default) {
            val timer = Timer(this, 4.seconds)
            assertTrue(timer.isRunning)
            delay(2.seconds)
            assertTrue(timer.isRunning)
            timer.restart()
            delay(2.seconds)
            assertTrue(timer.isRunning)
            timer.restart()
            delay(2.seconds)
            assertTrue(timer.isRunning)
            delay(3.seconds)
            assertFalse(timer.isRunning)
        }
    }
}
