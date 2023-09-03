// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.security.noise

import com.southernstorm.noise.protocol.CipherState
import io.ktor.network.util.DefaultByteBufferPool
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.ReaderJob
import io.ktor.utils.io.WriterJob
import io.ktor.utils.io.cancel
import io.ktor.utils.io.close
import io.ktor.utils.io.core.internal.ChunkBuffer
import io.ktor.utils.io.errors.IOException
import io.ktor.utils.io.pool.ObjectPool
import io.ktor.utils.io.pool.useInstance
import io.ktor.utils.io.reader
import io.ktor.utils.io.writer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import mu.KotlinLogging
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.host.RemoteIdentity
import org.erwinkok.libp2p.core.network.Connection
import org.erwinkok.libp2p.core.network.securitymuxer.SecureConnection
import org.erwinkok.result.errorMessage
import java.net.SocketException
import java.nio.ByteBuffer
import kotlin.math.min

private val logger = KotlinLogging.logger {}

class NoiseSecureConnection(
    private val scope: CoroutineScope,
    private val insecureConnection: Connection,
    private val receiverCipherState: CipherState,
    private val senderCipherState: CipherState,
    override val localIdentity: LocalIdentity,
    override val remoteIdentity: RemoteIdentity,
    override val pool: ObjectPool<ChunkBuffer> = insecureConnection.pool,
) : AwaitableClosable, SecureConnection {
    private val _context = Job(scope.coroutineContext[Job])
    override val jobContext: Job get() = _context
    override val input: ByteReadChannel = ByteChannel(false).also { attachForReading(it) }
    override val output: ByteWriteChannel = ByteChannel(false).also { attachForWriting(it) }

    private fun attachForReading(channel: ByteChannel): WriterJob =
        scope.writer(_context + CoroutineName("noise-conn-input-loop"), channel) {
            appDataInputLoop(this.channel)
        }.apply {
            invokeOnCompletion {
                insecureConnection.input.cancel()
            }
        }

    private fun attachForWriting(channel: ByteChannel): ReaderJob =
        scope.reader(_context + CoroutineName("noise-conn-output-loop"), channel) {
            appDataOutputLoop(this.channel)
        }.apply {
            invokeOnCompletion {
                insecureConnection.output.close()
            }
        }

    private suspend fun appDataInputLoop(channel: ByteWriteChannel) {
        val decryptBuffer = ByteArray(MaxPlaintextLength + senderCipherState.macLength)
        while (!channel.isClosedForWrite) {
            try {
                val length = insecureConnection.input.readShort().toInt()
                require(length < MaxPlaintextLength)
                insecureConnection.input.readFully(decryptBuffer, 0, length)
                val decryptLength = receiverCipherState.decryptWithAd(null, decryptBuffer, 0, decryptBuffer, 0, length)
                channel.writeFully(decryptBuffer, 0, decryptLength)
                channel.flush()
            } catch (e: CancellationException) {
                break
            } catch (e: ClosedReceiveChannelException) {
                // insecureConnection.input has been closed
                channel.close(IOException("Failed reading from closed connection"))
                break
            } catch (e: SocketException) {
                channel.close(IOException("Failed reading from closed connection"))
                break
            } catch (e: Exception) {
                logger.warn { "Unexpected error occurred in noise input loop: ${errorMessage(e)}" }
                throw e
            }
        }
        val closedCause = channel.closedCause
        if (closedCause != null && closedCause !is IOException) {
            throw closedCause
        }
    }

    private suspend fun appDataOutputLoop(channel: ByteReadChannel): Unit = DefaultByteBufferPool.useInstance { buffer: ByteBuffer ->
        val encryptBuffer = ByteArray(MaxPlaintextLength + senderCipherState.macLength)
        while (!channel.isClosedForRead) {
            buffer.clear()
            try {
                val size = channel.readAvailable(buffer)
                if (size > 0) {
                    buffer.flip()
                    var left = size
                    while (left > 0) {
                        val length = min(left, MaxPlaintextLength)
                        buffer.get(encryptBuffer, 0, length)
                        val encryptLength = senderCipherState.encryptWithAd(null, encryptBuffer, 0, encryptBuffer, 0, length)
                        insecureConnection.output.writeShort(encryptLength.toShort())
                        insecureConnection.output.writeFully(encryptBuffer, 0, encryptLength)
                        left -= length
                    }
                }
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                logger.warn { "Unexpected error occurred in noise output loop: ${errorMessage(e)}" }
                throw e
            }
            insecureConnection.output.flush()
        }
        if (!channel.isClosedForRead) {
            channel.cancel(IOException("Failed writing to closed connection"))
        } else {
            channel.closedCause?.let { throw it }
        }
    }

    override fun close() {
        input.cancel()
        output.close()
        insecureConnection.close()
        _context.complete()
    }

    companion object {
        private const val MaxTransportMsgLength = 0xffff
        private const val MaxPlaintextLength = MaxTransportMsgLength - 16
    }
}
