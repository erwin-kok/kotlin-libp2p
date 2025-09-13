// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.transport.tcp

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.SocketOptions
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.Direction
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.NetworkProtocol
import org.erwinkok.libp2p.core.network.transport.Listener
import org.erwinkok.libp2p.core.network.transport.Transport
import org.erwinkok.libp2p.core.network.transport.TransportConnection
import org.erwinkok.libp2p.core.network.transport.TransportFactory
import org.erwinkok.libp2p.core.network.upgrader.Upgrader
import org.erwinkok.libp2p.core.resourcemanager.ResourceManager
import org.erwinkok.multiformat.multiaddress.Protocol
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.erwinkok.result.getOrElse
import org.erwinkok.result.map
import java.net.BindException

private val logger = KotlinLogging.logger {}

class TcpTransport private constructor(
    private val upgrader: Upgrader,
    private val resourceManager: ResourceManager,
    private val dispatcher: CoroutineDispatcher,
    private val configure: SocketOptions.() -> Unit = {},
) : Transport {
    override val proxy: Boolean
        get() = false

    override val protocols: List<Protocol>
        get() = listOf(Protocol.TCP)

    override fun canDial(remoteAddress: InetMultiaddress): Boolean {
        return remoteAddress.isValidTcpIp
    }

    override fun canListen(bindAddress: InetMultiaddress): Boolean {
        val hn = bindAddress.hostName ?: return false
        return hn.isValid && bindAddress.networkProtocol == NetworkProtocol.TCP
    }

    override suspend fun dial(peerId: PeerId, remoteAddress: InetMultiaddress): Result<TransportConnection> {
        if (!remoteAddress.isValidTcpIp) {
            return Err("TcpTransport can only dial to valid tcp addresses, not $remoteAddress")
        }
        val connectionScope = resourceManager.openConnection(Direction.DirOutbound, true, remoteAddress)
            .getOrElse {
                return Err("resource manager blocked outgoing connection: peer=$peerId, address=$remoteAddress, error=${errorMessage(it)}")
            }
        connectionScope.setPeer(peerId)
            .getOrElse {
                connectionScope.done()
                return Err("resource manager blocked outgoing connection: peer=$peerId, address=$remoteAddress, error=${errorMessage(it)}")
            }
        return remoteAddress.toSocketAddress()
            .map { socketAddress ->
                try {
                    val socket = aSocket(ActorSelectorManager(dispatcher)).tcp().connect(socketAddress, configure)
                    return InetMultiaddress.fromSocketAndProtocol(socket.localAddress, NetworkProtocol.TCP)
                        .map { localAddress ->
                            logger.info { "new outbound connection: $localAddress --> $remoteAddress" }
                            val transportConnection = TcpTransportConnection(socket, localAddress, remoteAddress)
                            return upgrader.upgradeOutbound(this, transportConnection, Direction.DirOutbound, peerId, connectionScope)
                        }
                } catch (e: Exception) {
                    return Err("Could not open connection to $remoteAddress: ${errorMessage(e)}")
                }
            }
    }

    override fun listen(bindAddress: InetMultiaddress): Result<Listener> {
        if (!canListen(bindAddress)) {
            return Err("TcpTransport can only listen to valid tcp addresses, not $bindAddress")
        }
        return bindAddress.toSocketAddress()
            .map { socketAddress ->
                return try {
                    runBlocking {
                        val serverSocket = aSocket(ActorSelectorManager(dispatcher)).tcp().bind(socketAddress, configure)
                        InetMultiaddress.fromSocketAndProtocol(serverSocket.localAddress, NetworkProtocol.TCP)
                            .map { boundAddress -> TcpListener(this@TcpTransport, serverSocket, boundAddress, upgrader) }
                    }
                } catch (e: BindException) {
                    Err("Can not bind to $socketAddress (${errorMessage(e)})")
                }
            }
    }

    override fun toString(): String {
        return "tcp"
    }

    override fun close() = Unit

    companion object TcpTransportFactory : TransportFactory {
        override fun create(upgrader: Upgrader, resourceManager: ResourceManager, dispatcher: CoroutineDispatcher): Result<Transport> {
            return Ok(TcpTransport(upgrader, resourceManager, dispatcher))
        }
    }
}
