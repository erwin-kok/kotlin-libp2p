// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.network.connectiongater

import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.Direction
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.MultiaddressConnection
import org.erwinkok.libp2p.core.network.transport.TransportConnection

interface ConnectionGater {
    fun interceptPeerDial(peerId: PeerId): Boolean
    fun interceptAddressDial(peerId: PeerId, multiaddress: InetMultiaddress): Boolean
    fun interceptAccept(connection: MultiaddressConnection): Boolean
    fun interceptSecured(direction: Direction, peerId: PeerId, connection: MultiaddressConnection): Boolean
    fun interceptUpgraded(connection: TransportConnection): Boolean
}
