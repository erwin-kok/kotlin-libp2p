// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.yamux

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import mu.KotlinLogging
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.network.Connection
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger {}

class Session(
    private val scope: CoroutineScope,
    private val config: YamuxConfig,
    private val connection: Connection,
    private val client: Boolean,
    private val memoryManager: (() -> Result<MemoryManager>)? = { Ok(MemoryManager.NullMemoryManager) }
) : AwaitableClosable {
    private val _context = Job(scope.coroutineContext[Job])

    override val jobContext: Job get() = _context

//    rtt int64 // to be accessed atomically, in nanoseconds

    // remoteGoAway indicates the remote side does
    // not want futher connections. Must be first for alignment.
//    remoteGoAway int32

    // localGoAway indicates that we should stop
    // accepting futher connections. Must be first for alignment.
//    localGoAway int32

    // nextStreamID is the next stream we should send. This depends if we are a client/server.
    private val nextStreamID = AtomicLong(0)

    // pings is used to track inflight pings
//    pingLock   sync.Mutex
//    pingID     uint32
//    activePing *ping

    // streams maps a stream id to a stream, and inflight has an entry
    // for any outgoing stream that has not yet been established. Both are
    // protected by streamLock.
//    numIncomingStreams uint32
//    streams            map[uint32]*Stream
//    inflight           map[uint32]struct{}
//    streamLock         sync.Mutex

    // synCh acts like a semaphore. It is sized to the AcceptBacklog which
    // is assumed to be symmetric between the client and server. This allows
    // the client to avoid exceeding the backlog and instead blocks the open.
//    synCh chan struct{}

    // acceptCh is used to pass ready streams to the client
//    acceptCh chan *Stream

    // sendCh is used to send messages
//    sendCh chan []byte

    // pingCh and pingCh are used to send pings and pongs
//    pongCh, pingCh chan uint32

    // recvDoneCh is closed when recv() exits to avoid a race
    // between stream registration and stream shutdown
//    recvDoneCh chan struct{}

    // sendDoneCh is closed when send() exits to avoid a race
    // between returning from a Stream.Write and exiting from the send loop
    // (which may be reading a buffer on-load-from Stream.Write).
//    sendDoneCh chan struct{}

    // shutdown is used to safely close a session
//    shutdown     bool
//    shutdownErr  error
//    shutdownCh   chan struct{}
//    shutdownLock sync.Mutex

    // keepaliveTimer is a periodic timer for keepalive messages. It's nil
    // when keepalives are disabled.
//    keepaliveLock   sync.Mutex
//    keepaliveTimer  *time.Timer
//    keepaliveActive bool

    init {
        if (client) {
            nextStreamID.set(1)
        } else {
            nextStreamID.set(2)
        }
        if (config.enableKeepAlive) {

        }
    }

    override fun close() {

    }

    fun openStream(name: String?): Result<YamuxMuxedStream> {
        TODO("Not yet implemented")
    }

    fun acceptStream(): Result<YamuxMuxedStream> {
        TODO("Not yet implemented")
    }
}
