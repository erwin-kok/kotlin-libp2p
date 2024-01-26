// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.util

import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.SelectClause1
import java.util.concurrent.locks.ReentrantLock
import kotlin.time.Duration

class Timer(private val scope: CoroutineScope, private val timeout: Duration, startTimer: Boolean = true) {
    private val timeoutChannel = Channel<Unit>(Channel.RENDEZVOUS)
    private var job: Job? = null
    private val lock = ReentrantLock()

    val onTimeout: SelectClause1<Unit>
        get() = timeoutChannel.onReceive

    var isRunning: Boolean = false
        get() {
            lock.withLock {
                return field
            }
        }
        private set(value) {
            lock.withLock {
                field = value
            }
        }

    init {
        if (startTimer) {
            start()
        }
    }

    fun start(): Boolean {
        return lock.withLock {
            if (job == null) {
                job = run()
                true
            } else {
                false
            }
        }
    }

    fun stop() {
        lock.withLock {
            job?.cancel()
            job = null
        }
    }

    fun restart() {
        lock.withLock {
            job?.cancel()
            job = run()
        }
    }

    suspend fun wait() {
        lock.withLock {
            if (job != null) {
                timeoutChannel.receive()
            }
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun run(): Job {
        isRunning = true
        val name = StringUtils.randomString(8)
        return scope.launch(CoroutineName("timer-$name")) {
            delay(timeout)
            timeoutChannel.trySend(Unit)
        }.apply {
            invokeOnCompletion(true) {
                isRunning = false
            }
        }
    }
}
