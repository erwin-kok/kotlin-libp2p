// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
@file:OptIn(DelicateCoroutinesApi::class)

package org.erwinkok.libp2p.core.network.swarm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.RENDEZVOUS
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.SelectClause1
import kotlinx.coroutines.selects.SelectClause2
import kotlinx.coroutines.yield
import mu.KotlinLogging
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.result.errorMessage
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.PriorityBlockingQueue

private val logger = KotlinLogging.logger {}

interface TimedQueueElement {
    val scheduleTime: Instant
}

// We use RENDEZVOUS here since they have no buffer. Hence, these channels can not contain higher priority elements.
// When the queue is full, there is a chance that higher priority elements presented at the input are not processed
// accordingly.
internal class TimedPriorityQueue<T : TimedQueueElement>(
    scope: CoroutineScope,
    private val capacity: Int,
) : AwaitableClosable {
    private val _context = Job(scope.coroutineContext[Job])
    private val priorityQueue = PriorityBlockingQueue(capacity, ::comparator)
    private val inputChannel = Channel<T>(RENDEZVOUS)
    private val outputChannel = Channel<T>(RENDEZVOUS)

    override val jobContext: Job get() = _context

    init {
        scope.launch(_context + CoroutineName("timed-prio-queue")) {
            while (!inputChannel.isClosedForReceive || !outputChannel.isClosedForSend) {
                try {
                    receiveAndFillUpPriorityQueue()
                    sendOneEligibleElement()
                    if (outputChannel.isClosedForSend) {
                        // outputChannel is closed. Makes no sense for further processing.
                        inputChannel.cancel()
                        outputChannel.cancel()
                        priorityQueue.clear()
                    } else if (inputChannel.isClosedForReceive && priorityQueue.isEmpty()) {
                        outputChannel.close()
                    }
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    logger.warn { "Unexpected error occurred in timed priority queue: ${errorMessage(e)}" }
                    throw e
                }
            }
        }
    }

    val isClosedForDequeue: Boolean
        get() = outputChannel.isClosedForReceive

    suspend fun queueElement(element: T) {
        inputChannel.send(element)
    }

    suspend fun dequeueElement(): T {
        return outputChannel.receive()
    }

    val onSend: SelectClause2<T, SendChannel<T>>
        get() = inputChannel.onSend

    val onReceive: SelectClause1<T>
        get() = outputChannel.onReceive

    fun removeElement(function: (T) -> Boolean) {
        priorityQueue.removeIf(function)
    }

    fun cancel() {
        inputChannel.cancel()
        outputChannel.cancel()
        _context.complete()
    }

    override fun close() {
        inputChannel.close()
        _context.complete()
    }

    override val isClosed: Boolean
        get() = inputChannel.isClosedForReceive

    override fun toString(): String {
        val sb = StringBuilder()
        if (isClosed) {
            sb.append("closed")
        } else {
            sb.append("active")
        }
        sb.append(", pq=${priorityQueue.size}")
        return sb.toString()
    }

    // Try to read as many elements as possible and fill up the priority queue.
    private suspend fun receiveAndFillUpPriorityQueue() {
        while (!inputChannel.isClosedForReceive) {
            if (priorityQueue.size == 0) {
                try {
                    priorityQueue.add(inputChannel.receive())
                } catch (e: ClosedReceiveChannelException) {
                    break
                }
            } else if (priorityQueue.size < capacity) {
                val element = inputChannel.tryReceive().getOrNull()
                if (element != null) {
                    priorityQueue.add(element)
                } else {
                    // Nothing on the inputChannel. Try to send something out.
                    yield()
                    break
                }
            } else {
                // The priorityQueue is full
                yield()
                break
            }
        }
    }

    // Try to send one element, if eligible. The reason we only try to send one element is that we
    // try to fill up the priority queue as much as possible. The prioritization happens in  this queue
    // and the more elements it has, the better this prioritization works. If we send more elements, we
    // miss the chance of a high priority element presented at the input queue not processed on time.
    private suspend fun sendOneEligibleElement() {
        if (!outputChannel.isClosedForSend && priorityQueue.size > 0) {
            val element = priorityQueue.peek()
            val scheduleTime = element.scheduleTime
            val duration = Instant.now().until(scheduleTime, ChronoUnit.MILLIS)
            if (priorityQueue.size >= capacity || inputChannel.isClosedForReceive) {
                // The priority queue is full, which means that the order in the queue is fixed.
                // Or the inputChannel is closed, which means no more elements will be added to the priority queue
                // In both cases we can wait on the first element to expire and then send that element
                if (duration > 0) {
                    delay(duration)
                }
                outputChannel.send(element)
                priorityQueue.poll()
            } else if (duration <= 0 && outputChannel.trySend(element).isSuccess) {
                // If the first is expired, try to send it to the outputChannel.
                // We succeeded in sending the element. Remove it from the queue.
                priorityQueue.poll()
            } else {
                yield()
            }
        }
    }

    private fun comparator(elementA: T, elementB: T): Int {
        return elementA.scheduleTime.compareTo(elementB.scheduleTime)
    }
}
