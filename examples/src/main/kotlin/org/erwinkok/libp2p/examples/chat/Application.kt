// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.examples.chat

import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.erwinkok.libp2p.core.host.BasicHost
import org.erwinkok.libp2p.core.host.Host
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.host.builder.host
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.Stream
import org.erwinkok.libp2p.core.peerstore.Peerstore.Companion.PermanentAddrTTL
import org.erwinkok.libp2p.core.record.AddressInfo
import org.erwinkok.libp2p.ds.rocksdb.RocksDbDatastore
import org.erwinkok.libp2p.muxer.mplex.mplex
import org.erwinkok.libp2p.muxer.yamux.yamux
import org.erwinkok.libp2p.security.noise.noise
import org.erwinkok.libp2p.transport.tcp.tcp
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.errorMessage
import org.erwinkok.result.getOrElse
import org.erwinkok.result.getOrThrow
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

fun main() {
    runBlocking {
        logger.info { "Starting..." }

        val scope = CoroutineScope(SupervisorJob() + exceptionHandler + Dispatchers.Default)

        val localIdentity = LocalIdentity.random()
            .getOrElse {
                logger.error { "Could not create random identity" }
                return@runBlocking
            }

        val datastore = RocksDbDatastore.create(scope, "/home/erwin/rocks")
            .getOrElse {
                logger.error { "Could not create datastore: ${errorMessage(it)}" }
                return@runBlocking
            }

        val hostBuilder = host {
            enablePing = true
            identity(localIdentity)
            muxers {
                mplex()
                yamux()
            }
            securityTransport {
                noise()
            }
            transports {
                tcp()
            }
            swarm {
                dialTimeout = 10.minutes
                listenAddresses {
                    multiAddress("/ip4/0.0.0.0/tcp/10333")
                }
            }
            datastore(datastore)
        }

        val host = hostBuilder.build(scope)
            .getOrElse {
                logger.error { "The following errors occurred while creating the host: ${errorMessage(it)}" }
                return@runBlocking
            }

        displayLocalListenAddress(host, localIdentity)

        host.setStreamHandler(ProtocolId.of("/chat/1.0.0")) {
            chatHandler(it)
        }

        val peerAddress = addPeerAddress(host, "/ip4/127.0.0.1/tcp/3001/p2p/QmQ5AMXn326VSwivF4iuL8AEbJngGmXfwFo3adtzSWSmM5")

        val basicHost = host as? BasicHost
        if (basicHost != null) {
            val flow = basicHost.pingService?.ping(peerAddress.peerId, 5.seconds)
            if (flow != null) {
                launch {
                    flow.collect { pingResult ->
                        logger.info { "Ping RTT: ${pingResult.rtt}" }
                    }
                }
            }
        }

        val stream = host.newStream(peerAddress.peerId, ProtocolId.of("/chat/1.0.0"))
            .getOrElse {
                logger.error { "Could not open chat stream with peer: ${errorMessage(it)}" }
                host.close()
                host.awaitClosed()
                return@runBlocking
            }
        chatHandler(stream)
        stream.close()

        host.close()
        host.awaitClosed()
    }
}

private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
    val cause = throwable.cause ?: throwable
    val message = cause.message ?: cause.toString()
    logger.error { "Error: $message" }
}

private suspend fun addPeerAddress(host: Host, peerAddress: String): AddressInfo {
    val localAddress = InetMultiaddress.fromString(peerAddress).getOrThrow()
    val info = AddressInfo.fromP2pAddress(localAddress).getOrThrow()
    val addresses = info.p2pAddresses().getOrThrow()
    host.peerstore.setAddresses(info.peerId, addresses, PermanentAddrTTL)
    return info
}

private fun displayLocalListenAddress(host: Host, localIdentity: LocalIdentity) {
    val addresses = host
        .addresses()
        .map { it.withPeerId(localIdentity.peerId) }
    logger.info { "Local addresses the Host listens on: ${addresses.joinToString()} " }
}

private suspend fun chatHandler(stream: Stream) {
    logger.info { "NEW CHAT STREAM!!" }
    while (true) {
        val bytes = ByteArray(1024)
        val size = stream.input.readAvailable(bytes, 0, bytes.size)
        if (size > 0) {
            val message = String(bytes, 0, size).trim('\n')
            logger.info { message }
            stream.output.writeFully(bytes)
            if (message == "/quit") {
                break
            }
        }
    }
}
