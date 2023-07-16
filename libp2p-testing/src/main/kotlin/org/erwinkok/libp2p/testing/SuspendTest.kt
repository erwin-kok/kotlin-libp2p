// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.testing

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

val TestExceptionHandler = CoroutineExceptionHandler { c, e ->
    println("Error in $c -> ${e.stackTraceToString()}")
}

@OptIn(ExperimentalTime::class)
interface SuspendTest {
    val testTimeout: Duration get() = 10.seconds

    fun test(
        timeout: Duration = testTimeout,
        block: suspend CoroutineScope.() -> Unit,
    ) = runBlocking {
        val testError = runPhase("RUN", timeout, block)
        testError?.let { throw it }
    }

    private suspend fun runPhase(tag: String, timeout: Duration, block: suspend CoroutineScope.() -> Unit): Throwable? {
        println("[TEST] $tag started")
        return when (val result = runWithTimeout(timeout, block)) {
            is TestResult.Success -> {
                println("[TEST] $tag completed in ${result.duration}")
                null
            }

            is TestResult.Failed -> {
                println("[TEST] $tag failed in ${result.duration} with error: ${result.cause.stackTraceToString()}")
                result.cause
            }

            is TestResult.Timeout -> {
                println("[TEST] $tag failed by timeout: ${result.timeout}")
                result.cause
            }
        }
    }

    private sealed interface TestResult {
        class Success(val duration: Duration) : TestResult
        class Failed(val duration: Duration, val cause: Throwable) : TestResult
        class Timeout(val timeout: Duration, val cause: Throwable) : TestResult
    }

    private suspend fun runWithTimeout(timeout: Duration, block: suspend CoroutineScope.() -> Unit): TestResult =
        runCatching {
            withTimeout(timeout) {
                measureTimedValue {
                    runCatching {
                        block()
                    }
                }
            }
        }.fold(
            onSuccess = { (result, duration) ->
                result.fold(
                    onSuccess = { TestResult.Success(duration) },
                    onFailure = { TestResult.Failed(duration, it) },
                )
            },
            onFailure = { TestResult.Timeout(timeout, it) },
        )
}
