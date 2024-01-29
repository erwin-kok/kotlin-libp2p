// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.yamux

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.BytePacketBuilder
import io.ktor.utils.io.core.writeInt
import io.ktor.utils.io.core.writeShort
import mu.KotlinLogging
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result

private val logger = KotlinLogging.logger {}

class YamuxHeader(val type: FrameType, val flags: Flags, val streamId: Int, val length: Int) {
    override fun toString(): String {
        return "type=$type, flags: $flags, id=$streamId, length=$length"
    }
}

internal suspend fun ByteReadChannel.readYamuxHeader(): Result<YamuxHeader> {
    val version = readByte()
    val type = readByte()
    val flags = readShort()
    val streamId = readInt()
    val length = readInt()
    if (version != YamuxConst.protoVersion) {
        logger.error { "yamux: Invalid protocol version $version" }
        return Err(YamuxConst.errInvalidVersion)
    }
    return Result.zip(
        { FrameType.fromInt(type) },
        { Flags.fromShort(flags) },
    ) { t, f ->
        Ok(YamuxHeader(t, f, streamId, length))
    }
}

internal fun BytePacketBuilder.writeYamuxHeader(header: YamuxHeader) {
    writeByte(YamuxConst.protoVersion)
    writeByte(header.type.code)
    writeShort(header.flags.code)
    writeInt(header.streamId)
    writeInt(header.length)
}
