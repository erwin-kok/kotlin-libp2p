// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.util

import io.ktor.utils.io.core.BytePacketBuilder
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.EOFException
import io.ktor.utils.io.core.internal.ChunkBuffer
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.pool.ObjectPool
import org.erwinkok.multiformat.util.UVarInt
import org.erwinkok.result.Err
import org.erwinkok.result.Errors
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
inline fun buildPacket(pool: ObjectPool<ChunkBuffer>, block: BytePacketBuilder.() -> Unit): ByteReadPacket {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    val builder = BytePacketBuilder(pool)
    try {
        block(builder)
        return builder.build()
    } catch (t: Throwable) {
        builder.release()
        throw t
    }
}

fun ByteReadPacket.readPacket(pool: ObjectPool<ChunkBuffer>): ByteReadPacket {
    if (isEmpty) return ByteReadPacket.Empty
    return buildPacket(pool) {
        writePacket(this@readPacket)
    }
}

fun ByteReadPacket.readPacket(pool: ObjectPool<ChunkBuffer>, length: Int): ByteReadPacket {
    if (length == 0) return ByteReadPacket.Empty
    return buildPacket(pool) {
        writePacket(this@readPacket, length)
    }
}

fun ByteReadPacket.readUnsignedVarInt(): Result<ULong> {
    return UVarInt.readUnsignedVarInt {
        try {
            Ok(readByte().toUByte())
        } catch (e: EOFException) {
            Err(Errors.EndOfStream)
        }
    }
}

fun BytePacketBuilder.writeUnsignedVarInt(x: Int): Result<Int> {
    return writeUnsignedVarInt(x.toULong())
}

fun BytePacketBuilder.writeUnsignedVarInt(x: ULong): Result<Int> {
    return UVarInt.writeUnsignedVarInt(x) {
        try {
            this.writeByte(it)
            Ok(Unit)
        } catch (e: Exception) {
            Err(Errors.EndOfStream)
        }
    }
}
