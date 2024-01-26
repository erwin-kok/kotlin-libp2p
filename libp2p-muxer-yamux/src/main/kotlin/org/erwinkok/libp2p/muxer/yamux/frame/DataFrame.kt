// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.yamux.frame

import io.ktor.utils.io.core.ByteReadPacket

internal class DataFrame(flags: Short, streamId: Int, val packet: ByteReadPacket) : Frame(flags, streamId) {
    override val type: Int
        get() = typeData

    override fun close() {
        packet.close()
    }
}
