// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.network

import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.transport.Transport
import org.erwinkok.libp2p.core.peerstore.Peerstore
import org.erwinkok.libp2p.core.resourcemanager.ResourceManager
import org.erwinkok.result.Result

typealias StreamHandler = suspend (Stream) -> Unit

interface Network : AwaitableClosable {
    val peerstore: Peerstore
    val localPeerId: PeerId
    val resourceManager: ResourceManager
    var streamHandler: StreamHandler?

    fun addTransport(transport: Transport): Result<Unit>
    fun transportForListening(address: InetMultiaddress): Result<Transport>
    fun transportForDialing(address: InetMultiaddress): Result<Transport>
    suspend fun dialPeer(peerId: PeerId): Result<NetworkConnection>
    fun closePeer(peerId: PeerId)
    fun connectedness(peerId: PeerId): Connectedness
    fun peers(): List<PeerId>
    fun connections(): List<NetworkConnection>
    fun connectionsToPeer(peerId: PeerId): List<NetworkConnection>
    fun subscribe(subscriber: Subscriber)
    fun unsubscribe(subscriber: Subscriber)
    suspend fun newStream(peerId: PeerId): Result<Stream>
    fun addListener(address: InetMultiaddress): Result<Unit>

    // ListenAddresses returns a list of addresses at which this network listens.
    fun listenAddresses(): List<InetMultiaddress>

    // InterfaceListenAddresses returns a list of addresses at which this network
    // listens. It expands "any interface" addresses (/ip4/0.0.0.0, /ip6/::) to
    // use the known local interfaces.
    fun interfaceListenAddresses(): Result<List<InetMultiaddress>>
}
