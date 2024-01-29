// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.yamux

import io.ktor.utils.io.cancel
import io.ktor.utils.io.close
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.internal.ChunkBuffer
import io.ktor.utils.io.core.writeFully
import io.ktor.utils.io.pool.ObjectPool
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import mu.KotlinLogging
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.network.Connection
import org.erwinkok.libp2p.core.network.streammuxer.MuxedStream
import org.erwinkok.libp2p.core.resourcemanager.ResourceScope
import org.erwinkok.libp2p.core.resourcemanager.ResourceScopeSpan
import org.erwinkok.libp2p.core.util.SafeChannel
import org.erwinkok.libp2p.core.util.Timer
import org.erwinkok.libp2p.core.util.buildPacket
import org.erwinkok.result.Err
import org.erwinkok.result.Error
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.erwinkok.result.flatMap
import org.erwinkok.result.getOrElse
import org.erwinkok.result.map
import org.erwinkok.result.onFailure
import org.erwinkok.result.onSuccess
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlin.system.measureNanoTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

private val logger = KotlinLogging.logger {}

class Session(
    private val scope: CoroutineScope,
    private val config: YamuxConfig,
    private val connection: Connection,
    private val client: Boolean,
    private val memoryManager: (() -> Result<ResourceScopeSpan>)?
) : AwaitableClosable {
    private val _context = Job(scope.coroutineContext[Job])

    override val jobContext: Job get() = _context

    private val rtt = AtomicLong(0) // to be accessed atomically, in nanoseconds

    // remoteGoAway indicates the remote side does not want further connections. Must be first for alignment.
    private val remoteGoAway = AtomicBoolean(false)

    // localGoAway indicates that we should stop accepting further connections. Must be first for alignment.
    private val localGoAway = AtomicBoolean(false)

    // nextStreamID is the next stream we should send. This depends on if we are a client/server.
    private val nextStreamId = AtomicInteger(0)

    // pings is used to track inflight pings
    private val pingLock = Mutex()
    private var pingId: Int = 0
    private var activePing: Ping? = null

    // streams maps a stream id to a stream, and inflight has an entry for any outgoing stream that has not yet been established. Both are protected by streamLock.
    private var numIncomingStreams = 0
    private val streams = mutableMapOf<Int, YamuxMuxedStream>()

    private val inflight = mutableSetOf<Int>()
    private val streamLock = ReentrantLock()

    // synCh acts like a semaphore. It is sized to the AcceptBacklog which
    // is assumed to be symmetric between the client and server. This allows
    // the client to avoid exceeding the backlog and instead blocks the open.
    private val synChannel = Channel<Unit>(config.acceptBacklog)

    // acceptChannel is used to pass ready streams to the client
    private val acceptChannel = SafeChannel<YamuxMuxedStream>(config.acceptBacklog)

    // sendCh is used to send messages
//    sendCh chan []byte

    // pongChannel and pingChannel are used to send pings and pongs
    private val pongChannel = Channel<Int>(config.pingBacklog)
    private val pingChannel = Channel<Int>(Channel.RENDEZVOUS)

    // recvDoneCh is closed when recv() exits to avoid a race
    // between stream registration and stream shutdown
//    recvDoneCh chan struct{}

    // sendDoneCh is closed when send() exits to avoid a race
    // between returning from a Stream.Write and exiting from the send loop
    // (which may be reading a buffer on-load-from Stream.Write).
//    sendDoneCh chan struct{}

    // shutdown is used to safely close a session
    private val shutdown = false
    private val shutdownErr: Error? = null
    private val shutdownChannel = Channel<Unit>(Channel.RENDEZVOUS)
//    shutdownLock sync.Mutex

    // keepaliveTimer is a periodic timer for keepalive messages. It's nil
    // when keepalives are disabled.
//    keepaliveLock   sync.Mutex
//    keepaliveTimer  *time.Timer
//    keepaliveActive bool

    /// ***

    private val streamChannel = SafeChannel<MuxedStream>(16)
    private val outputChannel = SafeChannel<ByteReadPacket>(16)
    private val isClosing = AtomicBoolean(false)
    private var closeCause: Error? = null
    private val receiverJob: Job

    //    private val measureRttJob: Job
    private val pool: ObjectPool<ChunkBuffer> get() = connection.pool

    init {
        if (config.enableKeepAlive) {
            startKeepalive()
        }
        if (client) {
            nextStreamId.set(1)
        } else {
            nextStreamId.set(2)
        }
        receiverJob = processInbound()
        processOutbound()
//        measureRttJob = startMeasureRtt()
    }

    val getRtt = rtt.get().nanoseconds

    suspend fun openStream(name: String?): Result<MuxedStream> {
        if (outputChannel.isClosedForSend) {
            return Err(YamuxConst.errSessionShutdown)
        }
        if (remoteGoAway.get()) {
            return Err(YamuxConst.errRemoteGoAway)
        }

//        val stream = YamuxMuxedStream(scope, this, )

        return Err("TODO")
    }

    suspend fun acceptStream(): Result<MuxedStream> {
        if (streamChannel.isClosedForReceive) {
            return Err(closeCause ?: YamuxConst.errSessionShutdown)
        }
        return try {
            select {
                streamChannel.onReceive {
                    Ok(it)
                }
                receiverJob.onJoin {
                    Err(closeCause ?: YamuxConst.errSessionShutdown)
                }
            }
        } catch (e: Exception) {
            Err(YamuxConst.errSessionShutdown)
        }
    }

    suspend fun ping(): Result<Long> {
        pingLock.lock()
        val currentActivePing = activePing
        if (currentActivePing != null) {
            pingLock.unlock()
            return currentActivePing.wait()
        }

        val newActivePing = Ping(pingId)
        pingId++
        activePing = newActivePing
        pingLock.unlock()

        // Send the ping request, waiting at most one connection write timeout to flush it.
        val timer = Timer(scope, config.connectionWriteTimeout)

        // Define finish lambda
        val finish: (suspend (result: Result<Long>) -> Result<Long>) = { result ->
            timer.stop()
            newActivePing.finish(result)
            pingLock.withLock {
                activePing = null
            }
            result
        }

        select {
            pingChannel.onSend(newActivePing.id) { Ok(Unit) }
            timer.onTimeout { Err(YamuxConst.errTimeout) }
            shutdownChannel.onReceive { Err(YamuxConst.errSessionShutdown) }
        }.onFailure {
            finish(Err(it))
            return Err(it)
        }

        val time = measureNanoTime {
            timer.restart()
            select {
                newActivePing.onWait { Ok(Unit) }
                timer.onTimeout { Err(YamuxConst.errTimeout) }
                shutdownChannel.onReceive { Err(YamuxConst.errSessionShutdown) }
            }.onFailure {
                finish(Err(it))
                return Err(it)
            }
        }
        return finish(Ok(time))
    }

    override fun close() {
        isClosing.set(true)
//        measureRttJob.cancel()
        streamChannel.close()
        receiverJob.cancel()
        if (streams.isEmpty()) {
            outputChannel.close()
        }
        _context.complete()
    }

    private fun processOutbound() = scope.launch(_context + CoroutineName("yamux-stream-output-loop")) {
        while (!outputChannel.isClosedForReceive && !connection.output.isClosedForWrite) {
            try {
                val packet = outputChannel.receive()
                connection.output.writePacket(packet)
                connection.output.flush()
            } catch (e: ClosedReceiveChannelException) {
                break
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                logger.warn { "Unexpected error occurred in yamux mux input loop: ${errorMessage(e)}" }
                throw e
            }
        }
    }.apply {
        invokeOnCompletion {
            connection.output.close()
            outputChannel.close()
            // It is safe here to close the output of all streams, closing will still process pending requests.
            streams.forEach { it.value.output.close() }
        }
    }

    private fun startMeasureRtt() = scope.launch(_context + CoroutineName("yamux-stream-rtt-loop")) {
        measureRtt()
        while (isActive) {
            delay(config.measureRTTInterval)
            measureRtt()
        }
    }


    private fun goAway(goAwayProtoErr: GoAwayType): YamuxHeader {
        TODO("Not yet implemented")
    }

    private suspend fun measureRtt() {
        ping().onSuccess { newRtt ->
            if (!rtt.compareAndSet(0L, newRtt)) {
                val previous = rtt.get()
                val smoothedRtt = previous / 2 + newRtt / 2
                rtt.set(smoothedRtt)
            }
        }
    }

    private fun extendKeepalive() {

    }

    private fun startKeepalive() {
    }

    private suspend fun sendMessage(header: YamuxHeader, body: ByteArray? = null, timeout: Duration? = null): Result<Unit> {
        if (outputChannel.isClosedForSend) {
            return Err("XXX")
        }
        val packet = buildPacket(pool) {
            writeYamuxHeader(header)
            if (body != null) {
                writeFully(body)
            }
        }
        if (timeout != null) {
            val timeoutOrNull = withTimeoutOrNull(timeout) {
                outputChannel.send(packet)
            }
            if (timeoutOrNull == null) {
                packet.close()
                return Err(YamuxConst.errTimeout)
            }
        } else {
            outputChannel.send(packet)
        }
        return Ok(Unit)
    }

    private fun processInbound() = scope.launch(_context + CoroutineName("yamux-stream-input-loop")) {
        while (!connection.input.isClosedForRead) {
            try {
//                val s = connection.input.availableForRead
//                val b = ByteArray(s)
//                connection.input.readFully(b)
//                println(b)
                connection.input.readYamuxHeader()
                    .flatMap { header ->
                        extendKeepalive()
                        when (header.type) {
                            FrameType.TypeData, FrameType.TypeWindowUpdate -> handleStreamMessage(header)
                            FrameType.TypePing -> handlePing(header)
                            FrameType.TypeGoAway -> handleGoAway(header)
                        }
                    }
                    .onFailure {
                        closeCause = it
                        return@launch
                    }
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                logger.warn { "Unexpected error occurred in yamux multiplexer input loop: ${errorMessage(e)}" }
                throw e
            }
        }
    }.apply {
        invokeOnCompletion {
            connection.input.cancel()
        }
    }

    private suspend fun handleStreamMessage(header: YamuxHeader): Result<Unit> {
        val id = header.streamId
        val flags = header.flags
        if (flags.hasFlag(Flag.flagSyn)) {
            incomingStream(id)
                .onFailure { return Err(it) }
        }
        val stream = streamLock.withLock {
            streams[id]
        }
        if (stream == null) {
            if (header.type == FrameType.TypeData && header.length > 0) {
                logger.warn { "[WARN] yamux: Discarding data for stream: $id" }
                connection.input.readPacket(header.length).close()
            } else {
                logger.warn { "[WARN] yamux: frame for missing stream: $id" }
            }
            return Ok(Unit)
        }
        if (header.type == FrameType.TypeWindowUpdate) {
            stream.increaseSendWindow(header, flags)
            return Ok(Unit)
        }
        stream.readData(header, flags, connection.input)
            .onFailure {
                sendMessage(goAway(GoAwayType.GoAwayProtoError))
                return Err(it)
            }
        return Ok(Unit)
    }

    private suspend fun handlePing(header: YamuxHeader): Result<Unit> {
        val flags = header.flags
        val pingId = header.length
        if (flags.hasFlag(Flag.flagSyn)) {
            pongChannel.trySend(pingId)
                .onFailure {
                    logger.warn { "[WARN] yamux: dropped ping reply" }
                }
            return Ok(Unit)
        }
        pingLock.withLock {
            val ap = activePing
            if (ap != null && ap.id == pingId) {
                ap.pingResponse.trySend(Unit)
            }
        }
        return Ok(Unit)
    }

    private fun handleGoAway(header: YamuxHeader): Result<Unit> {
        return GoAwayType.fromInt(header.length)
            .map { code ->
                when (code) {
                    GoAwayType.GoAwayNormal -> {
                        remoteGoAway.set(true)
                        Ok(Unit)
                    }

                    GoAwayType.GoAwayProtoError -> {
                        logger.error { "[ERR] yamux: received protocol error go away" }
                        Err("yamux protocol error")
                    }

                    GoAwayType.GoAwayInternalError -> {
                        logger.error { "[ERR] yamux: received internal error go away" }
                        Err("remote yamux internal error")
                    }
                }
            }
    }

    private suspend fun incomingStream(id: Int): Result<Unit> {
//        if (client != (id % 2 == 0)) {
//            logger.error { "[ERR] yamux: both endpoints are clients" }
//            return Err("both yamux endpoints are clients")
//        }
        // Reject immediately if we are doing a go away
        if (localGoAway.get()) {
            return sendMessage(YamuxHeader(FrameType.TypeWindowUpdate, Flags.of(Flag.flagRst), id, 0))
        }
        // Allocate a new stream
        val mm = memoryManager
        val span = if (mm != null) {
            val span = mm()
                .getOrElse {
                    return Err("failed to create resource span: ${errorMessage(it)}")
                }
            span.reserveMemory(YamuxConst.initialStreamWindow, ResourceScope.ReservationPriorityAlways)
                .onFailure {
                    return Err(it)
                }
            span
        } else {
            null
        }
        val stream = YamuxMuxedStream(scope, this, id, StreamState.StreamSYNReceived, YamuxConst.initialStreamWindow, span, pool)
        streamLock.withLock {
            if (streams.contains(id)) {
                logger.error { "[ERR] yamux: duplicate stream declared" }
                sendMessage(goAway(GoAwayType.GoAwayProtoError))
                    .onFailure {
                        logger.warn { "[WARN] yamux: failed to send go away: ${errorMessage(it)}" }
                    }
                span?.done()
                return Err(YamuxConst.errDuplicateStream)
            }
        }
        if (numIncomingStreams >= config.maxIncomingStreams) {
            // too many active streams at the same time
            logger.warn { "[WARN] yamux: MaxIncomingStreams exceeded, forcing stream reset" }
            val result = sendMessage(YamuxHeader(FrameType.TypeWindowUpdate, Flags.of(Flag.flagRst), id, 0))
            span?.done()
            return result
        }
        numIncomingStreams++
        // Register the stream
        streams[id] = stream
        acceptChannel.trySend(stream)
            .onFailure {
                // Backlog exceeded! RST the stream
                logger.warn { "[WARN] yamux: backlog exceeded, forcing stream reset" }
                deleteStream(id)
                val result = sendMessage(YamuxHeader(FrameType.TypeWindowUpdate, Flags.of(Flag.flagRst), id, 0))
                span?.done()
                return result
            }
        return Ok(Unit)
    }

    internal fun closeStream(id: Int) {
        streamLock.withLock {
            if (inflight.contains(id)) {
                synChannel.tryReceive()
                    .onFailure {
                        logger.error { "[ERR] yamux: SYN tracking out of sync" }
                    }
                inflight.remove(id)
            }
            deleteStream(id)
        }
    }

    private fun deleteStream(id: Int) {
        val stream = streams[id] ?: return
        if (client == (id % 2 == 0)) {
            if (numIncomingStreams == 0) {
                logger.error { "[ERR] yamux: numIncomingStreams underflow" }
                // prevent the creation of any new streams
                numIncomingStreams = Int.MAX_VALUE
            } else {
                numIncomingStreams--
            }
        }
        streams.remove(id)
        stream.memorySpan?.done()
    }

    internal fun establishStream(id: Int) {
        streamLock.withLock {
            if (inflight.contains(id)) {
                inflight.remove(id)
            } else {
                logger.error { "[ERR] yamux: established stream without inflight SYN (no tracking entry)" }
            }
            synChannel.tryReceive()
                .onFailure {
                    logger.error { "[ERR] yamux: established stream without inflight SYN (didn't have semaphore)" }
                }
        }
    }

    override fun toString(): String {
        val initiator = if (client) "client" else "server"
        return "yamux-muxer<$initiator>"
    }
}
