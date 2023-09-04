// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.testing.mocknet.done

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.Direction
import org.erwinkok.libp2p.core.network.Network
import org.erwinkok.libp2p.testing.mocknet.PeerNet
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.getOrElse
import kotlin.time.Duration

class MockLink(
    val scope: CoroutineScope,
    val mocknet: MockNet,
    linkOptions: LinkOptions
) : Link {
    private val lock = Mutex()
    private val nets = mutableListOf<PeerNet>()
    private val ratelimiter = RateLimiter(linkOptions.bandwidth)

    override var options = linkOptions
        set(value) {
            runBlocking {
                lock.withLock {
                    field = value
                    ratelimiter.updateBandwidth(value.bandwidth)
                }
            }
        }
        get() = runBlocking { lock.withLock { field } }

    val latency: Duration
        get() = runBlocking { lock.withLock { options.latency } }

    suspend fun newConnectionPair(dialer: PeerNet): Result<ConnectionPair> {
        lock.withLock {
            val target = if (dialer == nets[0]) {
                nets[1]
            } else {
                nets[0]
            }
            val dialerConnection = MockConnection.create(scope, dialer, target, this, Direction.DirOutbound)
                .getOrElse { return Err(it) }
            val targetConnection = MockConnection.create(scope, target, dialer, this, Direction.DirInbound)
                .getOrElse { return Err(it) }
            dialerConnection.reverseConnection = targetConnection
            targetConnection.reverseConnection = dialerConnection
            return Ok(ConnectionPair(dialerConnection, targetConnection))
        }
    }

    override fun networks(): List<Network> {
        return runBlocking {
            lock.withLock {
                nets.toList()
            }
        }
    }

    override fun peers(): List<PeerId> {
        return runBlocking {
            lock.withLock {
                nets.map { it.peer }
            }
        }
    }

    fun ratelimit(dataSize: Int): Duration {
        return ratelimiter.limit(dataSize)
    }
}
