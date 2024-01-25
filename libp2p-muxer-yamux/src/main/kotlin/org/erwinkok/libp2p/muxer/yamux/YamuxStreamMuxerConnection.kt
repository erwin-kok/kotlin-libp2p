// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
@file:OptIn(DelicateCoroutinesApi::class)

package org.erwinkok.libp2p.muxer.yamux

import io.ktor.utils.io.cancel
import io.ktor.utils.io.close
import io.ktor.utils.io.core.BytePacketBuilder
import io.ktor.utils.io.core.internal.ChunkBuffer
import io.ktor.utils.io.pool.ObjectPool
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import mu.KotlinLogging
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.network.Connection
import org.erwinkok.libp2p.core.network.streammuxer.MuxedStream
import org.erwinkok.libp2p.core.network.streammuxer.StreamMuxerConnection
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
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

class YamuxStreamMuxerConnection internal constructor(
    private val scope: CoroutineScope,
    private val connection: Connection,
    private val initiator: Boolean,
) : StreamMuxerConnection, AwaitableClosable {
    private val streamChannel = SafeChannel<MuxedStream>(16)
    private val outputChannel = SafeChannel<Frame>(16)
    private val mutex = ReentrantLock()
    private val streams = mutableMapOf<MplexStreamId, MplexMuxedStream>()
    private val nextId = AtomicLong(0)
    private val isClosing = AtomicBoolean(false)
    private var closeCause: Error? = null
    private val _context = Job(scope.coroutineContext[Job])
    private val receiverJob: Job
    private val pool: ObjectPool<ChunkBuffer> get() = connection.pool

    override val jobContext: Job get() = _context

    init {
        receiverJob = processInbound()
        processOutbound()
    }

    override suspend fun openStream(name: String?): Result<MuxedStream> {
        return newNamedStream(name)
    }

    override suspend fun acceptStream(): Result<MuxedStream> {
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

    internal fun removeStream(streamId: MplexStreamId) {
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
        val mplexStreamId = MplexStreamId(!initiator, id)
        mutex.lock()
        val stream: MplexMuxedStream? = streams[mplexStreamId]
        when (mplexFrame) {
            is NewStreamFrame -> {
                if (stream != null) {
                    mutex.unlock()
                    logger.warn { "$this: Remote creates existing new stream: $id. Ignoring." }
                } else {
                    logger.debug { "$this: Remote creates new stream: $id" }
                    val name = streamName(mplexFrame.name, mplexStreamId)
                    val newStream = MplexMuxedStream(scope, this, outputChannel, mplexStreamId, name, pool)
                    streams[mplexStreamId] = newStream
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
                        logger.warn { "$this: Reader timeout for stream: $mplexStreamId. Reader is too slow, resetting the stream." }
                        stream.reset()
                    }
                } else {
                    mutex.unlock()
                    logger.warn { "$this: Remote sends message on non-existing stream: $mplexStreamId" }
                }
            }

            is CloseFrame -> {
                if (logger.isDebugEnabled) {
                    if (initiator) {
                        logger.debug("$this: Remote closes his stream: $mplexStreamId")
                    } else {
                        logger.debug("$this: Remote closes our stream: $mplexStreamId")
                    }
                }
                if (stream != null) {
                    mutex.unlock()
                    stream.remoteClosesWriting()
                } else {
                    mutex.unlock()
                    logger.debug { "$this: Remote closes non-existing stream: $mplexStreamId" }
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
        val streamId = MplexStreamId(true, id)
        logger.debug { "$this: We create stream: $id" }
        val name = streamName(newName, streamId)
        val muxedStream = MplexMuxedStream(scope, this, outputChannel, streamId, name, pool)
        streams[streamId] = muxedStream
        mutex.unlock()
        outputChannel.send(NewStreamFrame(id, name))
        return Ok(muxedStream)
    }

    private fun streamName(name: String?, streamId: MplexStreamId): String {
        if (name != null) {
            return name
        }
        return String.format("stream%08x", streamId.id)
    }

    override fun toString(): String {
        val initiator = if (initiator) "initiator" else "responder"
        return "mplex-muxer<$initiator>"
    }

    companion object {
        private val ErrShutdown = Error("session shut down")
        private val ReceivePushTimeout = 5.seconds
    }
}
