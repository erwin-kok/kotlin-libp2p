// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.testing.mocknet.done

import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.host.Host
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.Network
import org.erwinkok.libp2p.core.network.NetworkConnection
import org.erwinkok.libp2p.core.peerstore.Peerstore
import org.erwinkok.libp2p.crypto.PrivateKey
import org.erwinkok.libp2p.testing.mocknet.MockNetImpl
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.getOrElse
import org.erwinkok.result.onFailure

interface MockNet : AwaitableClosable {
    fun genPeer(): Result<Host>
    fun genPeerWithOptions(peerOptions: PeerOptions): Result<Host>
    fun addPeer(privateKey: PrivateKey, address: InetMultiaddress): Result<Host>
    fun addPeerWithPeerstore(peerId: PeerId, peerstore: Peerstore): Result<Host>
    fun addPeerWithOptions(peerId: PeerId, peerOptions: PeerOptions): Result<Host>
    fun peers(): List<PeerId>
    fun net(peerId: PeerId): Network
    fun nets(): List<Network>
    fun host(peerId: PeerId): Host
    fun hosts(): List<Host>
    fun links(): LinkMap
    fun linksBetweenPeers(a: PeerId, b: PeerId): List<MockLink>
    fun linksBetweenNets(a: Network, b: Network): List<Link>
    fun linkPeers(a: PeerId, b: PeerId): Result<Link>
    fun linkNets(a: Network, b: Network): Result<Link>
    fun unlink(link: Link): Result<Unit>
    fun unlinkPeers(a: PeerId, b: PeerId): Result<Unit>
    fun unlinkNets(a: Network, b: Network): Result<Unit>
    fun setLinkDefaults(linkOptions: LinkOptions)
    fun linkDefaults(): LinkOptions
    fun connectPeers(a: PeerId, b: PeerId): Result<NetworkConnection>
    fun connectNets(a: Network, b: Network): Result<NetworkConnection>
    fun disconnectPeers(a: PeerId, b: PeerId): Result<Unit>
    fun disconnectNets(a: Network, b: Network): Result<Unit>
    fun linkAll(): Result<Unit>
    fun connectAllButSelf(): Result<Unit>

    companion object {
        fun withNPeers(n: Int): Result<MockNet> {
            val mmocknet = MockNetImpl()
            repeat(n) {
                mmocknet.genPeer().onFailure { return Err(it) }
            }
            return Ok(mmocknet)
        }

        fun fullMeshLinked(n: Int): Result<MockNet> {
            val mocknet = withNPeers(n)
                .getOrElse { return Err(it) }
            mocknet.linkAll()
                .onFailure { return Err(it) }
            return Ok(mocknet)
        }

        fun fullMeshConnected(n: Int): Result<MockNet> {
            val mocknet = fullMeshLinked(n)
                .getOrElse { return Err(it) }
            mocknet.connectAllButSelf()
                .onFailure { return Err(it) }
            return Ok(mocknet)
        }
    }
}
