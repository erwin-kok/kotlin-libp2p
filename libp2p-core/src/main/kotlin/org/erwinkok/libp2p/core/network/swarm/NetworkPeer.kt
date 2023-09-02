// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network.swarm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.base.closableLockedList
import org.erwinkok.libp2p.core.base.withLock
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.Connectedness
import org.erwinkok.libp2p.core.network.Direction
import org.erwinkok.libp2p.core.network.Stream
import org.erwinkok.libp2p.core.network.StreamHandler
import org.erwinkok.libp2p.core.network.transport.TransportConnection
import org.erwinkok.libp2p.core.resourcemanager.ResourceManager
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

class NetworkPeer(
    val scope: CoroutineScope,
    val peerId: PeerId,
    private val swarm: Swarm,
    private val resourceManager: ResourceManager,
    private val streamHandler: StreamHandler?,
) : AwaitableClosable {
    private val _context = SupervisorJob(scope.coroutineContext[Job])

    private val connections = closableLockedList<SwarmConnection>()
    private val nextConnectionId = AtomicLong(0)

    override val jobContext: Job
        get() = _context

    fun addConnection(transportConnection: TransportConnection, direction: Direction): Result<SwarmConnection> {
        val statistics = transportConnection.statistic
        statistics.direction = direction
        statistics.opened = Instant.now()
        connections.withLock {
            val connection = SwarmConnection(scope, transportConnection, swarm, resourceManager, streamHandler, nextConnectionId())
            connections.add(connection)
            return Ok(connection)
        }
    }

    fun removeConnection(connection: SwarmConnection) {
        connections.remove(connection)
    }

    fun connections(): List<SwarmConnection> {
        return connections.toList()
    }

    fun connectedness(): Connectedness {
        return if (bestConnectionToPeer() != null) Connectedness.Connected else Connectedness.NotConnected
    }

    fun bestConnectionToPeer(): SwarmConnection? {
        connections.withLock {
            var bestConnection: SwarmConnection? = null
            for (swarmConnection in connections) {
                if (!swarmConnection.isClosed && (bestConnection == null || isBetterConnection(swarmConnection, bestConnection))) {
                    bestConnection = swarmConnection
                }
            }
            return bestConnection
        }
    }

    private fun nextConnectionId(): Long {
        return nextConnectionId.addAndGet(1)
    }

    suspend fun newStream(name: String? = null): Result<Stream> {
        val swarmConnection = bestConnectionToPeer() ?: return Err("No connections to peer")
        return swarmConnection.newStream(name)
    }

    override fun close() {
        connections.close()
        _context.complete()
    }

    private fun isBetterConnection(a: SwarmConnection, b: SwarmConnection): Boolean {
        // If one is transient and not the other, prefer the non-transient connection.
        val aTransient = a.isTransient
        val bTransient = b.isTransient
        if (aTransient != bTransient) {
            return !aTransient
        }

        // If one is direct and not the other, prefer the direct connection.
        val aDirect = a.isDirectConnection
        val bDirect = b.isDirectConnection
        if (aDirect != bDirect) {
            return aDirect
        }

        // Otherwise, prefer the connection with more open streams.
        val aLen = a.nrOfStreams
        val bLen = b.nrOfStreams
        return if (aLen != bLen) {
            aLen > bLen
        } else {
            true
        }
    }
}
