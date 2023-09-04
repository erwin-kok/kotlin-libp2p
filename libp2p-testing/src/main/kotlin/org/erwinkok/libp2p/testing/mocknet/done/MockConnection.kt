// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.testing.mocknet.done

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import mu.KotlinLogging
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.host.RemoteIdentity
import org.erwinkok.libp2p.core.network.ConnectionStatistics
import org.erwinkok.libp2p.core.network.Direction
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.NetworkConnection
import org.erwinkok.libp2p.core.network.Stream
import org.erwinkok.libp2p.core.network.address.IpUtil
import org.erwinkok.libp2p.core.resourcemanager.ConnectionScope
import org.erwinkok.libp2p.core.resourcemanager.NullScope
import org.erwinkok.libp2p.testing.mocknet.MockStream
import org.erwinkok.libp2p.testing.mocknet.PeerNet
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.map
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val logger = KotlinLogging.logger {}

class MockConnection private constructor(
    scope: CoroutineScope,
    val localNet: PeerNet,                                  // --> net
    private val remoteNet: PeerNet,
    private val link: MockLink,
    override val localIdentity: LocalIdentity,
    override val remoteIdentity: RemoteIdentity,
    override val localAddress: InetMultiaddress,
    override val remoteAddress: InetMultiaddress,
    override val statistic: ConnectionStatistics,
) : AwaitableClosable, NetworkConnection {
    private val _context = Job(scope.coroutineContext[Job])
    private val _id = connectionCounter.getAndIncrement()
    private val _streamsLock = ReentrantLock()
    private val _streams = mutableListOf<MockStream>()
    val notifyLock = ReentrantLock()

    lateinit var reverseConnection: MockConnection                  // --> rconn        counterpart

    override val jobContext: Job
        get() = _context

    override val id: String
        get() = "$_id"

    override val streams: List<Stream>
        get() = _streamsLock.withLock { _streams.toList() }

    override val connectionScope: ConnectionScope
        get() = NullScope.NullScope

    override suspend fun newStream(name: String?): Result<Stream> {
        logger.debug { "MockConnection.NewStream ($name): ${localNet.peer} --> ${remoteNet.peer}" }
        return openStream(name)
    }

    override fun close() {
        reverseConnection.close()
        _streamsLock.withLock {
            _streams.forEach { it.reset() }
            _streams.clear()
        }
        localNet.removeConnection(this)
        _context.complete()
    }

    internal fun addStream(stream: MockStream) {
        _streamsLock.withLock {
            stream.connection = this
            _streams.add(stream)
        }
    }

    internal fun removeStream(stream: MockStream) {
        _streamsLock.withLock {
            _streams.remove(stream)
        }
    }

    private suspend fun remoteOpenedStream(stream: MockStream) {
        addStream(stream)
        localNet.handleNewStream(stream)
    }

    private suspend fun openStream(name: String?): Result<Stream> {
        return MockStream.newStreamPair(name)
            .map { (sl, sr) ->
                reverseConnection.remoteOpenedStream(sr)
                addStream(sl)
                sl
            }
    }

    companion object {
        private val connectionCounter = AtomicLong(0)

        fun create(scope: CoroutineScope, localNet: PeerNet, remoteNet: PeerNet, link: MockLink, direction: Direction): Result<MockConnection> {
            return runBlocking {
                val localIdentity = localNet.peerstore.localIdentity(localNet.peer)
                val remoteIdentity = remoteNet.peerstore.remoteIdentity(remoteNet.peer)
                if (localIdentity == null || remoteIdentity == null) {
                    Err("Could not find LocalIdentity or RemoteIdentity in peerstore")
                } else {
                    val statistics = ConnectionStatistics()
                    statistics.direction = direction
                    val localAddress = localNet.peerstore.addresses(localNet.peer).firstOrNull()
                    val remoteAddress = remoteNet.peerstore.addresses(remoteNet.peer).firstOrNull { !IpUtil.isIpUnspecified(it) } ?: remoteNet.peerstore.addresses(remoteNet.peer).firstOrNull()
                    if (localAddress == null || remoteAddress == null) {
                        Err("Could not find LocalAddress or RemoteAddress in peerstore")
                    } else {
                        Ok(MockConnection(scope, localNet, remoteNet, link, localIdentity, remoteIdentity, localAddress, remoteAddress, statistics))
                    }
                }
            }
        }
    }
}
