// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.transport.tcp

import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.SocketAddress
import io.ktor.utils.io.core.Closeable
import io.ktor.utils.io.core.internal.ChunkBuffer
import io.ktor.utils.io.pool.ObjectPool
import mu.KotlinLogging
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.NetworkProtocol
import org.erwinkok.libp2p.core.network.transport.Listener
import org.erwinkok.libp2p.core.network.transport.Transport
import org.erwinkok.libp2p.core.network.transport.TransportConnection
import org.erwinkok.libp2p.core.network.upgrader.Upgrader
import org.erwinkok.result.Err
import org.erwinkok.result.Result
import org.erwinkok.result.map
import java.nio.channels.ClosedChannelException

private val logger = KotlinLogging.logger {}

class TcpListener(
    private val transport: Transport,
    private val serverSocket: ServerSocket,
    private val bindAddress: InetMultiaddress,
    private val upgrader: Upgrader,
    private val pool: ObjectPool<ChunkBuffer>,
) : Listener, Closeable {
    override val socketAddress: SocketAddress
        get() = serverSocket.localAddress

    override val transportAddress: InetMultiaddress
        get() = bindAddress

    override suspend fun accept(): Result<TransportConnection> {
        try {
            val socket = serverSocket.accept()
            return InetMultiaddress.fromSocketAndProtocol(socket.remoteAddress, NetworkProtocol.TCP)
                .map { remoteAddress ->
                    logger.info { "new inbound connection: $bindAddress <-- $remoteAddress" }
                    val transportConnection = TcpTransportConnection(socket, bindAddress, remoteAddress, pool)
                    return upgrader.upgradeInbound(transport, transportConnection)
                }
        } catch (e: ClosedChannelException) {
            return Err("Could not accept connection, channel was closed")
        }
    }

    override fun close() {
        serverSocket.close()
    }
}
