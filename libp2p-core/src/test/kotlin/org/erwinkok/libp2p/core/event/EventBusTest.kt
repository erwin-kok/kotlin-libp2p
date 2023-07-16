// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
@file:OptIn(ExperimentalCoroutinesApi::class)

package org.erwinkok.libp2p.core.event

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class EventBusTest {
    class TestEvent(val message: String) : EventType()

    @Test
    fun subscribeAnTryPublishPublisher() = runTest {
        var result: String? = null
        val bus = EventBus()
        val emitter = bus.publisher<TestEvent>()
        val done = Channel<Unit>(1)
        val job = launch {
            bus.subscribe<TestEvent>(this, this, Dispatchers.Unconfined) {
                result = it.message
                cancel()
            }
            done.send(Unit)
        }
        done.receive()
        assertTrue(emitter.tryPublish(TestEvent("Hello World!")))
        job.join()
        assertEquals("Hello World!", result)
        bus.close()
    }

    @Test
    fun subscribeAnPublishPublisher() = runTest {
        var result: String? = null
        val bus = EventBus()
        val emitter = bus.publisher<TestEvent>()
        val done = Channel<Unit>(1)
        val job = launch {
            bus.subscribe<TestEvent>(this, this, Dispatchers.Unconfined) {
                result = it.message
                cancel()
            }
            done.send(Unit)
        }
        done.receive()
        emitter.publish(TestEvent("Hello World!"))
        job.join()
        assertEquals("Hello World!", result)
        bus.close()
    }

    @Test
    fun subscribeWildcardAndEmitPublisher() = runTest {
        var result: String? = null
        val bus = EventBus()
        val emitter = bus.publisher<TestEvent>()
        val done = Channel<Unit>(1)
        val job = launch {
            bus.subscribeWildcard(this, this, Dispatchers.Unconfined) {
                assertInstanceOf(TestEvent::class.java, it)
                result = (it as TestEvent).message
                cancel()
            }
            done.send(Unit)
        }
        done.receive()
        assertTrue(emitter.tryPublish(TestEvent("Hello World!")))
        job.join()
        assertEquals("Hello World!", result)
        bus.close()
    }

    @Test
    fun subscribeAndTryPublish() = runTest {
        var result: String? = null
        val bus = EventBus()
        val done = Channel<Unit>(1)
        val job = launch {
            bus.subscribe<TestEvent>(this, this, Dispatchers.Unconfined) {
                result = it.message
                cancel()
            }
            done.send(Unit)
        }
        done.receive()
        assertTrue(bus.tryPublish(TestEvent("Hello World!")))
        job.join()
        assertEquals("Hello World!", result)
        bus.close()
    }

    @Test
    fun subscribeAndPublish() = runTest {
        var result: String? = null
        val bus = EventBus()
        val done = Channel<Unit>(1)
        val job = launch {
            bus.subscribe<TestEvent>(this, this, Dispatchers.Unconfined) {
                result = it.message
                cancel()
            }
            done.send(Unit)
        }
        done.receive()
        bus.publish(TestEvent("Hello World!"))
        job.join()
        assertEquals("Hello World!", result)
        bus.close()
    }
}
