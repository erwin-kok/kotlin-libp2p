// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.testing.mocknet

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.event.EventBus
import org.erwinkok.libp2p.core.event.EvtPeerConnectednessChanged
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.Connectedness
import org.erwinkok.libp2p.core.network.Direction
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.Network
import org.erwinkok.libp2p.core.network.NetworkConnection
import org.erwinkok.libp2p.core.network.Stream
import org.erwinkok.libp2p.core.network.StreamHandler
import org.erwinkok.libp2p.core.network.Subscriber
import org.erwinkok.libp2p.core.network.connectiongater.ConnectionGater
import org.erwinkok.libp2p.core.network.transport.Transport
import org.erwinkok.libp2p.core.peerstore.Peerstore
import org.erwinkok.libp2p.core.peerstore.Peerstore.Companion.PermanentAddrTTL
import org.erwinkok.libp2p.core.resourcemanager.NullResourceManager
import org.erwinkok.libp2p.core.resourcemanager.ResourceManager
import org.erwinkok.libp2p.testing.mocknet.done.MockConnection
import org.erwinkok.libp2p.testing.mocknet.done.MockLink
import org.erwinkok.libp2p.testing.mocknet.done.MockNet
import org.erwinkok.libp2p.testing.mocknet.done.PeerOptions
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.getOrElse
import org.erwinkok.result.onFailure
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

class PeerNet private constructor(
    val scope: CoroutineScope,
    val mocknet: MockNet,
    val peer: PeerId,
    val eventBus: EventBus,
    val connectionGater: ConnectionGater?,
    override val peerstore: Peerstore,
) : AwaitableClosable, Network {
    private val _context = Job(scope.coroutineContext[Job])
    private val lock = ReentrantLock()
    private val connectionsByPeer = mutableMapOf<PeerId, Set<MockConnection>>()
    private val connectionsByLink = mutableMapOf<MockLink, Set<MockConnection>>()
    private val subscribersLock = ReentrantLock()
    private val subscribers = mutableListOf<Subscriber>()

    override val jobContext: Job get() = _context
    override val localPeerId: PeerId
        get() = peer
    override val resourceManager: ResourceManager
        get() = NullResourceManager

    override var streamHandler: StreamHandler? = null

    override suspend fun dialPeer(peerId: PeerId): Result<NetworkConnection> {
        return connect(peerId)
    }

    override fun closePeer(peerId: PeerId) {
        val connections = lock.withLock {
            connectionsByPeer[peerId]
        }
        connections?.forEach { it.close() }
    }

    override fun connectedness(peerId: PeerId): Connectedness {
        val connections = lock.withLock {
            connectionsByPeer[peerId]
        }
        return if (!connections.isNullOrEmpty()) {
            Connectedness.Connected
        } else {
            Connectedness.NotConnected
        }
    }

    override fun peers(): List<PeerId> {
        return lock.withLock {
            connectionsByPeer.values.flatten().map { it.remoteIdentity.peerId }
        }
    }

    override fun connections(): List<NetworkConnection> {
        return lock.withLock {
            connectionsByPeer.values.flatten()
        }
    }

    override fun connectionsToPeer(peerId: PeerId): List<NetworkConnection> {
        return lock.withLock {
            connectionsByPeer[peerId]?.toList() ?: listOf()
        }
    }

    override fun subscribe(subscriber: Subscriber) {
        subscribersLock.withLock {
            subscribers.add(subscriber)
        }
    }

    override fun unsubscribe(subscriber: Subscriber) {
        subscribersLock.withLock {
            subscribers.remove(subscriber)
        }
    }

    override suspend fun newStream(peerId: PeerId): Result<Stream> {
        val connection = dialPeer(peerId)
            .getOrElse { return Err(it) }
        return connection.newStream()
    }

    override fun addListener(address: InetMultiaddress): Result<Unit> {
        runBlocking {
            peerstore.addAddress(localPeerId, address, PermanentAddrTTL)
        }
        return Ok(Unit)
    }

    override fun removeListener(address: InetMultiaddress): Result<Unit> {
        return Ok(Unit)
    }

    override fun listenAddresses(): List<InetMultiaddress> {
        return runBlocking {
            peerstore.addresses(localPeerId)
        }
    }

    override fun interfaceListenAddresses(): Result<List<InetMultiaddress>> {
        return Ok(listenAddresses())
    }

    override fun close() {
        lock.withLock {
            connectionsByPeer.values.flatten().forEach { it.close() }
        }
        peerstore.close()
        _context.complete()
    }

    override fun toString(): String {
        lock.withLock {
            return "<mock.peernet $peer - ${connectionsByPeer.values.size} conns>"
        }
    }

    override fun addTransport(transport: Transport): Result<Unit> = ErrNotSupported

    override fun transportForListening(address: InetMultiaddress): Result<Transport> = ErrNotSupported

    override fun transportForDialing(address: InetMultiaddress): Result<Transport> = ErrNotSupported

    internal fun notifyAll(notify: (Subscriber) -> Unit) {
        subscribersLock.withLock {
            subscribers.toList()
        }.forEach { notify(it) }
    }

    internal suspend fun handleNewStream(stream: MockStream) {
        val sh = lock.withLock {
            streamHandler
        }
        if (sh != null) {
            scope.launch(_context + CoroutineName("mock-peernet-$peer")) {
                sh(stream)
            }
        } else {
            logger.warn { "No StreamHandler registered. Not able to manage stream. Reset." }
            stream.reset()
        }
    }

    private suspend fun connect(p: PeerId): Result<NetworkConnection> {
        if (p == peer) {
            return Err("attempted to dial self $peer")
        }
        // first, check if we already have live connections
        lock.withLock {
            val connection = connectionsByPeer[p]?.firstOrNull()
            if (connection != null) {
                return Ok(connection)
            }
        }
        val gater = connectionGater
        if (gater != null && gater.interceptPeerDial(peer)) {
            return Err("gater disallowed outbound connection to peer $p")
        }
        logger.debug { "Dialing $peer --> $p" }
        val links = mocknet.linksBetweenPeers(peer, p)
        if (links.isEmpty()) {
            return Err("$peer cannot connect to $p")
        }
        val link = links[Random.nextInt(links.size)]
        return openConnection(p, link)
    }

    private suspend fun openConnection(p: PeerId, link: MockLink): Result<NetworkConnection> {
        val (lc, rc) = link.newConnectionPair(this)
            .getOrElse { return Err(it) }
        addConnectionPair(this, rc.localNet, lc, rc)
        logger.debug { "$localPeerId opening connection to ${lc.remoteIdentity.peerId}" }
        val gater = connectionGater
        if (gater != null && !gater.interceptAddressDial(lc.remoteIdentity.peerId, lc.remoteAddress)) {
            lc.close()
            rc.close()
            return Err("${lc.localIdentity} rejected dial to ${lc.remoteIdentity} on addr ${lc.remoteAddress}")
        }
        val remoteGater = rc.localNet.connectionGater
        if (remoteGater != null && !remoteGater.interceptAccept(rc)) {
            lc.close()
            rc.close()
            return Err("${rc.localIdentity} rejected connection from ${rc.remoteIdentity}")
        }
        checkSecureAndUpgrade(Direction.DirOutbound, gater, lc)
            .onFailure {
                lc.close()
                rc.close()
                return Err(it)
            }
        checkSecureAndUpgrade(Direction.DirInbound, remoteGater, rc)
            .onFailure {
                lc.close()
                rc.close()
                return Err(it)
            }
        scope.launch {
            rc.localNet.remoteOpenedConnection(rc)
        }
        addConnection(lc)
        return Ok(lc)
    }

    private fun checkSecureAndUpgrade(dir: Direction, gater: ConnectionGater?, connection: MockConnection): Result<Unit> {
        if (gater == null) {
            return Ok(Unit)
        }
        if (!gater.interceptSecured(dir, connection.remoteIdentity.peerId, connection)) {
            return Err("${connection.localIdentity} rejected secure handshake with ${connection.remoteIdentity}")
        }
        TODO("Not yet implemented")
    }

    private fun addConnectionPair(pn1: PeerNet, pn2: PeerNet, c1: MockConnection, c2: MockConnection) {
        TODO("Not yet implemented")
    }

    private fun remoteOpenedConnection(c: MockConnection) {
        logger.debug { "$localPeerId accepting connection from ${c.remoteIdentity.peerId}" }
        addConnection(c)
    }

    private fun addConnection(c: MockConnection) {
        notifyAll { subscriber -> subscriber.connected(this, c) }
        eventBus.tryPublish(EvtPeerConnectednessChanged(c.remoteIdentity.peerId, Connectedness.Connected))
        c.notifyLock.lock()
    }

    internal fun removeConnection(c: MockConnection) {
        TODO("Not yet implemented")
    }

    companion object {
        val ErrNotSupported = Err("")
        fun create(scope: CoroutineScope, mocknet: MockNet, peer: PeerId, options: PeerOptions, eventBus: EventBus): Result<PeerNet> {
            return Ok(PeerNet(scope, mocknet, peer, eventBus, options.connectionGater, options.peerstore))
        }
    }
}
