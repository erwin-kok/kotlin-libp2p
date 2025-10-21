// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
@file:OptIn(ExperimentalCoroutinesApi::class)

package org.erwinkok.libp2p.security.noise

import io.ktor.utils.io.ClosedReadChannelException
import io.ktor.utils.io.ClosedWriteChannelException
import io.ktor.utils.io.cancel
import io.ktor.utils.io.core.toByteArray
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readPacket
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.io.readByteArray
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.network.securitymuxer.SecureConnection
import org.erwinkok.libp2p.crypto.KeyType
import org.erwinkok.libp2p.testing.TestConnection
import org.erwinkok.result.coAssertErrorResultMatches
import org.erwinkok.result.expectNoErrors
import org.erwinkok.util.Tuple
import org.erwinkok.util.Tuple2
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.experimental.xor
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

internal class NoiseSecureConnectionTest {
    @Test
    fun ids() = runTest {
        val initTransport = newTestTransport(this, KeyType.ED25519, 2048)
        val respTransport = newTestTransport(this, KeyType.ED25519, 2048)
        val (initConnection, responseConnection) = connect(this, initTransport, respTransport)
        assertEquals(initConnection.localIdentity.peerId, initTransport.localIdentity.peerId)
        assertEquals(responseConnection.localIdentity.peerId, respTransport.localIdentity.peerId)
        assertEquals(initConnection.localIdentity.peerId, responseConnection.remoteIdentity.peerId)
        assertEquals(responseConnection.remoteIdentity.peerId, initConnection.localIdentity.peerId)
        initConnection.close()
        responseConnection.close()
    }

    @Test
    fun keys() = runTest {
        val initTransport = newTestTransport(this, KeyType.ED25519, 2048)
        val respTransport = newTestTransport(this, KeyType.ED25519, 2048)
        val (initConnection, responseConnection) = connect(this, initTransport, respTransport)
        val sk = responseConnection.localIdentity.privateKey
        val pk = sk.publicKey
        assertEquals(sk, respTransport.localIdentity.privateKey)
        assertEquals(pk, initConnection.remoteIdentity.publicKey)
        initConnection.close()
        responseConnection.close()
    }

    @Test
    fun peerIdMatch() = runTest {
        val initTransport = newTestTransport(this, KeyType.ED25519, 2048)
        val respTransport = newTestTransport(this, KeyType.ED25519, 2048)
        val (initConnection, responseConnection) = connect(this, initTransport, respTransport)
        val job = launch {
            assertEquals(initConnection.remoteIdentity.peerId, respTransport.localIdentity.peerId)
            val packet = initConnection.input.readPacket(6)
            assertEquals("foobar", String(packet.readByteArray()))
        }
        assertEquals(responseConnection.remoteIdentity.peerId, initTransport.localIdentity.peerId)
        val message = "foobar".toByteArray()
        responseConnection.output.writeFully(message)
        responseConnection.output.flush()
        job.join()
        initConnection.close()
        responseConnection.close()
    }

    @Test
    fun peerIdMismatchOutboundFailsHandshake() = runTest {
        val initTransport = newTestTransport(this, KeyType.ED25519, 2048)
        val respTransport = newTestTransport(this, KeyType.ED25519, 2048)
        val connection = TestConnection()
        val job = this.launch {
            respTransport.secureInbound(connection.remote, null)
        }
        val mismatchPeerId = LocalIdentity.random().expectNoErrors().peerId
        coAssertErrorResultMatches(Regex("PeerId mismatch: expected \\w+, but remote key matches \\w+")) { initTransport.secureOutbound(connection.local, mismatchPeerId) }
        job.cancelAndJoin()
    }

    @Test
    fun peerIdMismatchInboundFailsHandshake() = runTest {
        val initTransport = newTestTransport(this, KeyType.ED25519, 2048)
        val respTransport = newTestTransport(this, KeyType.ED25519, 2048)
        val connection = TestConnection()
        val deferred = this.async {
            initTransport.secureOutbound(connection.local, respTransport.localIdentity.peerId).expectNoErrors()
        }
        val mismatchPeerId = LocalIdentity.random().expectNoErrors().peerId
        coAssertErrorResultMatches(Regex("PeerId mismatch: expected \\w+, but remote key matches \\w+")) { respTransport.secureInbound(connection.remote, mismatchPeerId) }
        deferred.await().close()
    }

    @Test
    fun largePayloads() = runTest {
        val initTransport = newTestTransport(this, KeyType.ED25519, 2048)
        val respTransport = newTestTransport(this, KeyType.ED25519, 2048)
        val (initConnection, responseConnection) = connect(this, initTransport, respTransport)
        val random1 = Random.nextBytes(100000)
        val random2 = ByteArray(random1.size)
        val job = launch {
            responseConnection.input.readFully(random2)
        }
        initConnection.output.writeFully(random1)
        initConnection.output.flush()
        job.join()
        assertArrayEquals(random1, random2)
        initConnection.close()
        responseConnection.close()
    }

    @Test
    fun largePayloadsChunkedSmall() = runTest {
        val initTransport = newTestTransport(this, KeyType.ED25519, 2048)
        val respTransport = newTestTransport(this, KeyType.ED25519, 2048)
        val (initConnection, responseConnection) = connect(this, initTransport, respTransport)
        val random1 = Random.nextBytes(100000)
        val random2 = ByteArray(random1.size)
        val job = launch {
            var index = 0
            while (index < random2.size) {
                val packet = responseConnection.input.readPacket(min(7, random2.size - index))
                val bytes = packet.readByteArray()
                bytes.copyInto(random2, index, 0, bytes.size)
                index += bytes.size
            }
        }
        initConnection.output.writeFully(random1)
        initConnection.output.flush()
        job.join()
        assertArrayEquals(random1, random2)
        initConnection.close()
        responseConnection.close()
    }

    @Test
    fun largePayloadsChunkedLarge() = runTest {
        val initTransport = newTestTransport(this, KeyType.ED25519, 2048)
        val respTransport = newTestTransport(this, KeyType.ED25519, 2048)
        val (initConnection, responseConnection) = connect(this, initTransport, respTransport)
        val random1 = Random.nextBytes(100000)
        val random2 = ByteArray(random1.size)
        val job = launch {
            var index = 0
            while (index < random2.size) {
                val packet = responseConnection.input.readPacket(min(70000, random2.size - index))
                val bytes = packet.readByteArray()
                bytes.copyInto(random2, index, 0, bytes.size)
                index += bytes.size
            }
        }
        initConnection.output.writeFully(random1)
        initConnection.output.flush()
        job.join()
        assertArrayEquals(random1, random2)
        initConnection.close()
        responseConnection.close()
    }

    @Test
    fun pingPong() = runTest(timeout = 1.minutes) {
        repeat(1000) {
            val initTransport = newTestTransport(this, KeyType.ED25519, 2048)
            val respTransport = newTestTransport(this, KeyType.ED25519, 2048)
            val (initConnection, responseConnection) = connect(this, initTransport, respTransport)
            val random1 = Random.nextBytes(100000)
            val job = launch {
                val random2 = ByteArray(random1.size)
                responseConnection.input.readFully(random2)
                for (j in random2.indices) {
                    random2[j] = random2[j] xor 241.toByte()
                }
                responseConnection.input.cancel()
                responseConnection.output.writeFully(random2)
                responseConnection.output.flush()
                // Do not close the responseConnection.output here, because the "readFully" could fail, if the close just happened before the read.
            }
            initConnection.output.writeFully(random1)
            initConnection.output.flush()
            val random3 = ByteArray(random1.size)
            initConnection.input.readFully(random3)
            job.join()
            for (j in random3.indices) {
                random3[j] = random3[j] xor 241.toByte()
            }
            assertArrayEquals(random1, random3)
            initConnection.close()
            responseConnection.close()
        }
    }

    @Test
    fun pingPongErrorReadingRemoteClosesOutput() = runTest {
        val initTransport = newTestTransport(this, KeyType.ED25519, 2048)
        val respTransport = newTestTransport(this, KeyType.ED25519, 2048)
        val (initConnection, responseConnection) = connect(this, initTransport, respTransport)
        val random1 = Random.nextBytes(100000)
        val job = launch {
            val random2 = ByteArray(random1.size)
            responseConnection.input.readFully(random2)
            responseConnection.output.flushAndClose()
        }
        initConnection.output.writeFully(random1)
        initConnection.output.flush()
        val random2 = ByteArray(random1.size)
        val exception = assertThrows<IOException> {
            initConnection.input.readFully(random2)
        }
        assertEquals("Not enough data available", exception.message)
        job.join()
        initConnection.close()
        responseConnection.close()
    }

    @Test
    fun readErrorAfterLocalCancelsInput() = runTest {
        val initTransport = newTestTransport(this, KeyType.ED25519, 2048)
        val respTransport = newTestTransport(this, KeyType.ED25519, 2048)
        val (initConnection, responseConnection) = connect(this, initTransport, respTransport)
        val random1 = Random.nextBytes(100000)
        val random2 = ByteArray(random1.size)
        val job = launch {
            responseConnection.input.readFully(random2)
        }
        initConnection.input.cancel()
        val exception = assertThrows<ClosedReadChannelException> {
            initConnection.input.readPacket(10)
        }
        assertEquals("Channel was cancelled", exception.message)
        initConnection.output.writeFully(random1)
        initConnection.output.flush()
        job.join()
        assertArrayEquals(random1, random2)
        initConnection.close()
        responseConnection.close()
    }

    @Test
    fun readErrorAfterLocalClosesOutput() = runTest {
        val initTransport = newTestTransport(this, KeyType.ED25519, 2048)
        val respTransport = newTestTransport(this, KeyType.ED25519, 2048)
        val (initConnection, responseConnection) = connect(this, initTransport, respTransport)
        val random1 = Random.nextBytes(100000)
        val job = launch {
            responseConnection.output.writeFully(random1)
            responseConnection.output.flush()
        }
        initConnection.output.flushAndClose()
        val random2 = ByteArray(random1.size)
        initConnection.input.readFully(random2)
        assertThrows<ClosedWriteChannelException> {
            initConnection.output.writeFully(random1)
            initConnection.output.flush()
        }
        job.join()
        assertArrayEquals(random1, random2)
        initConnection.close()
        responseConnection.close()
    }

    @Suppress("SameParameterValue")
    private fun newTestTransport(scope: CoroutineScope, type: KeyType, bits: Int): NoiseTransport {
        val identity = LocalIdentity.random(type, bits).expectNoErrors()
        return NoiseTransport(scope, identity)
    }

    private suspend fun connect(scope: CoroutineScope, initTransport: NoiseTransport, responseTransport: NoiseTransport): Tuple2<SecureConnection, SecureConnection> {
        val connection = TestConnection()
        val deferred = scope.async {
            initTransport.secureOutbound(connection.local, responseTransport.localIdentity.peerId).expectNoErrors()
        }
        val responseConnection = responseTransport.secureInbound(connection.remote, null).expectNoErrors()
        val initConnection = deferred.await()
        return Tuple(initConnection, responseConnection)
    }
}
