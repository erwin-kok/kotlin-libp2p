// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.yamux

import org.erwinkok.result.Error

object YamuxConst {
    // initialStreamWindow is the initial stream window size.
    // It's not an implementation choice, the value defined in the specification.
    val initialStreamWindow: UInt = 256u * 1024u
    val maxStreamWindow: UInt = 16u * 1024u * 1024u

    val errTimeout = Error("Timeout occurred")
    val errShutdown = Error("Shutdown")
}
