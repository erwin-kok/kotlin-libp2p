// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.yamux.frame

internal class PingFrame(flags: Short, streamId: Int, val pingId: Int) : Frame(flags, streamId) {
    override val type: Int
        get() = typePing

    override fun close(): Unit = Unit
}
