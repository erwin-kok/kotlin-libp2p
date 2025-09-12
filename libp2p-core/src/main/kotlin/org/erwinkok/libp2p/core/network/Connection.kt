// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.network

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.core.internal.ChunkBuffer
import io.ktor.utils.io.pool.ObjectPool
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.multiformat.multistream.Utf8Connection
import org.erwinkok.multiformat.util.UVarInt
import org.erwinkok.result.Err
import org.erwinkok.result.Error
import org.erwinkok.result.Errors
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.getOrElse
import org.erwinkok.result.map
import org.erwinkok.result.toErrorIf

interface Connection : Utf8Connection, AwaitableClosable, DisposableHandle {
    val pool: ObjectPool<ChunkBuffer>
    val input: ByteReadChannel
    val output: ByteWriteChannel

    override suspend fun readUtf8(): Result<String> {
        return input.readUnsignedVarInt()
            .toErrorIf({ it > 1024u }, { ErrTooLarge })
            .map { it.toInt() }
            .map { wanted ->
                try {
                    val bytes = ByteArray(wanted)
                    input.readFully(bytes)
                    if (bytes.isEmpty() || Char(bytes[bytes.size - 1].toInt()) != '\n') {
                        return Err("message did not have trailing newline")
                    }
                    return Ok(String(bytes).trim { it <= ' ' })
                } catch (_: ClosedReceiveChannelException) {
                    return Err(Errors.EndOfStream)
                }
            }
    }

    override suspend fun writeUtf8(vararg messages: String): Result<Unit> {
        for (message in messages) {
            val messageNewline = message + '\n'
            output.writeUnsignedVarInt(messageNewline.length).getOrElse { return Err(it) }
            output.writeFully(messageNewline.toByteArray())
        }
        output.flush()
        return Ok(Unit)
    }

    override fun dispose() {
        try {
            close()
        } catch (_: Throwable) {
            // Ignore
        }
    }

    companion object {
        val ErrTooLarge = Error("incoming message was too large")
    }
}

suspend fun ByteReadChannel.readUnsignedVarInt(): Result<ULong> {
    return UVarInt.coReadUnsignedVarInt { _ ->
        try {
            Ok(readByte().toUByte())
        } catch (_: Exception) {
            Err(Errors.EndOfStream)
        }
    }
}

suspend fun ByteWriteChannel.writeUnsignedVarInt(value: Int): Result<Int> {
    return writeUnsignedVarInt(value.toULong())
}

suspend fun ByteWriteChannel.writeUnsignedVarInt(value: Long): Result<Int> {
    return writeUnsignedVarInt(value.toULong())
}

suspend fun ByteWriteChannel.writeUnsignedVarInt(value: ULong): Result<Int> {
    return UVarInt.coWriteUnsignedVarInt(value) { Ok(writeByte(it)) }
}
