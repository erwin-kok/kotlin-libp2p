// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.mplex.frame

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.core.toByteArray
import io.ktor.utils.io.writeFully
import org.erwinkok.libp2p.core.network.readUnsignedVarInt
import org.erwinkok.libp2p.core.network.writeUnsignedVarInt
import org.erwinkok.libp2p.muxer.mplex.MplexStreamId
import org.erwinkok.result.Result
import org.erwinkok.result.map

internal class NewStreamFrame(id: Long, val name: String) : Frame(MplexStreamId(true, id)) {
    override val type: Int get() = NewStreamTag

    override fun close(): Unit = Unit

    override suspend fun writeSelf(channel: ByteWriteChannel) {
        channel.writeUnsignedVarInt(name.length)
        channel.writeFully(name.toByteArray())
    }
}

internal suspend fun ByteReadChannel.readNewStreamFrame(id: Long): Result<NewStreamFrame> {
    return readUnsignedVarInt()
        .map { length -> readPacket(length.toInt()) }
        .map { data -> NewStreamFrame(id, String(data.readBytes())) }
}
