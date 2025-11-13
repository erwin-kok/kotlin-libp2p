package org.erwinkok.libp2p.core.network

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
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import org.erwinkok.libp2p.core.base.AwaitableClosable

abstract class ConnectionBase(
    protected val scope: CoroutineScope,
) : AwaitableClosable {
    protected val context = Job(scope.coroutineContext[Job])
    private val closeFlag = atomic(false)
    private val actualCloseFlag = atomic(false)
    private val readerJob = atomic<ReaderJob?>(null)
    private val writerJob = atomic<WriterJob?>(null)

    override val jobContext: Job get() = context

    val input: ByteReadChannel = ByteChannel(false).also {
        attachForReading(it)
    }

    val output: ByteWriteChannel = ByteChannel(false).also {
        attachForWriting(it)
    }

    @OptIn(InternalAPI::class)
    override fun close() {
        if (closeFlag.compareAndSet(expect = false, update = true)) {
            scope.launch(CoroutineName("conn-close")) {
                readerJob.value?.flushAndClose()
                writerJob.value?.cancel()
                checkCompleted()
            }
        }
    }

    abstract fun attachForReadingImpl(channel: ByteChannel): WriterJob

    abstract fun attachForWritingImpl(channel: ByteChannel): ReaderJob

    abstract fun actualClose(): Throwable?

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
        if (closeFlag.value && readerJob.completedOrNotStarted && writerJob.completedOrNotStarted) {
            if (actualCloseFlag.compareAndSet(expect = false, update = true)) {
                val e1 = readerJob.exception
                val e2 = writerJob.exception
                val e3 = actualClose()
                val combined = combine(combine(e1, e2), e3)
                if (combined == null) {
                    context.complete()
                } else {
                    context.completeExceptionally(combined)
                }
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

    private inline val AtomicRef<out ChannelJob?>.completedOrNotStarted: Boolean
        get() = value.let { it == null || it.isCompleted }

    private inline val AtomicRef<out ChannelJob?>.exception: Throwable?
        get() = value?.takeIf { it.isCancelled }
            ?.getCancellationException()?.cause
}
