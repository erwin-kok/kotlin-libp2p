// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
@file:OptIn(ExperimentalCoroutinesApi::class)

package org.erwinkok.libp2p.transport.tcp

import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.aSocket
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.Direction
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.upgrader.UpgradedTransportConnection
import org.erwinkok.libp2p.core.network.upgrader.Upgrader
import org.erwinkok.libp2p.core.resourcemanager.ConnectionManagementScope
import org.erwinkok.libp2p.core.resourcemanager.NullResourceManager
import org.erwinkok.libp2p.core.resourcemanager.ResourceManager
import org.erwinkok.multiformat.multiaddress.Protocol
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.coAssertErrorResult
import org.erwinkok.result.errorMessage
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.ServerSocket

internal class TcpTransportTest {
    private val validMultiaddress = InetMultiaddress.fromString("/ip4/127.0.0.1/tcp/1234").expectNoErrors()
    private val invalidMultiaddress = InetMultiaddress.fromString("/ip4/127.0.0.1/tcp/0").expectNoErrors()
    private val validPeerId = PeerId.fromString("QmXkJYqRgNvjTbmnS6YzVDDnSQaepQCCaBStB5izqajBea").expectNoErrors()

    private val upgrader = mockk<Upgrader>()
    private val transportConnection = mockk<UpgradedTransportConnection>()
    private val errors = mutableListOf<Throwable>()

    @BeforeEach
    fun setup() {
        errors.clear()
        coEvery { upgrader.upgradeOutbound(any(), any(), any(), any(), any()) } returns Ok(transportConnection)
        coEvery { upgrader.upgradeInbound(any(), any()) } returns Ok(transportConnection)
    }

    @AfterEach
    fun teardown() {
        errors.forEach {
            println("Server error occurred: ${errorMessage(it)}")
        }
        assertTrue(errors.isEmpty())
    }

    @Test
    fun proxy() {
        val transport = TcpTransport.create(mockk(), mockk(), Dispatchers.IO).expectNoErrors()
        assertFalse(transport.proxy)
    }

    @Test
    fun protocols() {
        val transport = TcpTransport.create(mockk(), mockk(), Dispatchers.IO).expectNoErrors()
        assertArrayEquals(arrayOf(Protocol.TCP), transport.protocols.toTypedArray())
    }

    @Test
    fun canDial() {
        val transport = TcpTransport.create(mockk(), mockk(), Dispatchers.IO).expectNoErrors()
        assertTrue(transport.canDial(validMultiaddress))
    }

    @Test
    fun canNotDial() {
        val transport = TcpTransport.create(mockk(), mockk(), Dispatchers.IO).expectNoErrors()
        assertFalse(transport.canDial(invalidMultiaddress))
    }

    @Test
    fun dialInvalidHostname() = runTest {
        val transport = TcpTransport.create(mockk(), mockk(), Dispatchers.IO).expectNoErrors()
        coAssertErrorResult("TcpTransport can only dial/listen to valid tcp addresses, not /ip4/127.0.0.1/tcp/0") {
            transport.dial(validPeerId, invalidMultiaddress)
        }
    }

    @Test
    fun resourceManagerBlocksOpenConnection() = runTest {
        val resourceManager = mockk<ResourceManager>()
        coEvery { resourceManager.openConnection(Direction.DirOutbound, true, any()) } returns Err("An Error")
        val transport = TcpTransport.create(mockk(), resourceManager, Dispatchers.IO).expectNoErrors()
        coAssertErrorResult("resource manager blocked outgoing connection: peer=QmXkJYqRgNvjTbmnS6YzVDDnSQaepQCCaBStB5izqajBea, address=/ip4/127.0.0.1/tcp/1234, error=An Error") {
            transport.dial(validPeerId, validMultiaddress)
        }
    }

    @Test
    fun resourceManagerBlocksSetPeer() = runTest {
        val resourceManager = mockk<ResourceManager>()
        val connectionManagementScope = mockk<ConnectionManagementScope>()
        coEvery { resourceManager.openConnection(Direction.DirOutbound, true, any()) } returns Ok(connectionManagementScope)
        coEvery { connectionManagementScope.setPeer(validPeerId) } returns Err("Other Error")
        coEvery { connectionManagementScope.done() } just runs
        val transport = TcpTransport.create(mockk(), resourceManager, Dispatchers.IO).expectNoErrors()
        coAssertErrorResult("resource manager blocked outgoing connection: peer=QmXkJYqRgNvjTbmnS6YzVDDnSQaepQCCaBStB5izqajBea, address=/ip4/127.0.0.1/tcp/1234, error=Other Error") {
            transport.dial(validPeerId, validMultiaddress)
        }
    }

    @Test
    fun testDialing() = runTest {
        val server = ServerSocket(0)
        var connected = false
        val job = launch {
            val clientSocket = server.accept()
            clientSocket.close()
            connected = true
        }

        val multiaddress = InetMultiaddress.fromString("/ip4/127.0.0.1/tcp/${server.localPort}").expectNoErrors()
        val transport = TcpTransport.create(upgrader, NullResourceManager, Dispatchers.IO).expectNoErrors()
        val connection = transport.dial(validPeerId, multiaddress).expectNoErrors()

        assertSame(transportConnection, connection)

        coVerify { upgrader.upgradeOutbound(any(), any(), any(), any(), any()) }

        job.join()

        assertTrue(connected)
    }

    @Test
    fun testListening() = runTest {
        var connected = false
        val multiaddress = InetMultiaddress.fromString("/ip4/0.0.0.0/tcp/10333").expectNoErrors()
        val transport = TcpTransport.create(upgrader, NullResourceManager, Dispatchers.IO).expectNoErrors()
        val listener = transport.listen(multiaddress).expectNoErrors()

        val job = launch {
            val socket = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp().connect("localhost", 10333)
            connected = true
            socket.close()
        }

        val connection = listener.accept().expectNoErrors()
        assertSame(transportConnection, connection)
        job.join()

        coVerify { upgrader.upgradeInbound(any(), any()) }
        assertTrue(connected)
    }
}
