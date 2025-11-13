package org.erwinkok.libp2p.muxer.mplex

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readByteArray
import io.ktor.utils.io.readPacket
import kotlinx.io.readByteArray
import org.erwinkok.result.Err
import org.erwinkok.result.Errors
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.getOrElse

object FrameReaderOld {
    internal suspend fun readMplexFrame(channel: ByteReadChannel): Result<Frame> {
        val header = readUnsignedVarInt(channel)
            .getOrElse { return Err(it) }
        val tag = (header and 0x7uL).toInt()
        val id = (header shr 3).toLong()
        val length = readUnsignedVarInt(channel)
            .getOrElse { return Err(it) }
            .toInt()
        if (tag == Frame.NewStreamTag) {
            val data = channel.readPacket(length)
            return Ok(Frame.NewStreamFrame(id, String(data.readByteArray())))
        }

        return when (tag) {
            Frame.MessageReceiverTag -> {
                val data = channel.readByteArray(length)
                Ok(Frame.MessageFrame(MplexStreamId(false, id), data, Frame.MessageReceiverTag))
            }

            Frame.MessageInitiatorTag -> {
                val data = channel.readByteArray(length)
                Ok(Frame.MessageFrame(MplexStreamId(true, id), data, Frame.MessageInitiatorTag))
            }

            Frame.CloseReceiverTag -> {
                if (length != 0) {
                    throw ProtocolViolationException("CloseFrame should not carry data")
                }
                Ok(Frame.CloseFrame(MplexStreamId(false, id), Frame.CloseReceiverTag))
            }

            Frame.CloseInitiatorTag -> {
                if (length != 0) {
                    throw ProtocolViolationException("CloseFrame should not carry data")
                }
                Ok(Frame.CloseFrame(MplexStreamId(true, id), Frame.CloseInitiatorTag))
            }

            Frame.ResetReceiverTag -> {
                if (length != 0) {
                    throw ProtocolViolationException("ResetFrame should not carry data")
                }
                Ok(Frame.ResetFrame(MplexStreamId(false, id), Frame.ResetReceiverTag))
            }

            Frame.ResetInitiatorTag -> {
                if (length != 0) {
                    throw ProtocolViolationException("ResetFrame should not carry data")
                }
                Ok(Frame.ResetFrame(MplexStreamId(true, id), Frame.ResetInitiatorTag))
            }

            else -> throw ProtocolViolationException("Unknown Mplex tag type '$tag'")
        }
    }

    private suspend fun readUnsignedVarInt(channel: ByteReadChannel): Result<ULong> {
        var value = 0uL
        var scale = 0
        var index = 0
        while (true) {
            val uByte = try {
                channel.readByte().toUByte()
            } catch (_: Exception) {
                return Err(Errors.EndOfStream)
            }

            if ((index == 8 && uByte >= 0x80u) || index >= 9) {
                // this is the 9th and last byte we're willing to read, but it
                // signals there's more (1 in MSB).
                // or this is the >= 10th byte, and for some reason we're still here.
                throw ProtocolViolationException("varints larger than uint63 are not supported")
            }
            if (uByte < 0x80u) {
                if (uByte == 0u.toUByte() && scale > 0) {
                    throw ProtocolViolationException("varint not minimally encoded")
                }
                return Ok(value or (uByte.toULong() shl scale))
            }
            value = value or ((uByte and 0x7fu).toULong() shl scale)
            scale += 7
            index++
        }
    }
}
