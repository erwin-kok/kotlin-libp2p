// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.yamux.frame

internal class WindowUpdateFrame(flags: Short, streamId: Int, val windowSize: Int) : Frame(flags, streamId) {
    override val type: Int
        get() = typeWindowUpdate

    override fun close(): Unit = Unit
}
