// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
@file:OptIn(DelicateCoroutinesApi::class)

package org.erwinkok.libp2p.core.protocol.ping

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.host.Host
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.Stream
import org.erwinkok.libp2p.core.resourcemanager.ResourceScope.Companion.reservationPriorityAlways
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.Err
import org.erwinkok.result.Error
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.erwinkok.result.getOrElse
import org.erwinkok.result.onFailure
import org.erwinkok.result.onSuccess
import java.nio.ByteBuffer
import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

class PingService(
    private val scope: CoroutineScope,
    private val host: Host,
) : AwaitableClosable {
    private val _context = Job(scope.coroutineContext[Job])

    override val jobContext: Job
        get() = _context

    init {
        host.setStreamHandler(PingId) { stream ->
            pingHandler(stream)
        }
    }

    private suspend fun pingHandler(stream: Stream) = withContext(_context) {
        stream.streamScope.setService(ServiceName)
            .onFailure {
                logger.debug { "error attaching stream to ping service: ${errorMessage(it)}" }
                stream.reset()
                return@withContext
            }
        stream.streamScope.reserveMemory(PingSize, reservationPriorityAlways)
            .onFailure {
                logger.debug { "error reserving memory for ping stream: ${errorMessage(it)}" }
                stream.reset()
                return@withContext
            }
        val buffer = ByteBuffer.allocate(PingSize)
        while (scope.isActive && !stream.input.isClosedForRead && !stream.output.isClosedForWrite) {
            try {
                val timeout = withTimeoutOrNull(pingTimeout) {
                    buffer.clear()
                    stream.input.readFully(buffer)
                    buffer.flip()
                    stream.output.writeFully(buffer)
                    stream.output.flush()
                }
                if (timeout == null) {
                    logger.debug { "Ping timeout with peer ${stream.connection.remoteIdentity.peerId}" }
                    stream.close()
                }
            } catch (_: Exception) {
                // This is fine. The stream could be open and waiting for input in readFully.
                // The peer can (and this is usual) close the stream. This will generate an
                // exception, but this is not a protocol error.
                stream.close()
            }
        }
        stream.streamScope.releaseMemory(PingSize)
    }

    data class PingResult(
        val rtt: Long? = null,
        val error: Error? = null,
    )

    fun ping(peerId: PeerId, delay: Duration): Flow<PingResult> = channelFlow {
        val stream = host.newStream(peerId, PingId)
            .getOrElse {
                send(PingResult(error = it))
                close(it)
                return@channelFlow
            }
        stream.streamScope.setService(ServiceName)
            .getOrElse {
                logger.debug { "error attaching stream to ping service: ${errorMessage(it)}" }
                stream.reset()
                send(PingResult(error = it))
                close(it)
                return@channelFlow
            }
        launch(_context + CoroutineName("ping-service-$peerId")) {
            try {
                while (_context.isActive && !isClosedForSend && !stream.input.isClosedForRead && !stream.output.isClosedForWrite) {
                    ping(stream)
                        .onSuccess {
                            host.peerstore.recordLatency(peerId, it)
                            send(PingResult(rtt = it))
                            delay(delay)
                        }
                        .onFailure {
                            logger.warn { "Error occurred: ${errorMessage(it)}" }
                            stream.reset()
                            close()
                        }
                }
            } catch (_: CancellationException) {
                // Do nothing...
            }
            close()
        }
        awaitClose()
        stream.close()
    }

    override fun close() {
        _context.cancel()
    }

    private suspend fun ping(stream: Stream): Result<Long> {
        stream.streamScope.reserveMemory(2 * PingSize, reservationPriorityAlways)
            .getOrElse {
                return Err("error reserving memory for ping stream: ${errorMessage(it)}")
            }
        val input = ByteArray(PingSize)
        val output = Random.nextBytes(PingSize)
        val elapsed = measureNanoTime {
            stream.output.writeFully(output)
            stream.output.flush()
            try {
                stream.input.readFully(input)
            } catch (e: Exception) {
                stream.streamScope.releaseMemory(2 * PingSize)
                return Err("while waiting for ping reply: ${errorMessage(e)}")
            }
        }
        if (!input.contentEquals(output)) {
            stream.streamScope.releaseMemory(2 * PingSize)
            return Err("ping packet was incorrect")
        }
        stream.streamScope.releaseMemory(2 * PingSize)
        return Ok(elapsed)
    }

    companion object {
        private const val ServiceName = "libp2p.ping"
        private const val PingSize = 32
        private val PingId = ProtocolId.of("/ipfs/ping/1.0.0")
        private val pingTimeout = 60.seconds
    }
}
