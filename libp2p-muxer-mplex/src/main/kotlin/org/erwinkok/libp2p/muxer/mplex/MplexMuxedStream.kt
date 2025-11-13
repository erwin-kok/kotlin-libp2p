// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
@file:OptIn(DelicateCoroutinesApi::class)

package org.erwinkok.libp2p.muxer.mplex

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.util.DefaultByteBufferPool
import io.ktor.util.moveToByteArray
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.ChannelJob
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.ReaderJob
import io.ktor.utils.io.WriterJob
import io.ktor.utils.io.attachJob
import io.ktor.utils.io.cancel
import io.ktor.utils.io.close
import io.ktor.utils.io.getCancellationException
import io.ktor.utils.io.invokeOnCompletion
import io.ktor.utils.io.isCancelled
import io.ktor.utils.io.isCompleted
import io.ktor.utils.io.pool.useInstance
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.reader
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import org.erwinkok.libp2p.core.network.StreamResetException
import org.erwinkok.libp2p.core.network.streammuxer.MuxedStream
import org.erwinkok.libp2p.muxer.mplex.Frame.Companion.CloseInitiatorTag
import org.erwinkok.libp2p.muxer.mplex.Frame.Companion.CloseReceiverTag
import org.erwinkok.libp2p.muxer.mplex.Frame.Companion.MessageInitiatorTag
import org.erwinkok.libp2p.muxer.mplex.Frame.Companion.MessageReceiverTag
import org.erwinkok.libp2p.muxer.mplex.Frame.Companion.ResetInitiatorTag
import org.erwinkok.libp2p.muxer.mplex.Frame.Companion.ResetReceiverTag
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
    private val inputChannel = Channel<ByteArray>(16)
    private val context = Job(scope.coroutineContext[Job])
    private val closeFlag = atomic(false)
    private val actualCloseFlag = atomic(false)
    private val readerJob = atomic<ReaderJob?>(null)
    private val writerJob = atomic<WriterJob?>(null)

    override val id
        get() = mplexStreamId.toString()

    override val jobContext: Job
        get() = context

    override val input: ByteReadChannel = ByteChannel(false).also {
        attachForReading(it)
    }

    override val output: ByteWriteChannel = ByteChannel(false).also {
        attachForWriting(it)
    }

    fun actualClose(): Throwable? {
        return try {
            close()
            inputChannel.close()
            null
        } catch (cause: Throwable) {
            cause
        }
    }

    private fun attachForReadingImpl(channel: ByteChannel): WriterJob =
        scope.writer(context + CoroutineName("mplex-stream-input-loop"), channel) {
            inputDataLoop(this.channel)
        }.apply {
            invokeOnCompletion {
                if (readerJob.value?.isCompleted == true) {
                    mplexMultiplexer.removeStream(mplexStreamId)
                }
            }
        }

    private fun attachForWritingImpl(channel: ByteChannel): ReaderJob =
        scope.reader(context + CoroutineName("mplex-stream-output-loop"), channel) {
            outputDataLoop(this.channel)
        }.apply {
            invokeOnCompletion {
                if (writerJob.value?.isCompleted == true) {
                    mplexMultiplexer.removeStream(mplexStreamId)
                }
            }
        }

    private fun attachForReading(channel: ByteChannel): WriterJob {
        return attachFor("reading", channel, writerJob, ::attachForReadingImpl, ::checkCompleted)
    }

    private fun attachForWriting(channel: ByteChannel): ReaderJob {
        return attachFor("writing", channel, readerJob, ::attachForWritingImpl, ::checkCompleted)
    }

    private inline fun <J : ChannelJob> attachFor(
        name: String,
        channel: ByteChannel,
        ref: AtomicRef<J?>,
        producer: (ByteChannel) -> J,
        crossinline checkClosed: () -> Unit,
    ): J {
        if (closeFlag.value) {
            val e = IOException("Connection closed")
            channel.close(e)
            throw e
        }
        val j = producer(channel)
        if (!ref.compareAndSet(null, j)) {
            val e = IllegalStateException("$name channel has already been set")
            j.cancel()
            throw e
        }
        if (closeFlag.value) {
            val e = IOException("Connection closed")
            j.cancel()
            channel.close(e)
            throw e
        }
        channel.attachJob(j)
        j.invokeOnCompletion { checkClosed() }
        return j
    }

    private fun checkCompleted() {
        if (closeFlag.value && input.isClosedForRead && output.isClosedForWrite) {
            if (actualCloseFlag.compareAndSet(false, true)) {
                val e1 = input.closedCause // readerJob.exception
                val e2 = output.closedCause // writerJob.exception
                val e3 = actualClose()
                val combined = combine(combine(e1, e2), e3)
//                if (combined == null) {
//                    context.complete()
//                } else {
//                    context.complete() // Exceptionally(combined)
//                }
            }
        }
    }

    private fun combine(e1: Throwable?, e2: Throwable?): Throwable? = when {
        e1 == null -> e2
        e2 == null -> e1
        e1 === e2 -> e1
        else -> {
            e1.addSuppressed(e2)
            e1
        }
    }

//    private inline val AtomicRef<out ChannelJob?>.completedOrNotStarted: Boolean
//        get() = value.let { it == null || it.isCompleted }

    private inline val AtomicRef<out ChannelJob?>.exception: Throwable?
        get() = value?.takeIf { it.isCancelled }
            ?.getCancellationException()?.cause

    private suspend fun inputDataLoop(channel: ByteWriteChannel) {
        while (!inputChannel.isClosedForReceive && !channel.isClosedForWrite) {
            try {
                inputChannel.consumeEach {
                    channel.writeFully(it)
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
                    val data = buffer.moveToByteArray()
                    val type = if (mplexStreamId.initiator) MessageInitiatorTag else MessageReceiverTag
                    val messageFrame = Frame.MessageFrame(mplexStreamId, data, type)
                    outputChannel.send(messageFrame)
                }
            } catch (_: CancellationException) {
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
            if (channel.closedCause?.cause is StreamResetException) {
                val type = if (mplexStreamId.initiator) ResetInitiatorTag else ResetReceiverTag
                outputChannel.send(Frame.ResetFrame(mplexStreamId, type))
            } else {
                val type = if (mplexStreamId.initiator) CloseInitiatorTag else CloseReceiverTag
                outputChannel.send(Frame.CloseFrame(mplexStreamId, type))
            }
        }
    }

    override fun reset() {
        if (closeFlag.compareAndSet(false, true)) {
            scope.launch(CoroutineName("conn-close")) {
                inputChannel.cancel()
                output.close(StreamResetException())
                input.cancel(StreamResetException())
                context.complete()
            }
        }
    }

    @OptIn(InternalAPI::class)
    override fun close() {
        if (closeFlag.compareAndSet(false, true)) {
            scope.launch(CoroutineName("conn-close")) {
                inputChannel.cancel()
                output.flushAndClose()
                input.cancel()
                context.complete()
            }
        }
    }

    override fun toString(): String {
        return "mplex-<$mplexStreamId>"
    }

    internal suspend fun remoteSendsNewMessage(body: ByteArray): Boolean {
        if (inputChannel.isClosedForSend) {
            return false
        }
        inputChannel.send(body)
        return true
    }

    internal fun remoteClosesWriting() {
        inputChannel.close()
    }

    internal fun remoteResetsStream() {
        inputChannel.cancel()
        input.cancel(StreamResetException())
        output.close(StreamResetException())
        context.completeExceptionally(StreamResetException())
    }
}
