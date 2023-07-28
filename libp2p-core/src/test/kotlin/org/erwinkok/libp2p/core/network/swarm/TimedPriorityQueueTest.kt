// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
@file:OptIn(ExperimentalCoroutinesApi::class)

package org.erwinkok.libp2p.core.network.swarm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

internal class TimedPriorityQueueTest {
    @Test
    fun testPriority() = runTest(timeout = 1.minutes) {
        repeat(500) {
            val queue = TimedPriorityQueue<TestElement>(this, 1000)
            val scheduleTimes = mutableListOf<Instant>()
            val now = Instant.now()
            repeat(1000) {
                val seconds = Random.nextLong(60 * 60 * 24 * 365)
                scheduleTimes.add(now.plus(seconds, ChronoUnit.SECONDS))
            }
            scheduleTimes.forEach {
                queue.queueElement(TestElement(it))
            }
            queue.close()
            val actualScheduleTimes = mutableListOf<Instant>()
            repeat(1000) {
                actualScheduleTimes.add(queue.dequeueElement().scheduleTime)
            }
            assertEquals(actualScheduleTimes.toSet(), scheduleTimes.toSet())
            var current = now
            for (element in actualScheduleTimes) {
                assertTrue(element.isAfter(current) || element == current)
                current = element
            }
        }
    }

    @Test
    fun testReadAfterClose() = runTest {
        val queue = TimedPriorityQueue<TestElement>(this, 16)
        val now = Instant.now()
        val nowPlusOneHour = now.plus(1, ChronoUnit.HOURS)
        queue.queueElement(TestElement(nowPlusOneHour))
        queue.queueElement(TestElement(now))
        assertEquals(now, queue.dequeueElement().scheduleTime)
        queue.close()
        assertEquals(nowPlusOneHour, queue.dequeueElement().scheduleTime)
    }

    @Test
    fun testReadAfterCancel() = runTest {
        val queue = TimedPriorityQueue<TestElement>(this, 16)
        val now = Instant.now()
        val nowPlusOneHour = now.plus(1, ChronoUnit.HOURS)
        queue.queueElement(TestElement(nowPlusOneHour))
        queue.queueElement(TestElement(now))
        assertEquals(now, queue.dequeueElement().scheduleTime)
        queue.cancel()
        val exception = assertThrows<CancellationException> {
            assertEquals(nowPlusOneHour, queue.dequeueElement().scheduleTime)
        }
        assertEquals("Channel was cancelled", exception.message)
    }

    @Test
    fun testReadAfterRemove() = runTest {
        val queue = TimedPriorityQueue<TestElement>(this, 16)
        val now = Instant.now()
        val nowPlusOneHour = now.plus(1, ChronoUnit.HOURS)
        queue.queueElement(TestElement(nowPlusOneHour))
        queue.queueElement(TestElement(now))
        queue.removeElement { it.scheduleTime == now }
        queue.close()
        assertEquals(nowPlusOneHour, queue.dequeueElement().scheduleTime)
    }

    @Test
    fun testBlockWhenQueueFull() = runTest {
        val queue = TimedPriorityQueue<TestElement>(this, 3)
        val now = Instant.now()
        val nowPlusOneSeconds = now.plus(1, ChronoUnit.SECONDS)
        val nowPlusTwoSeconds = now.plus(2, ChronoUnit.SECONDS)
        val nowPlusThreeSeconds = now.plus(4, ChronoUnit.SECONDS)
        queue.queueElement(TestElement(nowPlusOneSeconds))
        queue.queueElement(TestElement(nowPlusThreeSeconds))
        queue.queueElement(TestElement(now))
        val result = withTimeoutOrNull(100) {
            queue.queueElement(TestElement(nowPlusTwoSeconds))
        }
        assertNull(result)
        assertEquals(now, queue.dequeueElement().scheduleTime)
        assertEquals(nowPlusOneSeconds, queue.dequeueElement().scheduleTime)
        assertEquals(nowPlusThreeSeconds, queue.dequeueElement().scheduleTime)
        queue.close()
    }

    @Test
    fun stress() = runTest(timeout = 1.minutes) {
        repeat(500) {
            val queue = TimedPriorityQueue<TestElement>(this, 7)
            val scheduleTimes = mutableListOf<Instant>()
            val now = Instant.now()
            repeat(1000) {
                val seconds = Random.nextLong(60 * 60 * 24 * 365)
                scheduleTimes.add(now.plus(seconds, ChronoUnit.SECONDS))
            }
            val job = launch {
                scheduleTimes.forEach {
                    queue.queueElement(TestElement(it))
                }
                queue.close()
            }
            val actualScheduleTimes = mutableListOf<Instant>()
            repeat(1000) {
                actualScheduleTimes.add(queue.dequeueElement().scheduleTime)
            }
            job.join()
            assertEquals(actualScheduleTimes.toSet(), scheduleTimes.toSet())
        }
    }

    private class TestElement(override val scheduleTime: Instant) : TimedQueueElement
}
