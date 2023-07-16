// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.mplex.frame

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.core.ByteReadPacket
import org.erwinkok.libp2p.core.network.readUnsignedVarInt
import org.erwinkok.libp2p.core.network.writeUnsignedVarInt
import org.erwinkok.libp2p.muxer.mplex.MplexStreamId
import org.erwinkok.result.Result
import org.erwinkok.result.map

internal class MessageFrame(streamId: MplexStreamId, val packet: ByteReadPacket) : Frame(streamId) {
    override val type: Int
        get() {
            return if (streamId.initiator) MessageInitiatorTag else MessageReceiverTag
        }

    override fun close() {
        packet.close()
    }

    override suspend fun writeSelf(channel: ByteWriteChannel) {
        channel.writeUnsignedVarInt(packet.remaining.toInt())
        channel.writePacket(packet)
    }
}

internal suspend fun ByteReadChannel.readMessageFrame(streamId: MplexStreamId): Result<MessageFrame> {
    return readUnsignedVarInt()
        .map { length -> readPacket(length.toInt()) }
        .map { packet -> MessageFrame(streamId, packet) }
}
