// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.testing

import io.ktor.utils.io.bits.Allocator
import io.ktor.utils.io.bits.Memory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
inline fun <R> ByteArray.useMemory(offset: Int, length: Int, block: (Memory) -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return Memory(ByteBuffer.wrap(this, offset, length).slice().order(ByteOrder.BIG_ENDIAN)).let(block)
}

@Suppress("NOTHING_TO_INLINE")
inline fun Memory.Companion.of(array: ByteArray, offset: Int = 0, length: Int = array.size - offset): Memory {
    return Memory(ByteBuffer.wrap(array, offset, length).slice().order(ByteOrder.BIG_ENDIAN))
}

@Suppress("NOTHING_TO_INLINE")
inline fun Memory.Companion.of(buffer: ByteBuffer): Memory {
    return Memory(buffer.slice().order(ByteOrder.BIG_ENDIAN))
}

@PublishedApi
internal object DefaultAllocator : Allocator {
    override fun alloc(size: Int): Memory = Memory(ByteBuffer.allocate(size))

    override fun alloc(size: Long): Memory = alloc(size.toIntOrFail("size"))

    override fun free(instance: Memory) {
    }
}

@PublishedApi
@Suppress("NOTHING_TO_INLINE")
internal inline fun Long.toIntOrFail(name: String): Int {
    if (this >= Int.MAX_VALUE) failLongToIntConversion(this, name)
    return toInt()
}

@PublishedApi
internal fun failLongToIntConversion(value: Long, name: String): Nothing =
    throw IllegalArgumentException("Long value $value of $name doesn't fit into 32-bit integer")
