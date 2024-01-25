// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.yamux

import io.ktor.utils.io.cancel
import io.ktor.utils.io.close
import io.ktor.utils.io.core.BytePacketBuilder
import io.ktor.utils.io.core.internal.ChunkBuffer
import io.ktor.utils.io.pool.ObjectPool
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import mu.KotlinLogging
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.network.Connection
import org.erwinkok.libp2p.core.network.streammuxer.MuxedStream
import org.erwinkok.libp2p.core.util.SafeChannel
import org.erwinkok.libp2p.muxer.yamux.frame.CloseFrame
import org.erwinkok.libp2p.muxer.yamux.frame.Frame
import org.erwinkok.libp2p.muxer.yamux.frame.MessageFrame
import org.erwinkok.libp2p.muxer.yamux.frame.NewStreamFrame
import org.erwinkok.libp2p.muxer.yamux.frame.ResetFrame
import org.erwinkok.libp2p.muxer.yamux.frame.readMplexFrame
import org.erwinkok.libp2p.muxer.yamux.frame.writeMplexFrame
import org.erwinkok.result.Err
import org.erwinkok.result.Error
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.erwinkok.result.map
import org.erwinkok.result.onFailure
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
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


    private val streamChannel = SafeChannel<MuxedStream>(16)
    private val outputChannel = SafeChannel<Frame>(16)
    private val mutex = ReentrantLock()
    private val streams = mutableMapOf<YamuxStreamId, YamuxMuxedStream>()
    private val nextId = AtomicLong(0)
    private val isClosing = AtomicBoolean(false)
    private var closeCause: Error? = null
    private val receiverJob: Job
    private val pool: ObjectPool<ChunkBuffer> get() = connection.pool

    init {
        receiverJob = processInbound()
        processOutbound()
    }

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

    override fun close() {
        isClosing.set(true)
        streamChannel.close()
        receiverJob.cancel()
        if (streams.isEmpty()) {
            outputChannel.close()
        }
        _context.complete()
    }

    internal fun removeStream(streamId: YamuxStreamId) {
        mutex.withLock {
            streams.remove(streamId)
            if (isClosing.get() && streams.isEmpty()) {
                outputChannel.close()
            }
        }
    }

    private fun processInbound() = scope.launch(_context + CoroutineName("mplex-stream-input-loop")) {
        while (!connection.input.isClosedForRead && !streamChannel.isClosedForSend) {
            try {
                connection.input.readMplexFrame()
                    .map { mplexFrame -> processFrame(mplexFrame) }
                    .onFailure {
                        closeCause = it
                        return@launch
                    }
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                logger.warn { "Unexpected error occurred in mplex multiplexer input loop: ${errorMessage(e)}" }
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

    private fun processOutbound() = scope.launch(_context + CoroutineName("mplex-stream-output-loop")) {
        while (!outputChannel.isClosedForReceive && !connection.output.isClosedForWrite) {
            try {
                val frame = outputChannel.receive()
                connection.output.writeMplexFrame(frame)
                connection.output.flush()
            } catch (e: ClosedReceiveChannelException) {
                break
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                logger.warn { "Unexpected error occurred in mplex mux input loop: ${errorMessage(e)}" }
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

    private suspend fun processFrame(mplexFrame: Frame) {
        val id = mplexFrame.id
        val initiator = mplexFrame.initiator
        val yamuxStreamId = YamuxStreamId(!initiator, id)
        mutex.lock()
        val stream: YamuxMuxedStream? = streams[yamuxStreamId]
        when (mplexFrame) {
            is NewStreamFrame -> {
                if (stream != null) {
                    mutex.unlock()
                    logger.warn { "$this: Remote creates existing new stream: $id. Ignoring." }
                } else {
                    logger.debug { "$this: Remote creates new stream: $id" }
                    val name = streamName(mplexFrame.name, yamuxStreamId)
                    val newStream = YamuxMuxedStream(scope, this, outputChannel, yamuxStreamId, name, pool)
                    streams[yamuxStreamId] = newStream
                    mutex.unlock()
                    streamChannel.send(newStream)
                }
            }

            is MessageFrame -> {
                if (logger.isDebugEnabled) {
                    if (initiator) {
                        logger.debug("$this: Remote sends message on his stream: $id")
                    } else {
                        logger.debug("$this: Remote sends message on our stream: $id")
                    }
                }
                if (stream != null) {
                    mutex.unlock()
                    val builder = BytePacketBuilder(pool)
                    val data = mplexFrame.packet
                    builder.writePacket(data.copy())
                    // There is (almost) no backpressure. If the reader is slow/blocking, then the entire muxer is blocking.
                    // Give the reader "ReceiveTimeout" time to process, reset stream if too slow.
                    val timeout = withTimeoutOrNull(ReceivePushTimeout) {
                        stream.remoteSendsNewMessage(builder.build())
                    }
                    if (timeout == null) {
                        logger.warn { "$this: Reader timeout for stream: $yamuxStreamId. Reader is too slow, resetting the stream." }
                        stream.reset()
                    }
                } else {
                    mutex.unlock()
                    logger.warn { "$this: Remote sends message on non-existing stream: $yamuxStreamId" }
                }
            }

            is CloseFrame -> {
                if (logger.isDebugEnabled) {
                    if (initiator) {
                        logger.debug("$this: Remote closes his stream: $yamuxStreamId")
                    } else {
                        logger.debug("$this: Remote closes our stream: $yamuxStreamId")
                    }
                }
                if (stream != null) {
                    mutex.unlock()
                    stream.remoteClosesWriting()
                } else {
                    mutex.unlock()
                    logger.debug { "$this: Remote closes non-existing stream: $yamuxStreamId" }
                }
            }

            is ResetFrame -> {
                if (logger.isDebugEnabled) {
                    if (initiator) {
                        logger.debug("$this: Remote resets his stream: $id")
                    } else {
                        logger.debug("$this: Remote resets our stream: $id")
                    }
                }
                if (stream != null) {
                    mutex.unlock()
                    stream.remoteResetsStream()
                } else {
                    mutex.unlock()
                    logger.debug { "$this: Remote resets non-existing stream: $id" }
                }
            }
        }
        mplexFrame.close()
    }

    private suspend fun newNamedStream(newName: String?): Result<MuxedStream> {
        if (outputChannel.isClosedForSend) {
            return Err("$this: Mplex is closed")
        }
        mutex.lock()
        val id = nextId.getAndIncrement()
        val streamId = YamuxStreamId(true, id)
        logger.debug { "$this: We create stream: $id" }
        val name = streamName(newName, streamId)
        val muxedStream = YamuxMuxedStream(scope, this, outputChannel, streamId, name, pool)
        streams[streamId] = muxedStream
        mutex.unlock()
        outputChannel.send(NewStreamFrame(id, name))
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
