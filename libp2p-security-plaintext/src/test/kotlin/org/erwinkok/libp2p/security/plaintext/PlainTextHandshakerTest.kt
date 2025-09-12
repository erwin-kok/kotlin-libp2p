// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.security.plaintext

import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.securitymuxer.SecureConnection
import org.erwinkok.libp2p.core.network.securitymuxer.SecureTransport
import org.erwinkok.libp2p.crypto.CryptoUtil
import org.erwinkok.libp2p.crypto.KeyType
import org.erwinkok.libp2p.testing.TestConnection
import org.erwinkok.result.Result
import org.erwinkok.result.coAssertErrorResultMatches
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PlainTextHandshakerTest {
    @Test
    fun connections() = runTest {
        val clientTpt = newTestTransport(this, KeyType.RSA, 2048)
        val serverTpt = newTestTransport(this, KeyType.ED25519, 1024)
        val (client, server) = connect(this, clientTpt, serverTpt, serverTpt.localIdentity.peerId, null)
        val clientConnection = client.expectNoErrors()
        val serverConnection = server.expectNoErrors()
        testIds(clientTpt, serverTpt, clientConnection, serverConnection)
        testKeys(serverTpt, clientConnection, serverConnection)
        testReadWrite(clientConnection, serverConnection)
    }

    @Test
    fun peerIdMatchInbound() = runTest {
        val clientTpt = newTestTransport(this, KeyType.RSA, 2048)
        val serverTpt = newTestTransport(this, KeyType.ED25519, 1024)
        val (client, server) = connect(this, clientTpt, serverTpt, serverTpt.localIdentity.peerId, clientTpt.localIdentity.peerId)
        val clientConnection = client.expectNoErrors()
        val serverConnection = server.expectNoErrors()
        testIds(clientTpt, serverTpt, clientConnection, serverConnection)
        testKeys(serverTpt, clientConnection, serverConnection)
        testReadWrite(clientConnection, serverConnection)
    }

    @Test
    fun peerIdMismatchInbound() = runTest {
        val (_, pub) = CryptoUtil.generateKeyPair(KeyType.ED25519, 1024).expectNoErrors()
        val randomPeerId = PeerId.fromPublicKey(pub).expectNoErrors()
        val clientTpt = newTestTransport(this, KeyType.RSA, 2048)
        val serverTpt = newTestTransport(this, KeyType.ED25519, 1024)
        coAssertErrorResultMatches(Regex("Remote peer sent unexpected PeerId. expected=\\w+ received=\\w+")) { connect(this, clientTpt, serverTpt, serverTpt.localIdentity.peerId, randomPeerId).server }
    }

    @Test
    fun peerIdMismatchOutbound() = runTest {
        val (_, pub) = CryptoUtil.generateKeyPair(KeyType.ED25519, 1024).expectNoErrors()
        val randomPeerId = PeerId.fromPublicKey(pub).expectNoErrors()
        val clientTpt = newTestTransport(this, KeyType.RSA, 2048)
        val serverTpt = newTestTransport(this, KeyType.ED25519, 1024)
        coAssertErrorResultMatches(Regex("Remote peer sent unexpected PeerId. expected=\\w+ received=\\w+")) { connect(this, clientTpt, serverTpt, randomPeerId, null).client }
    }

    private suspend fun testReadWrite(clientConn: SecureConnection, serverConn: SecureConnection) {
        val before = "hello world".toByteArray()
        clientConn.output.writeFully(before)
        clientConn.output.flush()
        val after = ByteArray(before.size)
        serverConn.input.readFully(after)
        assertArrayEquals(before, after, "message mismatch")
    }

    private fun testKeys(serverTpt: SecureTransport, clientConn: SecureConnection, serverConn: SecureConnection) {
        assertEquals(serverConn.localIdentity.privateKey, serverTpt.localIdentity.privateKey, "private key mismatch")
        assertEquals(serverConn.localIdentity.privateKey.publicKey, clientConn.remoteIdentity.publicKey, "public key mismatch")
    }

    private fun testIds(clientTpt: SecureTransport, serverTpt: SecureTransport, clientConn: SecureConnection, serverConn: SecureConnection) {
        assertEquals(clientConn.localIdentity.peerId, clientTpt.localIdentity.peerId, "Client Local PeerId mismatch.")
        assertEquals(clientConn.remoteIdentity.peerId, serverTpt.localIdentity.peerId, "Client Remote PeerId mismatch.")
        assertEquals(clientConn.localIdentity.peerId, serverConn.remoteIdentity.peerId, "Server Local PeerId mismatch.")
    }

    private suspend fun connect(coroutineScope: CoroutineScope, clientTpt: SecureTransport, serverTpt: SecureTransport, localPeer: PeerId, remotePeer: PeerId?): ConnectInfo {
        val connection = TestConnection()
        var client: Result<SecureConnection>? = null
        val job = coroutineScope.launch {
            client = clientTpt.secureOutbound(connection.local, localPeer)
        }
        val server = serverTpt.secureInbound(connection.remote, remotePeer)
        job.join()
        return ConnectInfo(client!!, server)
    }

    private fun newTestTransport(coroutineScope: CoroutineScope, type: KeyType, bits: Int): SecureTransport {
        val identity = LocalIdentity.random(type, bits).expectNoErrors()
        return PlainTextSecureTransport.create(coroutineScope, identity).expectNoErrors()
    }

    internal data class ConnectInfo(
        val client: Result<SecureConnection>,
        val server: Result<SecureConnection>,
    )
}
