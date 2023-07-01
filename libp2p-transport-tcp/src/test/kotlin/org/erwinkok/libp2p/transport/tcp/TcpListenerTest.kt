// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.transport.tcp

import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.SocketAddress
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.result.coAssertErrorResult
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.nio.channels.ClosedChannelException

internal class TcpListenerTest {
    @Test
    fun socketAddress() {
        val serverSocket = mockk<ServerSocket>()
        val socketAddress = mockk<SocketAddress>()
        every { serverSocket.localAddress } returns socketAddress
        val listener = TcpListener(mockk(), serverSocket, mockk(), mockk(), mockk())
        assertSame(socketAddress, listener.socketAddress)
    }

    @Test
    fun bindAddress() {
        val bindAddress = mockk<InetMultiaddress>()
        val listener = TcpListener(mockk(), mockk(), bindAddress, mockk(), mockk())
        assertSame(bindAddress, listener.transportAddress)
    }

    @Test
    fun close() {
        val serverSocket = mockk<ServerSocket>()
        every { serverSocket.close() } just Runs
        val listener = TcpListener(mockk(), serverSocket, mockk(), mockk(), mockk())
        listener.close()
        verify { serverSocket.close() }
    }

    @Test
    fun acceptThrows() = runTest {
        val serverSocket = mockk<ServerSocket>()
        coEvery { serverSocket.accept() } throws ClosedChannelException()
        val listener = TcpListener(mockk(), serverSocket, mockk(), mockk(), mockk())
        coAssertErrorResult("Could not accept connection, channel was closed") { listener.accept() }
    }
}
