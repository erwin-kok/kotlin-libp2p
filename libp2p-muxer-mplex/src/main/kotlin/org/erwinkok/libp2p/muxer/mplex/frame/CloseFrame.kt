// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.mplex.frame

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import org.erwinkok.libp2p.core.network.readUnsignedVarInt
import org.erwinkok.libp2p.core.network.writeUnsignedVarInt
import org.erwinkok.libp2p.muxer.mplex.MplexStreamId
import org.erwinkok.result.Error
import org.erwinkok.result.Result
import org.erwinkok.result.map
import org.erwinkok.result.toErrorIf

internal class CloseFrame(streamId: MplexStreamId) : Frame(streamId) {
    override val type: Int
        get() {
            return if (streamId.initiator) CloseInitiatorTag else CloseReceiverTag
        }

    override fun close(): Unit = Unit

    override suspend fun writeSelf(channel: ByteWriteChannel) {
        channel.writeUnsignedVarInt(0)
    }
}

internal suspend fun ByteReadChannel.readCloseFrame(streamId: MplexStreamId): Result<CloseFrame> {
    return readUnsignedVarInt()
        .toErrorIf({ it != 0uL }, { Error("CloseFrame should not carry data") })
        .map { CloseFrame(streamId) }
}
