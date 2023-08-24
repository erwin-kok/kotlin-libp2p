// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network.swarm

import io.ktor.util.collections.ConcurrentMap
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import mu.KotlinLogging
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.event.EventBus
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.host.Option
import org.erwinkok.libp2p.core.host.Options
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.host.builder.SwarmConfig
import org.erwinkok.libp2p.core.network.Connectedness
import org.erwinkok.libp2p.core.network.Direction
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.Network
import org.erwinkok.libp2p.core.network.Stream
import org.erwinkok.libp2p.core.network.Subscriber
import org.erwinkok.libp2p.core.network.connectiongater.ConnectionGater
import org.erwinkok.libp2p.core.network.transport.Transport
import org.erwinkok.libp2p.core.network.transport.TransportConnection
import org.erwinkok.libp2p.core.peerstore.Peerstore
import org.erwinkok.libp2p.core.resourcemanager.NullResourceManager
import org.erwinkok.libp2p.core.resourcemanager.ResourceManager
import org.erwinkok.multiformat.multistream.MultistreamMuxer
import org.erwinkok.result.Err
import org.erwinkok.result.Error
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.flatMap
import org.erwinkok.result.onFailure
import org.erwinkok.result.onSuccess

private val logger = KotlinLogging.logger {}

fun Options<Swarm>.withConnectionGater(bla: ConnectionGater): Options<Swarm> {
    list.add { it.bla = bla }
    return this
}

fun Option<Swarm>.withConnectionGater(bla: ConnectionGater): Option<Swarm> {
    return { it.bla = bla }
}

class Swarm private constructor(
    val scope: CoroutineScope,
    override val localPeerId: PeerId,
    override val peerstore: Peerstore,
    override val resourceManager: ResourceManager,
    override val multistreamMuxer: MultistreamMuxer<Stream>,
    private val eventBus: EventBus,
    private val connectionGater: ConnectionGater?,
    swarmConfig: SwarmConfig,
) : AwaitableClosable, Network {
    private val _context = Job(scope.coroutineContext[Job])
    private val swarmTransport = SwarmTransport()
    private val swarmListener: SwarmListener
    private val swarmDialer: SwarmDialer
    private val peers = ConcurrentMap<PeerId, NetworkPeer>()
    private val subscribersLock = ReentrantLock()
    private val subscribers = mutableListOf<Subscriber>()

    internal var bla: ConnectionGater? = null

    override val jobContext: Job
        get() = _context

    init {
        swarmDialer = SwarmDialer(scope, swarmTransport, this, peerstore, connectionGater, swarmConfig)
        swarmListener = SwarmListener(scope, this, swarmTransport)
    }

    override fun addTransport(transport: Transport): Result<Unit> {
        return swarmTransport.addTransport(transport)
    }

    override fun transportForListening(address: InetMultiaddress): Result<Transport> {
        return swarmTransport.transportForListening(address)
    }

    override fun transportForDialing(address: InetMultiaddress): Result<Transport> {
        return swarmTransport.transportForDialing(address)
    }

    override suspend fun dialPeer(peerId: PeerId): Result<SwarmConnection> {
        if (connectionGater != null && !connectionGater.interceptPeerDial(peerId)) {
            logger.debug { "gater disallowed outbound connection to peer $peerId" }
            return Err(ErrGaterDisallowedConnection)
        }
        logger.debug { "dialing peer from $localPeerId to $peerId" }
        if (!peerId.validate()) {
            return Err("PeerId is not valid")
        }
        if (peerId == localPeerId) {
            return Err(ErrDialToSelf)
        }
        val swarmConnection = bestConnectionToPeer(peerId)
        if (swarmConnection != null) {
            return Ok(swarmConnection)
        }
        return swarmDialer.dial(peerId)
    }

    override fun closePeer(peerId: PeerId) {
        peers.remove(peerId)?.close()
    }

    override fun connectedness(peerId: PeerId): Connectedness {
        return getPeer(peerId)?.connectedness() ?: Connectedness.NotConnected
    }

    override fun peers(): List<PeerId> {
        return peers.keys.toList()
    }

    override fun connections(): List<SwarmConnection> {
        return peers.values.toList().flatMap { it.connections() }
    }

    override fun connectionsToPeer(peerId: PeerId): List<SwarmConnection> {
        return getPeer(peerId)?.connections() ?: listOf()
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
        return dialPeer(peerId)
            .flatMap { peer -> peer.newStream() }
    }

    override fun addListener(address: InetMultiaddress): Result<Unit> {
        return swarmListener.addListener(address)
    }

    override fun listenAddresses(): List<InetMultiaddress> {
        return swarmListener.listenAddresses()
    }

    override fun interfaceListenAddresses(): Result<List<InetMultiaddress>> {
        return swarmListener.interfaceListenAddresses()
    }

    override fun close() {
        peers.forEach { it.value.close() }
        swarmDialer.close()
        swarmListener.close()
        swarmTransport.close()
        peerstore.close()
        _context.complete()
    }

    internal fun removeConnection(swarmConnection: SwarmConnection) {
        getPeer(swarmConnection.remoteIdentity.peerId)?.removeConnection(swarmConnection)
    }

    internal fun bestConnectionToPeer(peerId: PeerId): SwarmConnection? {
        return getPeer(peerId)?.bestConnectionToPeer()
    }

    private fun getPeer(peerId: PeerId): NetworkPeer? {
        return peers[peerId]
    }

    internal fun getOrCreatePeer(peerId: PeerId): NetworkPeer {
        return peers.computeIfAbsent(peerId) { NetworkPeer(scope, peerId, this, resourceManager, multistreamMuxer) }
    }

    internal fun addConnection(transportConnection: TransportConnection, direction: Direction): Result<SwarmConnection> {
        val peerId = transportConnection.remoteIdentity.peerId
        return if (connectionGater != null) {
            val allow = connectionGater.interceptUpgraded(transportConnection)
            if (!allow) {
                logger.warn { "gater disallows connection from peer $peerId" }
                transportConnection.close()
                Err(ErrGaterDisallowedConnection)
            } else {
                getOrCreatePeer(peerId).addConnection(transportConnection, direction)
                    .onSuccess { notifyAll { subscriber -> subscriber.connected(this, it) } }
            }
        } else {
            getOrCreatePeer(peerId).addConnection(transportConnection, direction)
                .onSuccess { notifyAll { subscriber -> subscriber.connected(this, it) } }
        }
    }

    internal fun notifyAll(notify: (Subscriber) -> Unit) {
        subscribersLock.withLock {
            subscribers.toList()
        }.forEach { notify(it) }
    }

    class Builder(
        private val eventBus: EventBus,
        private val localIdentity: LocalIdentity,
        private val peerstore: Peerstore,
        private val multistreamMuxer: MultistreamMuxer<Stream>,
    ) {
        private var connectionGater: ConnectionGater? = null
        private var resourceManager: ResourceManager? = null
        private var swarmConfig: SwarmConfig? = null

        fun withConnectionGater(connectionGater: ConnectionGater): Builder {
            this.connectionGater = connectionGater
            return this
        }

        fun withResourceManager(resourceManager: ResourceManager): Builder {
            this.resourceManager = resourceManager
            return this
        }

        fun withSwarmConfig(swarmConfig: SwarmConfig): Builder {
            this.swarmConfig = swarmConfig
            return this
        }

        suspend fun build(scope: CoroutineScope): Result<Swarm> {
            peerstore.addLocalIdentity(localIdentity)
                .onFailure { return Err(it) }
            return Ok(
                Swarm(
                    scope,
                    localIdentity.peerId,
                    peerstore,
                    resourceManager ?: NullResourceManager,
                    multistreamMuxer,
                    eventBus,
                    connectionGater,
                    swarmConfig ?: SwarmConfig(),
                ),
            )
        }
    }

    companion object {
        val ErrSwarmClosed = Error("Swarm is closed")
        val ErrDialToSelf = Error("dial to self attempted")
        val ErrGaterDisallowedConnection = Error("gater disallows connection to peer")

        fun builder(eventBus: EventBus, localIdentity: LocalIdentity, peerstore: Peerstore, multistreamMuxer: MultistreamMuxer<Stream>): Builder {
            return Builder(eventBus, localIdentity, peerstore, multistreamMuxer)
        }
    }
}
