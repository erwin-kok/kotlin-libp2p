// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network.swarm

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.base.closableLockedList
import org.erwinkok.libp2p.core.base.withLock
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.host.RemoteIdentity
import org.erwinkok.libp2p.core.network.ConnectionStatistics
import org.erwinkok.libp2p.core.network.Direction
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.NetworkConnection
import org.erwinkok.libp2p.core.network.Stream
import org.erwinkok.libp2p.core.network.StreamHandler
import org.erwinkok.libp2p.core.network.StreamResetException
import org.erwinkok.libp2p.core.network.streammuxer.MuxedStream
import org.erwinkok.libp2p.core.network.transport.TransportConnection
import org.erwinkok.libp2p.core.resourcemanager.ConnectionScope
import org.erwinkok.libp2p.core.resourcemanager.ResourceManager
import org.erwinkok.libp2p.core.resourcemanager.StreamManagementScope
import org.erwinkok.result.Err
import org.erwinkok.result.Error
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.flatMap
import org.erwinkok.result.map
import org.erwinkok.result.onFailure
import org.erwinkok.result.onSuccess
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.minutes

private val logger = KotlinLogging.logger {}

class SwarmConnection(
    scope: CoroutineScope,
    private val transportConnection: TransportConnection,
    private val resourceManager: ResourceManager,
    private val streamHandler: StreamHandler?,
    private val identifier: Long,
    private val onClose: (SwarmConnection) -> Unit,
) : AwaitableClosable, NetworkConnection {
    private val _context = Job()
    private val _streams = closableLockedList<SwarmStream>()
    private val nextStreamId = AtomicLong(0)

    override val jobContext: Job get() = _context
    override val id: String
        get() = String.format("%s-%d", transportConnection.remoteIdentity.toString().substring(0, 10), identifier)
    override val streams: List<Stream>
        get() = _streams.toList()
    override val localAddress: InetMultiaddress
        get() = transportConnection.localAddress
    override val localIdentity: LocalIdentity
        get() = transportConnection.localIdentity
    override val remoteAddress: InetMultiaddress
        get() = transportConnection.remoteAddress
    override val remoteIdentity: RemoteIdentity
        get() = transportConnection.remoteIdentity
    override val statistic: ConnectionStatistics
        get() = transportConnection.statistic
    override val connectionScope: ConnectionScope
        get() = transportConnection.connectionScope

    val nrOfStreams: Int
        get() = streams.size
    val isDirectConnection: Boolean
        get() = !transportConnection.transport.proxy
    val isTransient: Boolean
        get() = statistic.transient

    init {
        scope.launch(_context + CoroutineName("swarm-connection-$id")) {
            while (!transportConnection.isClosed) {
                try {
                    transportConnection.acceptStream()
                        .onSuccess { muxedStream ->
                            val sh = streamHandler
                            if (sh != null) {
                                resourceManager.openStream(remoteIdentity.peerId, Direction.DirInbound)
                                    .flatMap { streamScope -> addStream(muxedStream, Direction.DirInbound, streamScope) }
                                    .onSuccess { stream -> sh(stream) }
                            } else {
                                logger.warn { "No StreamHandler registered. Not able to manage stream. Reset." }
                                muxedStream.reset()
                            }
                        }
                } catch (e: StreamResetException) {
                    // The stream was reset. That's fine here.
                    // We just continue accepting and handling a new stream.
                }
            }
        }.invokeOnCompletion {
            close()
        }
    }

    override suspend fun newStream(name: String?): Result<Stream> {
        return resourceManager.openStream(remoteIdentity.peerId, Direction.DirOutbound)
            .map { scope ->
                return transportConnection
                    .openStream(name)
                    .flatMap { addStream(it, Direction.DirOutbound, scope) }
                    .onFailure { scope.done() }
            }
    }

    private fun removeStream(stream: SwarmStream) {
        _streams.withLock {
            transportConnection.statistic.numberOfStreams--
            _streams.remove(stream)
        }
        stream.streamScope.done()
    }

    override fun toString(): String {
        return buildString {
            append("<SwarmConnection[${transportConnection.transport}]")
            append("${transportConnection.localAddress} (${transportConnection.localIdentity})")
            append(" <-> ")
            append("${transportConnection.remoteAddress} (${transportConnection.remoteIdentity.peerId})")
            append(")>")
            if (isClosed) {
                append(" (CLOSED)")
            }
        }
    }

    override fun close() {
        logger.info { "Closing NetworkConnection $this" }
        transportConnection.close()
        streams.forEach { it.reset() }
        _context.complete()
        onClose(this)
    }

    private fun addStream(muxedStream: MuxedStream, direction: Direction, streamScope: StreamManagementScope): Result<Stream> {
        _streams.withLock {
            if (_context.isCancelled) {
                muxedStream.reset()
                logger.error { "SwarmConnection closed." }
                return Err(ErrConnectionClosed)
            }
            val statistics = ConnectionStatistics()
            statistics.direction = direction
            statistics.opened = Instant.now()
            val stream = SwarmStream(
                muxedStream,
                this,
                nextStreamId(),
                streamScope,
                statistics,
                {
                    removeStream(it)
                },
            )
            statistics.numberOfStreams++
            _streams.add(stream)
            return Ok(stream)
        }
    }

    private fun nextStreamId(): Long {
        return nextStreamId.addAndGet(1)
    }

    companion object {
        val NegotiationTimeout = 1.minutes
        val ErrConnectionClosed = Error("connection closed")
    }
}
