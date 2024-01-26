// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.yamux.frame

internal class GoAwayFrame(flags: Short, streamId: Int, val errorCode: Int) : Frame(flags, streamId) {
    override val type: Int
        get() = typeGoAway

    override fun close(): Unit = Unit
}
