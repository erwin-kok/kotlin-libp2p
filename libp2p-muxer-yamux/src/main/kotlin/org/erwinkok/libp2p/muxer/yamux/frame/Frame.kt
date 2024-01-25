// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.yamux.frame

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.core.Closeable
import org.erwinkok.libp2p.core.network.readUnsignedVarInt
import org.erwinkok.libp2p.core.network.writeUnsignedVarInt
import org.erwinkok.libp2p.muxer.yamux.MplexStreamId
import org.erwinkok.result.Err
import org.erwinkok.result.Result
import org.erwinkok.result.getOrElse

sealed class Frame(val streamId: MplexStreamId) : Closeable {
    val initiator: Boolean get() = streamId.initiator
    val id: Long get() = streamId.id
    abstract val type: Int
    abstract suspend fun writeSelf(channel: ByteWriteChannel)

    companion object {
        const val NewStreamTag = 0
        const val MessageReceiverTag = 1
        const val MessageInitiatorTag = 2
        const val CloseReceiverTag = 3
        const val CloseInitiatorTag = 4
        const val ResetReceiverTag = 5
        const val ResetInitiatorTag = 6
    }
}

internal suspend fun ByteWriteChannel.writeMplexFrame(frame: Frame) {
    val header = (frame.streamId.id shl 3) or frame.type.toLong()
    writeUnsignedVarInt(header)
    frame.writeSelf(this)
}

internal suspend fun ByteReadChannel.readMplexFrame(): Result<Frame> {
    val header = readUnsignedVarInt()
        .getOrElse { return Err(it) }
    val tag = (header and 0x7uL).toInt()
    val id = (header shr 3).toLong()
    return when (tag) {
        Frame.NewStreamTag -> readNewStreamFrame(id)
        Frame.MessageReceiverTag -> readMessageFrame(MplexStreamId(false, id))
        Frame.MessageInitiatorTag -> readMessageFrame(MplexStreamId(true, id))
        Frame.CloseReceiverTag -> readCloseFrame(MplexStreamId(false, id))
        Frame.CloseInitiatorTag -> readCloseFrame(MplexStreamId(true, id))
        Frame.ResetReceiverTag -> readResetFrame(MplexStreamId(false, id))
        Frame.ResetInitiatorTag -> readResetFrame(MplexStreamId(true, id))
        else -> Err("Unknown Mplex tag type '$tag'")
    }
}
