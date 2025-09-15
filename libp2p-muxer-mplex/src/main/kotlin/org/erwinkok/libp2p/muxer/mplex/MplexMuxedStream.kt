// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
@file:OptIn(DelicateCoroutinesApi::class)

package org.erwinkok.libp2p.muxer.mplex

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.util.DefaultByteBufferPool
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.ReaderJob
import io.ktor.utils.io.WriterJob
import io.ktor.utils.io.cancel
import io.ktor.utils.io.close
import io.ktor.utils.io.core.buildPacket
import io.ktor.utils.io.core.writeFully
import io.ktor.utils.io.invokeOnCompletion
import io.ktor.utils.io.isCompleted
import io.ktor.utils.io.pool.useInstance
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.reader
import io.ktor.utils.io.writePacket
import io.ktor.utils.io.writer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.consumeEach
import kotlinx.io.IOException
import kotlinx.io.Source
import org.erwinkok.libp2p.core.network.StreamResetException
import org.erwinkok.libp2p.core.network.streammuxer.MuxedStream
import org.erwinkok.libp2p.muxer.mplex.frame.CloseFrame
import org.erwinkok.libp2p.muxer.mplex.frame.Frame
import org.erwinkok.libp2p.muxer.mplex.frame.MessageFrame
import org.erwinkok.libp2p.muxer.mplex.frame.ResetFrame
import org.erwinkok.result.errorMessage
import java.nio.ByteBuffer

private val logger = KotlinLogging.logger {}

class MplexMuxedStream(
    private val scope: CoroutineScope,
    private val mplexMultiplexer: MplexStreamMuxerConnection,
    private val outputChannel: Channel<Frame>,
    private val mplexStreamId: MplexStreamId,
    override val name: String,
) : MuxedStream {
    private val inputChannel = Channel<Source>(16)
    private val _context = Job(scope.coroutineContext[Job])
    private val writerJob: WriterJob
    private val readerJob: ReaderJob

    override val id
        get() = mplexStreamId.toString()
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
                    mplexMultiplexer.removeStream(mplexStreamId)
                }
            }
        }

    private fun attachForWriting(channel: ByteChannel): ReaderJob =
        scope.reader(_context + CoroutineName("mplex-stream-output-loop"), channel) {
            outputDataLoop(this.channel)
        }.apply {
            invokeOnCompletion {
                if (writerJob.isCompleted) {
                    mplexMultiplexer.removeStream(mplexStreamId)
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
            } catch (_: CancellationException) {
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

    private suspend fun outputDataLoop(channel: ByteReadChannel) = DefaultByteBufferPool.useInstance { buffer: ByteBuffer ->
        while (!channel.isClosedForRead && !outputChannel.isClosedForSend) {
            buffer.clear()
            try {
                val size = channel.readAvailable(buffer)
                if (size > 0) {
                    buffer.flip()
                    val packet = buildPacket { writeFully(buffer) }
                    val messageFrame = MessageFrame(mplexStreamId, packet)
                    outputChannel.send(messageFrame)
                }
            } catch (_: CancellationException) {
                break
            } catch (_: ClosedSendChannelException) {
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
                outputChannel.send(ResetFrame(mplexStreamId))
            } else {
                outputChannel.send(CloseFrame(mplexStreamId))
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
        return "mplex-<$mplexStreamId>"
    }

    internal suspend fun remoteSendsNewMessage(packet: Source): Boolean {
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
}
