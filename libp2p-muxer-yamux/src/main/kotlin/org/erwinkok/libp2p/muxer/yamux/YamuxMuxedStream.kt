// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.yamux

import io.ktor.network.util.DefaultByteBufferPool
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.ReaderJob
import io.ktor.utils.io.WriterJob
import io.ktor.utils.io.cancel
import io.ktor.utils.io.close
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.internal.ChunkBuffer
import io.ktor.utils.io.core.writeFully
import io.ktor.utils.io.pool.ObjectPool
import io.ktor.utils.io.pool.useInstance
import io.ktor.utils.io.reader
import io.ktor.utils.io.writer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.consumeEach
import mu.KotlinLogging
import org.erwinkok.libp2p.core.network.StreamResetException
import org.erwinkok.libp2p.core.network.streammuxer.MuxedStream
import org.erwinkok.libp2p.core.util.SafeChannel
import org.erwinkok.libp2p.core.util.buildPacket
import org.erwinkok.libp2p.muxer.yamux.frame.Frame
import org.erwinkok.result.errorMessage
import java.io.IOException
import java.nio.ByteBuffer

enum class StreamState {
    StreamInit,
    StreamSYNSent,
    StreamSYNReceived,
    StreamEstablished,
    StreamFinished,
}

enum class HalfStreamState {
    HalfOpen,
    HalfClosed,
    HalfReset,
}

private val logger = KotlinLogging.logger {}

class YamuxMuxedStream(
    private val scope: CoroutineScope,
    private val session: Session,
    private val outputChannel: Channel<Frame>,
    private val yamuxStreamId: YamuxStreamId,
    override val name: String,
    override val pool: ObjectPool<ChunkBuffer>
) : MuxedStream {
//    sendWindow uint32
//
//    memorySpan MemoryManager
//
//    recvWindow uint32
//    epochStart time.Time
//
//    state                 streamState
//    writeState, readState halfStreamState
//    stateLock             sync.Mutex
//
//    recvBuf segmentedBuffer
//
//    recvNotifyCh chan struct{}
//    sendNotifyCh chan struct{}
//
//    readDeadline, writeDeadline pipeDeadline

    private val inputChannel = SafeChannel<ByteReadPacket>(16)
    private val _context = Job(scope.coroutineContext[Job])
    private val writerJob: WriterJob
    private val readerJob: ReaderJob

    override val id
        get() = yamuxStreamId.toString()
    override val jobContext: Job
        get() = _context

    override val input: ByteReadChannel = ByteChannel(false).also { writerJob = attachForReading(it) }
    override val output: ByteWriteChannel = ByteChannel(false).also { readerJob = attachForWriting(it) }

    private fun attachForReading(channel: ByteChannel): WriterJob =
        scope.writer(_context + CoroutineName("mplex-stream-input-loop"), channel) {
            inputDataLoop(this.channel)
        }.apply {
            invokeOnCompletion {
                if (readerJob.isCompleted) {
                    session.removeStream(yamuxStreamId)
                }
            }
        }

    private fun attachForWriting(channel: ByteChannel): ReaderJob =
        scope.reader(_context + CoroutineName("mplex-stream-output-loop"), channel) {
            outputDataLoop(this.channel)
        }.apply {
            invokeOnCompletion {
                if (writerJob.isCompleted) {
                    session.removeStream(yamuxStreamId)
                }
            }
        }

    private suspend fun inputDataLoop(channel: ByteWriteChannel) {
        while (!inputChannel.isClosedForReceive && !channel.isClosedForWrite) {
            try {
                inputChannel.consumeEach {
                    channel.writePacket(it)
                    channel.flush()
                }
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                logger.warn { "Unexpected error occurred in mplex mux input loop: ${errorMessage(e)}" }
                throw e
            }
        }
        if (!inputChannel.isClosedForReceive) {
            inputChannel.cancel()
        }
    }

    private suspend fun outputDataLoop(channel: ByteReadChannel): Unit = DefaultByteBufferPool.useInstance { buffer: ByteBuffer ->
        while (!channel.isClosedForRead && !outputChannel.isClosedForSend) {
            buffer.clear()
            try {
                val size = channel.readAvailable(buffer)
                if (size > 0) {
                    buffer.flip()
                    val packet = buildPacket(pool) { writeFully(buffer) }
//                    val messageFrame = MessageFrame(yamuxStreamId, packet)
//                    outputChannel.send(messageFrame)
                }
            } catch (e: CancellationException) {
                break
            } catch (e: ClosedSendChannelException) {
                break
            } catch (e: Exception) {
                logger.warn { "Unexpected error occurred in mplex mux output loop: ${errorMessage(e)}" }
                throw e
            }
        }
        if (!channel.isClosedForRead) {
            channel.cancel(IOException("Failed writing to closed connection"))
        }
        if (!outputChannel.isClosedForSend) {
            if (channel.closedCause is StreamResetException) {
//                outputChannel.send(ResetFrame(yamuxStreamId))
            } else {
//                outputChannel.send(CloseFrame(yamuxStreamId))
            }
        }
    }

    override fun reset() {
        inputChannel.cancel()
        input.cancel(StreamResetException())
        output.close(StreamResetException())
        _context.complete()
    }

    override fun close() {
        inputChannel.cancel()
        input.cancel()
        output.close()
        _context.complete()
    }

    override fun toString(): String {
        return "mplex-<$yamuxStreamId>"
    }

    internal suspend fun remoteSendsNewMessage(packet: ByteReadPacket): Boolean {
        if (inputChannel.isClosedForSend) {
            packet.close()
            return false
        }
        inputChannel.send(packet)
        return true
    }

    internal fun remoteClosesWriting() {
        inputChannel.close()
    }

    internal fun remoteResetsStream() {
        inputChannel.cancel()
        input.cancel(StreamResetException())
        output.close(StreamResetException())
        _context.completeExceptionally(StreamResetException())
    }

    internal fun increaseSendWindow(frame: Frame) {
        inputChannel.cancel()
        input.cancel(StreamResetException())
        output.close(StreamResetException())
        _context.completeExceptionally(StreamResetException())
    }
}
