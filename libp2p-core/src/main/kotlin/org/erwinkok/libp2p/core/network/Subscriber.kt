// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network

interface Subscriber {
    fun listen(network: Network, address: InetMultiaddress) = Unit
    fun listenClose(network: Network, address: InetMultiaddress) = Unit
    fun connected(network: Network, connection: NetworkConnection) = Unit
    fun disconnected(network: Network, connection: NetworkConnection) = Unit
}
