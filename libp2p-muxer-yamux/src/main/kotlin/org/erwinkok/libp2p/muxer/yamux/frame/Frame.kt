// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.yamux.frame

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.core.Closeable
import mu.KotlinLogging
import org.erwinkok.libp2p.muxer.yamux.YamuxConst.errInvalidMsgType
import org.erwinkok.libp2p.muxer.yamux.YamuxConst.errInvalidVersion
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result

private val logger = KotlinLogging.logger {}

sealed class Frame(val flags: Short, val id: Int) : Closeable {
    abstract val type: Int

    companion object {
        const val protoVersion = 0.toByte()

        // Data is used for data frames. They are followed by length bytes worth of payload.
        const val typeData = 0

        // WindowUpdate is used to change the window of a given stream. The length indicates the delta update to the window.
        const val typeWindowUpdate = 1

        // Ping is sent as a keep-alive or to measure the RTT. The StreamID and Length value are echoed back in the response.
        const val typePing = 2

        // GoAway is sent to terminate a session. The StreamID should be 0 and the length is an error code.
        const val typeGoAway = 3

        const val SynFlag = 1.toShort()
        const val AckFlag = 2.toShort()
        const val FinFlag = 4.toShort()
        const val RstFlag = 8.toShort()
    }
}

internal suspend fun ByteWriteChannel.writeYamuxFrame(frame: Frame) {
    writeByte(Frame.protoVersion)
    writeByte(frame.type.toByte())
    writeShort(frame.flags)
    when (frame) {
        is DataFrame -> {
            writeInt(frame.packet.remaining.toInt())
            writePacket(frame.packet)
        }

        is WindowUpdateFrame -> {
            writeInt(frame.windowSize)
        }

        is PingFrame -> {
            writeInt(frame.pingId)
        }

        is GoAwayFrame -> {
            writeInt(frame.errorCode)
        }
    }
    flush()
}

internal suspend fun ByteReadChannel.readYamuxFrame(): Result<Frame> {
    val version = readByte()
    val type = readByte()
    val flags = readShort()
    val streamId = readInt()
    val length = readInt()

    if (version != Frame.protoVersion) {
        logger.error { "yamux: Invalid protocol version $version" }
        return Err(errInvalidVersion)
    }

    if (type < Frame.typeData || type > Frame.typeGoAway) {
        return Err(errInvalidMsgType)
    }

    val frame = when (type.toInt()) {
        Frame.typeData -> DataFrame(flags, streamId, readPacket(length))
        Frame.typeWindowUpdate -> WindowUpdateFrame(flags, streamId, length)
        Frame.typePing -> PingFrame(flags, streamId, length)
        Frame.typeGoAway -> GoAwayFrame(flags, streamId, length)
        else -> return Err("Unknown Yamux message type '$type'")
    }
    return Ok(frame)
}


