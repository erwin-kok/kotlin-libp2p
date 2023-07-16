// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
@file:Suppress("UNUSED_PARAMETER")
@file:OptIn(DelicateCoroutinesApi::class)

package org.erwinkok.libp2p.testing.testsuites.transport

import io.ktor.utils.io.close
import io.ktor.utils.io.core.toByteArray
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.NetworkProtocol
import org.erwinkok.libp2p.core.network.StreamResetException
import org.erwinkok.libp2p.core.network.streammuxer.MuxedStream
import org.erwinkok.libp2p.core.network.transport.Listener
import org.erwinkok.libp2p.core.network.transport.Transport
import org.erwinkok.libp2p.core.network.transport.TransportConnection
import org.erwinkok.libp2p.testing.ConnectionBuilder
import org.erwinkok.multiformat.multicodec.Multicodec
import org.erwinkok.result.coAssertErrorResultMatches
import org.erwinkok.result.expectNoErrors
import org.erwinkok.result.onFailure
import org.erwinkok.result.onSuccess
import org.erwinkok.util.Tuple
import org.erwinkok.util.Tuple2
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.function.Executable
import java.util.concurrent.CancellationException
import java.util.stream.Stream
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class TransportTestSuite(private val transportName: String) {
    private val TestData = "this is some test data".toByteArray()

    private class SubTestExecutable(
        private val ca: ConnectionBuilder,
        private val cb: ConnectionBuilder,
        private val multiaddress: InetMultiaddress,
        private val localIdentity: LocalIdentity,
        private val exec: suspend (CoroutineScope, Transport, Transport, InetMultiaddress, LocalIdentity) -> Unit,
    ) : Executable {
        override fun execute() = runBlocking {
            val ta = ca.build(this, Dispatchers.IO)
            val tb = cb.build(this, Dispatchers.IO)
            exec(this, ta.transport, tb.transport, multiaddress, localIdentity)
            tb.transport.close()
            ta.transport.close()
        }
    }

    private val subtests = listOf(
        ::subtestProtocols,
        ::subtestBasic,
        ::subtestPingPong,
        ::subtestCancel,
        ::subtestStress1Connection1Stream1Message,
        ::subtestStress1Connection1Stream1000Messages,
        ::subtestStress1Connection15Streams100Messages,
        ::subtestStress50Connections10Stream100Messages,
        ::subtestStress50Connections1Stream1000Messages,
        ::subtestStreamOpenStress,
        ::subtestStreamReset,
    )

    fun testTransport(ca: ConnectionBuilder, cb: ConnectionBuilder, address: String, localIdentity: LocalIdentity): Stream<DynamicTest> {
        val multiAddress = InetMultiaddress.fromString(address).expectNoErrors()
        val tests = mutableListOf<DynamicTest>()
        for (test in subtests) {
            tests.add(
                DynamicTest.dynamicTest(
                    "Test: ${test.name} ($transportName)",
                    SubTestExecutable(ca, cb, multiAddress, localIdentity, test),
                ),
            )
        }
        return tests.stream()
    }

    private suspend fun subtestProtocols(scope: CoroutineScope, ta: Transport, tb: Transport, multiaddress: InetMultiaddress, localIdentity: LocalIdentity) {
        val rawIPAddress = InetMultiaddress.fromString("/ip4/1.2.3.4").expectNoErrors()
        assertFalse(ta.canDial(rawIPAddress))
        assertFalse(tb.canDial(rawIPAddress))
        val protocol = multiaddress.networkProtocol
        assertTrue(ta.protocols.any { matches(it.codec, protocol) })
        assertTrue(tb.protocols.any { matches(it.codec, protocol) })
    }

    private suspend fun subtestBasic(scope: CoroutineScope, ta: Transport, tb: Transport, multiaddress: InetMultiaddress, localIdentity: LocalIdentity) {
        val (dialConnection, listenConnection) = connect(scope, ta, tb, multiaddress, localIdentity)
        val job = scope.launch {
            val streamB = listenConnection.acceptStream().expectNoErrors()
            val buffer = ByteArray(TestData.size)
            streamB.input.readFully(buffer)
            assertArrayEquals(TestData, buffer)
            streamB.output.writeFully(buffer)
            streamB.output.flush()
            streamB.close()
            listenConnection.close()
        }
        val streamA = dialConnection.openStream().expectNoErrors()
        streamA.output.writeFully(TestData)
        streamA.output.flush()
        streamA.output.close()
        val buffer = ByteArray(TestData.size)
        streamA.input.readFully(buffer)
        assertArrayEquals(TestData, buffer)
        streamA.close()
        dialConnection.close()
        job.join()
    }

    private suspend fun subtestPingPong(scope: CoroutineScope, ta: Transport, tb: Transport, multiaddress: InetMultiaddress, localIdentity: LocalIdentity) {
        val (dialConnection, listenConnection) = connect(scope, ta, tb, multiaddress, localIdentity)
        val job = scope.launch {
            repeat(1000) {
                val streamB = listenConnection.acceptStream().expectNoErrors()
                val buffer = ByteArray(TestData.size)
                streamB.input.readFully(buffer)
                assertArrayEquals(TestData, buffer)
                val nr = streamB.input.readInt()
                assertEquals(it, nr)
                streamB.output.writeFully(buffer)
                streamB.output.writeInt(it)
                streamB.output.flush()
                streamB.close()
            }
            listenConnection.close()
        }
        repeat(1000) {
            val streamA = dialConnection.openStream().expectNoErrors()
            streamA.output.writeFully(TestData)
            streamA.output.flush()
            streamA.output.writeInt(it)
            streamA.output.flush()
            streamA.output.close()
            val buffer = ByteArray(TestData.size)
            streamA.input.readFully(buffer)
            assertArrayEquals(TestData, buffer)
            val nr = streamA.input.readInt()
            assertEquals(it, nr)
            streamA.close()
        }
        dialConnection.close()
        job.join()
    }

    private suspend fun subtestCancel(scope: CoroutineScope, ta: Transport, tb: Transport, multiaddress: InetMultiaddress, localIdentity: LocalIdentity) {
        val job = scope.launch {
            val listener = ta.listen(multiaddress).expectNoErrors()
            assertTrue(tb.canDial(listener.transportAddress))
            cancel(CancellationException("TEST-MESSAGE"))
            coAssertErrorResultMatches(Regex("Could not open connection to [0-9a-z/.]+: TEST-MESSAGE")) { tb.dial(localIdentity.peerId, listener.transportAddress) }
            listener.close()
        }
        job.join()
    }

    private val messageSize = 2048

    private suspend fun fullClose(stream: MuxedStream) {
        stream.output.close()
        val packet = stream.input.readRemaining()
        assertEquals(0, packet.remaining)
        packet.close()
        stream.close()
    }

    private suspend fun serve(scope: CoroutineScope, listener: Listener) {
        val jobs = mutableListOf<Job>()
        var error = false
        while (!error) {
            listener.accept()
                .onFailure { error = true }
                .onSuccess { connection ->
                    jobs.add(
                        scope.launch {
                            echo(connection)
                            connection.close()
                        },
                    )
                }
        }
        jobs.joinAll()
    }

    private suspend fun echo(connection: TransportConnection) {
        val jobs = mutableListOf<Job>()
        var error = false
        while (!error) {
            connection.acceptStream()
                .onFailure { error = true }
                .onSuccess { stream ->
                    while (true) {
                        try {
                            val buffer = ByteArray(messageSize)
                            stream.input.readFully(buffer)
                            stream.output.writeFully(buffer)
                            stream.output.flush()
                        } catch (e: ClosedReceiveChannelException) {
                            break
                        }
                    }
                    stream.close()
                }
        }
        jobs.joinAll()
    }

    private suspend fun openStreamAndReadWrite(scope: CoroutineScope, connection: TransportConnection, msgNum: Int) {
        val stream = connection.openStream().expectNoErrors()
        val dataChannel = Channel<ByteArray>()
        val job = scope.launch {
            writeStream(stream, dataChannel, msgNum)
            dataChannel.close()
        }
        readStream(stream, dataChannel)
        job.join()
        fullClose(stream)
    }

    private suspend fun readStream(stream: MuxedStream, dataChannel: Channel<ByteArray>) {
        val buf2 = ByteArray(messageSize)
        while (!dataChannel.isClosedForReceive) {
            val buf1 = dataChannel.receive()
            stream.input.readFully(buf2)
            assertArrayEquals(buf1, buf2)
        }
    }

    private suspend fun writeStream(stream: MuxedStream, dataChannel: Channel<ByteArray>, msgNum: Int) {
        repeat(msgNum) {
            val buf = Random.nextBytes(messageSize)
            dataChannel.send(buf)
            stream.output.writeFully(buf)
            stream.output.flush()
        }
    }

    private suspend fun openConnectionAndReadWrite(scope: CoroutineScope, msgNum: Int, streamNum: Int, ta: Transport, tb: Transport, multiaddress: InetMultiaddress, localIdentity: LocalIdentity) {
        val listener = ta.listen(multiaddress).expectNoErrors()
        val listenerJob = scope.launch {
            serve(scope, listener)
        }

        assertTrue(tb.canDial(listener.transportAddress))
        val connection = tb.dial(localIdentity.peerId, listener.transportAddress).expectNoErrors()

        val jobs = mutableListOf<Job>()
        repeat(streamNum) {
            jobs.add(
                scope.launch {
                    openStreamAndReadWrite(scope, connection, msgNum)
                },
            )
        }
        jobs.joinAll()

        connection.close()

        listener.close()
        listenerJob.join()
    }

    private suspend fun subtestStress(scope: CoroutineScope, ta: Transport, tb: Transport, multiaddress: InetMultiaddress, localIdentity: LocalIdentity, connNum: Int, streamNum: Int, msgNum: Int) {
        val jobs = mutableListOf<Job>()
        repeat(connNum) {
            jobs.add(
                scope.launch {
                    openConnectionAndReadWrite(scope, msgNum, streamNum, ta, tb, multiaddress, localIdentity)
                },
            )
        }
        jobs.joinAll()
    }

    private suspend fun subtestStress1Connection1Stream1Message(scope: CoroutineScope, ta: Transport, tb: Transport, multiaddress: InetMultiaddress, localIdentity: LocalIdentity) {
        subtestStress(scope, ta, tb, multiaddress, localIdentity, 1, 1, 1)
    }

    private suspend fun subtestStress1Connection1Stream1000Messages(scope: CoroutineScope, ta: Transport, tb: Transport, multiaddress: InetMultiaddress, localIdentity: LocalIdentity) {
        subtestStress(scope, ta, tb, multiaddress, localIdentity, 1, 1, 1000)
    }

    private suspend fun subtestStress1Connection15Streams100Messages(scope: CoroutineScope, ta: Transport, tb: Transport, multiaddress: InetMultiaddress, localIdentity: LocalIdentity) {
        subtestStress(scope, ta, tb, multiaddress, localIdentity, 1, 15, 100)
    }

    private suspend fun subtestStress50Connections1Stream1000Messages(scope: CoroutineScope, ta: Transport, tb: Transport, multiaddress: InetMultiaddress, localIdentity: LocalIdentity) {
        subtestStress(scope, ta, tb, multiaddress, localIdentity, 50, 1, 1000)
    }

    private suspend fun subtestStress50Connections10Stream100Messages(scope: CoroutineScope, ta: Transport, tb: Transport, multiaddress: InetMultiaddress, localIdentity: LocalIdentity) {
        subtestStress(scope, ta, tb, multiaddress, localIdentity, 50, 10, 100)
    }

    private suspend fun subtestStreamOpenStress(scope: CoroutineScope, ta: Transport, tb: Transport, multiaddress: InetMultiaddress, localIdentity: LocalIdentity) {
        val workers = 5
        val count = 10000
        val closeJobs = mutableListOf<Job>()
        val (dialConnection, listenConnection) = connect(scope, ta, tb, multiaddress, localIdentity)
        val job = scope.launch {
            repeat(workers) {
                repeat(count) {
                    val stream = listenConnection.openStream().expectNoErrors()
                    closeJobs.add(
                        scope.launch {
                            fullClose(stream)
                        },
                    )
                }
            }
        }
        repeat(workers * count) {
            val stream = dialConnection.acceptStream().expectNoErrors()
            closeJobs.add(
                scope.launch {
                    fullClose(stream)
                },
            )
        }
        closeJobs.joinAll()
        job.join()
        listenConnection.close()
        dialConnection.close()
    }

    private suspend fun subtestStreamReset(scope: CoroutineScope, ta: Transport, tb: Transport, multiaddress: InetMultiaddress, localIdentity: LocalIdentity) {
        val (dialConnection, listenConnection) = connect(scope, ta, tb, multiaddress, localIdentity)
        val job = scope.launch {
            val stream = listenConnection.openStream().expectNoErrors()
            stream.output.writeFully(byteArrayOf(1, 2, 3, 4, 5))
            delay(2.seconds) // To make sure the reset was received
            val exception = assertThrows<StreamResetException> {
                stream.output.writeFully(byteArrayOf(1, 2, 3, 4, 5))
            }
            assertEquals("Stream was reset", exception.message)
            listenConnection.close()
        }
        val stream = dialConnection.acceptStream().expectNoErrors()
        stream.reset()
        dialConnection.close()
        job.join()
    }

    private fun matches(codec: Multicodec, protocol: NetworkProtocol): Boolean {
        return when (protocol) {
            NetworkProtocol.UDP -> (codec == Multicodec.UDP)
            NetworkProtocol.TCP -> (codec == Multicodec.TCP)
            else -> false
        }
    }

    private suspend fun connect(scope: CoroutineScope, ta: Transport, tb: Transport, multiaddress: InetMultiaddress, localIdentity: LocalIdentity): Tuple2<TransportConnection, TransportConnection> {
        val listener = ta.listen(multiaddress).expectNoErrors()
        val deferred = scope.async {
            listener.accept().expectNoErrors()
        }
        assertTrue(tb.canDial(listener.transportAddress))
        val dialConnection = tb.dial(localIdentity.peerId, listener.transportAddress).expectNoErrors()
        val listenConnection = deferred.await()
        listener.close()
        return Tuple(dialConnection, listenConnection)
    }
}
