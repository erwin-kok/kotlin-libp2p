// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
@file:OptIn(DelicateCoroutinesApi::class)

package org.erwinkok.libp2p.muxer.mplex

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.cio.KtorDefaultPool
import io.ktor.utils.io.cancel
import io.ktor.utils.io.close
import io.ktor.utils.io.pool.ObjectPool
import io.ktor.utils.io.readAvailable
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.network.Connection
import org.erwinkok.libp2p.core.network.streammuxer.MuxedStream
import org.erwinkok.libp2p.core.network.streammuxer.StreamMuxerConnection
import org.erwinkok.result.Err
import org.erwinkok.result.Error
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

class MplexStreamMuxerConnection internal constructor(
    private val scope: CoroutineScope,
    private val connection: Connection,
    private val initiator: Boolean,
    private val pool: ObjectPool<ByteBuffer> = KtorDefaultPool,
) : StreamMuxerConnection, AwaitableClosable {
    private val streamChannel = Channel<MuxedStream>(16)
    private val outputChannel = Channel<Frame>(16)
    private val mutex = ReentrantLock()
    private val streams = mutableMapOf<MplexStreamId, MplexMuxedStream>()
    private val nextId = AtomicLong(0)
    private val isClosing = AtomicBoolean(false)
    private var closeCause: Error? = null
    private val _context = Job(scope.coroutineContext[Job])
    private val receiverJob: Job
    private val frameReader = FrameReader()

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
        } catch (_: Exception) {
            Err(ErrShutdown)
        }
    }

    override fun close() {
        isClosing.set(true)
        streamChannel.close()
        receiverJob.cancel()
        streams.forEach { it.value.close() }
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

    private fun processInbound(): Job {
        return scope.launch(_context + CoroutineName("mplex-stream-input-loop")) {
            val buffer = pool.borrow()
            try {
                readLoop(buffer)
            } catch (expected: ClosedChannelException) {
            } catch (expected: CancellationException) {
            } catch (cause: Throwable) {
                logger.warn { "Unexpected error occurred in mplex multiplexer input loop: ${errorMessage(cause)}" }
                throw cause
            } finally {
                pool.recycle(buffer)
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
    }

    private suspend fun readLoop(buffer: ByteBuffer) {
        buffer.clear()
        while (!connection.input.isClosedForRead && !streamChannel.isClosedForSend) {
            connection.input.readAvailable(buffer)
            buffer.flip()
            parseLoop(buffer)
            buffer.compact()
        }
    }

    private suspend fun parseLoop(buffer: ByteBuffer) {
        while (buffer.hasRemaining()) {
            val frame = frameReader.frame(buffer)
            if (frame != null) {
                processFrame(frame)
            }
        }

//        FrameReader.readMplexFrame(connection.input)
//            .map { mplexFrame -> processFrame(mplexFrame) }
//            .onFailure {
//                closeCause = it
//                return
//            }
    }

    private suspend fun processFrame(mplexFrame: Frame) {
        val id = mplexFrame.id
        val initiator = mplexFrame.initiator
        val mplexStreamId = MplexStreamId(!initiator, id)
        mutex.lock()
        val stream: MplexMuxedStream? = streams[mplexStreamId]
        when (mplexFrame) {
            is Frame.NewStreamFrame -> {
                if (stream != null) {
                    mutex.unlock()
                    logger.warn { "$this: Remote creates existing new stream: $id. Ignoring." }
                } else {
                    logger.debug { "$this: Remote creates new stream: $id" }
                    val name = streamName(mplexFrame.name, mplexStreamId)
                    val newStream = MplexMuxedStream(scope, this, outputChannel, mplexStreamId, name)
                    streams[mplexStreamId] = newStream
                    mutex.unlock()
                    streamChannel.send(newStream)
                }
            }

            is Frame.MessageFrame -> {
                if (logger.isDebugEnabled()) {
                    if (initiator) {
                        logger.debug { "$this: Remote sends message on his stream: $id" }
                    } else {
                        logger.debug { "$this: Remote sends message on our stream: $id" }
                    }
                }
                if (stream != null) {
                    mutex.unlock()
                    // There is (almost) no backpressure. If the reader is slow/blocking, then the entire muxer is blocking.
                    // Give the reader "ReceiveTimeout" time to process, reset stream if too slow.
                    val timeout = withTimeoutOrNull(ReceivePushTimeout) {
                        stream.remoteSendsNewMessage(mplexFrame.data)
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

            is Frame.CloseFrame -> {
                if (logger.isDebugEnabled()) {
                    if (initiator) {
                        logger.debug { "$this: Remote closes his stream: $mplexStreamId" }
                    } else {
                        logger.debug { "$this: Remote closes our stream: $mplexStreamId" }
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

            is Frame.ResetFrame -> {
                if (logger.isDebugEnabled()) {
                    if (initiator) {
                        logger.debug { "$this: Remote resets his stream: $id" }
                    } else {
                        logger.debug { "$this: Remote resets our stream: $id" }
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
        val muxedStream = MplexMuxedStream(scope, this, outputChannel, streamId, name)
        streams[streamId] = muxedStream
        mutex.unlock()
        outputChannel.send(Frame.NewStreamFrame(id, name))
        return Ok(muxedStream)
    }

    private fun streamName(name: String?, streamId: MplexStreamId): String {
        if (name != null) {
            return name
        }
        return String.format("stream%08x", streamId.id)
    }

    private fun processOutbound() = scope.launch(_context + CoroutineName("mplex-stream-output-loop")) {
        while (!outputChannel.isClosedForReceive && !connection.output.isClosedForWrite) {
            try {
                val frame = outputChannel.receive()
                FrameWriter.writeMplexFrame(connection.output, frame)
                connection.output.flush()
            } catch (_: ClosedReceiveChannelException) {
                break
            } catch (_: CancellationException) {
                break
            } catch (e: Exception) {
                logger.warn { "Unexpected error occurred in mplex mux output loop: ${errorMessage(e)}" }
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

    override fun toString(): String {
        val initiator = if (initiator) "initiator" else "responder"
        return "mplex-muxer<$initiator>"
    }

    companion object {
        private val ErrShutdown = Error("session shut down")
        private val ReceivePushTimeout = 5.seconds
    }
}
