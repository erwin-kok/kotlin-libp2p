// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.yamux

import io.ktor.utils.io.cancel
import io.ktor.utils.io.close
import io.ktor.utils.io.core.internal.ChunkBuffer
import io.ktor.utils.io.pool.ObjectPool
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.network.Connection
import org.erwinkok.libp2p.core.network.streammuxer.MuxedStream
import org.erwinkok.libp2p.core.util.SafeChannel
import org.erwinkok.libp2p.core.util.Timer
import org.erwinkok.libp2p.muxer.yamux.YamuxConst.errShutdown
import org.erwinkok.libp2p.muxer.yamux.YamuxConst.errTimeout
import org.erwinkok.libp2p.muxer.yamux.frame.DataFrame
import org.erwinkok.libp2p.muxer.yamux.frame.Frame
import org.erwinkok.libp2p.muxer.yamux.frame.GoAwayFrame
import org.erwinkok.libp2p.muxer.yamux.frame.PingFrame
import org.erwinkok.libp2p.muxer.yamux.frame.WindowUpdateFrame
import org.erwinkok.libp2p.muxer.yamux.frame.readYamuxFrame
import org.erwinkok.libp2p.muxer.yamux.frame.writeYamuxFrame
import org.erwinkok.result.Err
import org.erwinkok.result.Error
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.erwinkok.result.map
import org.erwinkok.result.onFailure
import org.erwinkok.result.onSuccess
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlin.experimental.and
import kotlin.system.measureNanoTime
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

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

    private val rtt = AtomicLong(0) // to be accessed atomically, in nanoseconds

    // remoteGoAway indicates the remote side does
    // not want futher connections. Must be first for alignment.
//    remoteGoAway int32

    // localGoAway indicates that we should stop
    // accepting futher connections. Must be first for alignment.
//    localGoAway int32

    // nextStreamID is the next stream we should send. This depends if we are a client/server.
    private val nextStreamId = AtomicInteger(0)

    // pings is used to track inflight pings
    private val pingLock = Mutex()
    private var pingId: Long = 0L
    private var activePing: Ping? = null

    // streams maps a stream id to a stream, and inflight has an entry
    // for any outgoing stream that has not yet been established. Both are
    // protected by streamLock.
//    numIncomingStreams uint32
    private val streams = mutableMapOf<Int, YamuxMuxedStream>()

    //    inflight           map[uint32]struct{}
    private val streamLock = ReentrantLock()

    // synCh acts like a semaphore. It is sized to the AcceptBacklog which
    // is assumed to be symmetric between the client and server. This allows
    // the client to avoid exceeding the backlog and instead blocks the open.
//    synCh chan struct{}

    // acceptCh is used to pass ready streams to the client
//    acceptCh chan *Stream

    // sendCh is used to send messages
//    sendCh chan []byte

    // pongChannel and pingChannel are used to send pings and pongs
    private val pongChannel = Channel<Long>(config.pingBacklog)
    private val pingChannel = Channel<Long>(Channel.RENDEZVOUS)

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
    private val shutdownChannel = Channel<Unit>(Channel.RENDEZVOUS)
//    shutdownLock sync.Mutex

    // keepaliveTimer is a periodic timer for keepalive messages. It's nil
    // when keepalives are disabled.
//    keepaliveLock   sync.Mutex
//    keepaliveTimer  *time.Timer
//    keepaliveActive bool

    private val streamChannel = SafeChannel<MuxedStream>(16)
    private val outputChannel = SafeChannel<Frame>(16)
    private val isClosing = AtomicBoolean(false)
    private var closeCause: Error? = null
    private val receiverJob: Job
    private val measureRttJob: Job
    private val pool: ObjectPool<ChunkBuffer> get() = connection.pool

    init {
        if (config.enableKeepAlive) {
            startKeepalive()
        }
        receiverJob = processInbound()
        processOutbound()
        measureRttJob = startMeasureRtt()
    }

    val getRtt = rtt.get().nanoseconds

    suspend fun openStream(name: String?): Result<MuxedStream> {
        return newNamedStream(name)
    }

    suspend fun acceptStream(): Result<MuxedStream> {
        if (streamChannel.isClosedForReceive) {
            return Err(closeCause ?: ErrShutdown)
        }
        return try {
            select {
                streamChannel.onReceive {
                    Ok(it)
                }
                receiverJob.onJoin {
                    Err(closeCause ?: ErrShutdown)
                }
            }
        } catch (e: Exception) {
            Err(ErrShutdown)
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
            timer.onTimeout { Err(errTimeout) }
            shutdownChannel.onReceive { Err(errShutdown) }
        }.onFailure {
            finish(Err(it))
            return Err(it)
        }

        val time = measureNanoTime {
            timer.restart()
            select {
                newActivePing.onWait { Ok(Unit) }
                timer.onTimeout { Err(errTimeout) }
                shutdownChannel.onReceive { Err(errShutdown) }
            }.onFailure {
                finish(Err(it))
                return Err(it)
            }
        }
        return finish(Ok(time))
    }

    override fun close() {
        isClosing.set(true)
        measureRttJob.cancel()
        streamChannel.close()
        receiverJob.cancel()
        if (streams.isEmpty()) {
            outputChannel.close()
        }
        _context.complete()
    }

    internal fun removeStream(streamId: YamuxStreamId) {
        streamLock.withLock {
            streams.remove(streamId.id)
            if (isClosing.get() && streams.isEmpty()) {
                outputChannel.close()
            }
        }
    }

    private fun processInbound() = scope.launch(_context + CoroutineName("yamux-stream-input-loop")) {
        while (!connection.input.isClosedForRead && !streamChannel.isClosedForSend) {
            try {
                connection.input.readYamuxFrame()
                    .map { frame -> processFrame(frame) }
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
            streamChannel.cancel()
            // do not cancel the input of the streams here, there might still be some pending frames in the input queue.
            // instead, close the input loop gracefully.
            streams.forEach { it.value.remoteClosesWriting() }
        }
    }

    private fun processOutbound() = scope.launch(_context + CoroutineName("yamux-stream-output-loop")) {
        while (!outputChannel.isClosedForReceive && !connection.output.isClosedForWrite) {
            try {
                val frame = outputChannel.receive()
                connection.output.writeYamuxFrame(frame)
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

    private suspend fun measureRtt() {
        ping().onSuccess { newRtt ->
            if (!rtt.compareAndSet(0L, newRtt)) {
                val previous = rtt.get()
                val smoothedRtt = previous / 2 + newRtt / 2
                rtt.set(smoothedRtt)
            }
        }
    }

    private fun startKeepalive() {
    }

    private suspend fun processFrame(frame: Frame) {
        when (frame) {
            is DataFrame, is WindowUpdateFrame -> {
                handleStreamMessage(frame)
            }

            is PingFrame -> TODO()
            is GoAwayFrame -> TODO()
        }


//        val id = mplexFrame.id
//        val initiator = mplexFrame.initiator
//        val yamuxStreamId = YamuxStreamId(!initiator, id)
//        streamLock.lock()
//        val stream: YamuxMuxedStream? = streams[yamuxStreamId]
//        when (mplexFrame) {
//            is NewStreamFrame -> {
//            }
//
//            is MessageFrame -> {
//                if (logger.isDebugEnabled) {
//                    if (initiator) {
//                        logger.debug("$this: Remote sends message on his stream: $id")
//                    } else {
//                        logger.debug("$this: Remote sends message on our stream: $id")
//                    }
//                }
//                if (stream != null) {
//                    streamLock.unlock()
//                    val builder = BytePacketBuilder(pool)
//                    val data = mplexFrame.packet
//                    builder.writePacket(data.copy())
//                    // There is (almost) no backpressure. If the reader is slow/blocking, then the entire muxer is blocking.
//                    // Give the reader "ReceiveTimeout" time to process, reset stream if too slow.
//                    val timeout = withTimeoutOrNull(ReceivePushTimeout) {
//                        stream.remoteSendsNewMessage(builder.build())
//                    }
//                    if (timeout == null) {
//                        logger.warn { "$this: Reader timeout for stream: $yamuxStreamId. Reader is too slow, resetting the stream." }
//                        stream.reset()
//                    }
//                } else {
//                    streamLock.unlock()
//                    logger.warn { "$this: Remote sends message on non-existing stream: $yamuxStreamId" }
//                }
//            }
//
//            is CloseFrame -> {
//                if (logger.isDebugEnabled) {
//                    if (initiator) {
//                        logger.debug("$this: Remote closes his stream: $yamuxStreamId")
//                    } else {
//                        logger.debug("$this: Remote closes our stream: $yamuxStreamId")
//                    }
//                }
//                if (stream != null) {
//                    streamLock.unlock()
//                    stream.remoteClosesWriting()
//                } else {
//                    streamLock.unlock()
//                    logger.debug { "$this: Remote closes non-existing stream: $yamuxStreamId" }
//                }
//            }
//
//            is ResetFrame -> {
//                if (logger.isDebugEnabled) {
//                    if (initiator) {
//                        logger.debug("$this: Remote resets his stream: $id")
//                    } else {
//                        logger.debug("$this: Remote resets our stream: $id")
//                    }
//                }
//                if (stream != null) {
//                    streamLock.unlock()
//                    stream.remoteResetsStream()
//                } else {
//                    streamLock.unlock()
//                    logger.debug { "$this: Remote resets non-existing stream: $id" }
//                }
//            }
//        }
        frame.close()
    }

    private fun handleStreamMessage(frame: Frame): Result<Unit> {
        val id = frame.id
        val flags = frame.flags
        if ((flags and Frame.SynFlag) == Frame.SynFlag) {
            incomingStream(id)
                .onFailure { return Err(it) }
        }
        val stream = streamLock.withLock {
            streams[id]
        }
        if (stream == null) {
            logger.warn { "[WARN] yamux: frame for missing stream: $id" }
            return Ok(Unit)
        }
        if (frame.type == Frame.typeWindowUpdate) {
            stream.increaseSendWindow(frame)
            return Ok(Unit)
        }

        return Ok(Unit)
    }


    private fun incomingStream(id: Int): Result<Unit> {
//                if (stream != null) {
//                    streamLock.unlock()
//                    logger.warn { "$this: Remote creates existing new stream: $id. Ignoring." }
//                } else {
//                    logger.debug { "$this: Remote creates new stream: $id" }
//                    val name = streamName(mplexFrame.name, yamuxStreamId)
//                    val newStream = YamuxMuxedStream(scope, this, outputChannel, yamuxStreamId, name, pool)
//                    streams[yamuxStreamId] = newStream
//                    streamLock.unlock()
//                    streamChannel.send(newStream)
//                }
        return Ok(Unit)
    }

    private suspend fun newNamedStream(newName: String?): Result<MuxedStream> {
        if (outputChannel.isClosedForSend) {
            return Err("$this: yamux is closed")
        }
        streamLock.lock()
        val id = nextStreamId.getAndIncrement()
        val streamId = YamuxStreamId(true, id)
        logger.debug { "$this: We create stream: $id" }
        val name = streamName(newName, streamId)
        val muxedStream = YamuxMuxedStream(scope, this, outputChannel, streamId, name, pool)
        streams[streamId.id] = muxedStream
        streamLock.unlock()
//        outputChannel.send(NewStreamFrame(id, name))
        return Ok(muxedStream)
    }

    private fun streamName(name: String?, streamId: YamuxStreamId): String {
        if (name != null) {
            return name
        }
        return String.format("stream%08x", streamId.id)
    }

    override fun toString(): String {
        val initiator = if (client) "client" else "server"
        return "yamux-muxer<$initiator>"
    }

    companion object {
        private val ErrShutdown = Error("session shut down")
        private val ReceivePushTimeout = 5.seconds
    }
}
