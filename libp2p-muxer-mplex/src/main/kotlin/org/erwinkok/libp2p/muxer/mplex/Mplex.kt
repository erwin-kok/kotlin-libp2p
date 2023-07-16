// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.mplex

import org.erwinkok.libp2p.core.host.builder.HostDsl
import org.erwinkok.libp2p.core.host.builder.MuxersBuilder

@HostDsl
fun MuxersBuilder.mplex() {
    config.streamMuxerTransportFactories.add(MplexStreamMuxerTransport)
}
