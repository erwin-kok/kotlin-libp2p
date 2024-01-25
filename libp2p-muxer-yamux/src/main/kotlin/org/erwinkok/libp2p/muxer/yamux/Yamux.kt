// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.yamux

import org.erwinkok.libp2p.core.host.builder.HostDsl
import org.erwinkok.libp2p.core.host.builder.MuxersBuilder

@HostDsl
fun MuxersBuilder.yamux() {
    config.streamMuxerTransportFactories.add(YamuxStreamMuxerTransport)
}
