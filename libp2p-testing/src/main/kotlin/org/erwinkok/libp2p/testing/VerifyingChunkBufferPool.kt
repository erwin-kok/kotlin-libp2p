// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.testing

import io.ktor.utils.io.core.internal.ChunkBuffer
import io.ktor.utils.io.pool.ObjectPool
import org.junit.jupiter.api.Assertions.assertTrue
import java.lang.System.identityHashCode

class VerifyingChunkBufferPool(
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE,
) : ObjectPool<ChunkBuffer> {
    override val capacity: Int = Int.MAX_VALUE
    private val allocator = DefaultAllocator
    private val allocated = mutableSetOf<IdentityWrapper>()

    override fun borrow(): ChunkBuffer {
        val instance = ChunkBuffer(allocator.alloc(bufferSize), null, this)
        synchronized(this) {
            check(allocated.add(IdentityWrapper(instance, Throwable())))
        }
        return instance
    }

    override fun recycle(instance: ChunkBuffer) {
        synchronized(this) {
            check(allocated.remove(IdentityWrapper(instance, null)))
        }
        allocator.free(instance.memory)
    }

    override fun dispose() = Unit

    fun resetInUse() {
        synchronized(this) {
            allocated.clear()
        }
    }

    fun assertNoInUse() {
        val traces = synchronized(this) {
            allocated.mapNotNull(IdentityWrapper::throwable)
        }
        assertTrue(traces.isEmpty(), traceMessage(traces))
    }

    private fun traceMessage(traces: List<Throwable>): String = traces
        .groupBy(Throwable::stackTraceToString)
        .mapKeys { alignStackTrace(it.key) }
        .mapValues { it.value.size }
        .toList()
        .joinToString("\n", "Buffers in use[${traces.size}]:\n", "\n") { (stack, amount) ->
            "Buffer creation trace ($amount the same):\n$stack"
        }

    private fun alignStackTrace(trace: String): String {
        val stack = trace.split("\n").drop(1) // drops first empty trace
        val filtered =
            stack
                .filterNot { it.contains("VerifyingChunkBufferPool") } // filter not relevant
                .ifEmpty { stack }
        return filtered.joinToString("\n  ->", "  ->") { it.substringAfter("at") }
    }

    private class IdentityWrapper(private val instance: ChunkBuffer, val throwable: Throwable?) {
        override fun equals(other: Any?): Boolean {
            if (other !is IdentityWrapper) return false
            return other.instance == this.instance
        }

        override fun hashCode() = identityHashCode(instance)
    }
}
