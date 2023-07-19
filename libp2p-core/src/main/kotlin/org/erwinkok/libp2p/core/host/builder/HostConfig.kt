// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.host.builder

import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.network.securitymuxer.SecureTransportFactory
import org.erwinkok.libp2p.core.network.streammuxer.StreamMuxerTransportFactory
import org.erwinkok.libp2p.core.network.transport.TransportFactory

class HostConfig {
    var insecure = false
    var localIdentity: LocalIdentity? = null
    val streamMuxerTransportFactories = mutableListOf<StreamMuxerTransportFactory>()
    val securityTransportFactories = mutableListOf<SecureTransportFactory>()
    val transportFactories = mutableListOf<TransportFactory>()
}