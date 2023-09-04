// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.testing.mocknet.done

import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.Network

typealias LinkMap = Map<PeerId, Map<PeerId, Map<String, Any>>>

interface Link {
    var options: LinkOptions
    fun networks(): List<Network>
    fun peers(): List<PeerId>
}
