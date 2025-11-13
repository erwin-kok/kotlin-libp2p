package org.erwinkok.libp2p.muxer.mplex

import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.core.toByteArray
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeFully
import org.erwinkok.multiformat.util.UVarInt
import org.erwinkok.result.Ok
import org.erwinkok.result.Result

object FrameWriter {
    internal suspend fun writeMplexFrame(channel: ByteWriteChannel, frame: Frame) {
        val header = (frame.id shl 3) or frame.type.toLong()
        writeUnsignedVarInt(channel, header.toULong())
        when (frame) {
            is Frame.NewStreamFrame -> writeNewStreamFrame(channel, frame)
            is Frame.MessageFrame -> writeMessageFrame(channel, frame)
            is Frame.CloseFrame -> writeCloseFrame(channel)
            is Frame.ResetFrame -> writeResetFrame(channel)
        }
    }

    private suspend fun writeNewStreamFrame(channel: ByteWriteChannel, frame: Frame.NewStreamFrame) {
        writeUnsignedVarInt(channel, frame.name.length.toULong())
        channel.writeFully(frame.name.toByteArray())
    }

    private suspend fun writeMessageFrame(channel: ByteWriteChannel, frame: Frame.MessageFrame) {
        writeUnsignedVarInt(channel, frame.data.size.toULong())
        channel.writeFully(frame.data)
    }

    private suspend fun writeCloseFrame(channel: ByteWriteChannel) {
        writeUnsignedVarInt(channel, 0UL)
    }

    private suspend fun writeResetFrame(channel: ByteWriteChannel) {
        writeUnsignedVarInt(channel, 0UL)
    }

    suspend fun writeUnsignedVarInt(channel: ByteWriteChannel, value: ULong): Result<Int> {
        return UVarInt.coWriteUnsignedVarInt(value) { Ok(channel.writeByte(it)) }
    }
}
