// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.transport.tcp

import org.erwinkok.libp2p.core.host.builder.HostDsl
import org.erwinkok.libp2p.core.host.builder.TransportsBuilder
import org.erwinkok.libp2p.transport.tcp.TcpTransport.TcpTransportFactory

@HostDsl
fun TransportsBuilder.tcp() {
    config.transportFactories.add(TcpTransportFactory)
}
