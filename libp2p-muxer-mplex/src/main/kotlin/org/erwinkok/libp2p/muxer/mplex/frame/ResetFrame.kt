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

internal class ResetFrame(streamId: MplexStreamId) : Frame(streamId) {
    override val type: Int
        get() {
            return if (streamId.initiator) ResetInitiatorTag else ResetReceiverTag
        }

    override fun close(): Unit = Unit

    override suspend fun writeSelf(channel: ByteWriteChannel) {
        channel.writeUnsignedVarInt(0)
    }
}

internal suspend fun ByteReadChannel.readResetFrame(streamId: MplexStreamId): Result<ResetFrame> {
    return readUnsignedVarInt()
        .toErrorIf({ it != 0uL }, { Error("ResetFrame should not carry data") })
        .map { ResetFrame(streamId) }
}
