// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.testing.mocknet

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.host.Host
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.Network
import org.erwinkok.libp2p.core.network.NetworkConnection
import org.erwinkok.libp2p.core.peerstore.Peerstore
import org.erwinkok.libp2p.crypto.PrivateKey
import org.erwinkok.libp2p.testing.mocknet.done.Link
import org.erwinkok.libp2p.testing.mocknet.done.LinkMap
import org.erwinkok.libp2p.testing.mocknet.done.LinkOptions
import org.erwinkok.libp2p.testing.mocknet.done.MockLink
import org.erwinkok.libp2p.testing.mocknet.done.MockNet
import org.erwinkok.libp2p.testing.mocknet.done.PeerOptions
import org.erwinkok.result.Result
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class MockNetImpl(
    private val scope: CoroutineScope
) : MockNet, AwaitableClosable {
    private val _context = Job(scope.coroutineContext[Job])
    private val lock = ReentrantLock()
    private val nets = mutableMapOf<PeerId, PeerNet>()
    private val hosts = mutableMapOf<PeerId, Host>()
    private val links = mutableMapOf<PeerId, MutableMap<PeerId, MockLink>>()

    override val jobContext: Job get() = _context

    override fun genPeer(): Result<Host> {
        TODO("Not yet implemented")
    }

    override fun genPeerWithOptions(peerOptions: PeerOptions): Result<Host> {
        TODO("Not yet implemented")
    }

    override fun addPeer(privateKey: PrivateKey, address: InetMultiaddress): Result<Host> {
        TODO("Not yet implemented")
    }

    override fun addPeerWithPeerstore(peerId: PeerId, peerstore: Peerstore): Result<Host> {
        TODO("Not yet implemented")
    }

    override fun addPeerWithOptions(peerId: PeerId, peerOptions: PeerOptions): Result<Host> {
        TODO("Not yet implemented")
    }

    override fun peers(): List<PeerId> {
        TODO("Not yet implemented")
    }

    override fun net(peerId: PeerId): Network {
        TODO("Not yet implemented")
    }

    override fun nets(): List<Network> {
        TODO("Not yet implemented")
    }

    override fun host(peerId: PeerId): Host {
        TODO("Not yet implemented")
    }

    override fun hosts(): List<Host> {
        TODO("Not yet implemented")
    }

    override fun links(): LinkMap {
        TODO("Not yet implemented")
    }

    override fun linksBetweenPeers(a: PeerId, b: PeerId): List<MockLink> {
        TODO("Not yet implemented")
    }

    override fun linksBetweenNets(a: Network, b: Network): List<Link> {
        TODO("Not yet implemented")
    }

    override fun linkPeers(a: PeerId, b: PeerId): Result<Link> {
        TODO("Not yet implemented")
    }

    override fun linkNets(a: Network, b: Network): Result<Link> {
        TODO("Not yet implemented")
    }

    override fun unlink(link: Link): Result<Unit> {
        TODO("Not yet implemented")
    }

    override fun unlinkPeers(a: PeerId, b: PeerId): Result<Unit> {
        TODO("Not yet implemented")
    }

    override fun unlinkNets(a: Network, b: Network): Result<Unit> {
        TODO("Not yet implemented")
    }

    override fun setLinkDefaults(linkOptions: LinkOptions) {
        TODO("Not yet implemented")
    }

    override fun linkDefaults(): LinkOptions {
        TODO("Not yet implemented")
    }

    override fun connectPeers(a: PeerId, b: PeerId): Result<NetworkConnection> {
        TODO("Not yet implemented")
    }

    override fun connectNets(a: Network, b: Network): Result<NetworkConnection> {
        TODO("Not yet implemented")
    }

    override fun disconnectPeers(a: PeerId, b: PeerId): Result<Unit> {
        TODO("Not yet implemented")
    }

    override fun disconnectNets(a: Network, b: Network): Result<Unit> {
        TODO("Not yet implemented")
    }

    override fun linkAll(): Result<Unit> {
        TODO("Not yet implemented")
    }

    override fun connectAllButSelf(): Result<Unit> {
        TODO("Not yet implemented")
    }

    override fun close() {
        lock.withLock {
            hosts.forEach { it.value.close() }
            nets.forEach { it.value.close() }
        }
        _context.complete()
    }
}
