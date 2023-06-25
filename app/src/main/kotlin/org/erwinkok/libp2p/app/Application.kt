// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.app

import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.erwinkok.libp2p.core.AddrInfo
import org.erwinkok.libp2p.core.datastore.MapDatastore
import org.erwinkok.libp2p.core.host.Host
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.host.builder.host
import org.erwinkok.libp2p.core.host.builder.ping
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.Stream
import org.erwinkok.libp2p.core.peerstore.Peerstore.Companion.PermanentAddrTTL
import org.erwinkok.libp2p.crypto.CryptoUtil
import org.erwinkok.libp2p.muxer.mplex.mplex
import org.erwinkok.libp2p.security.noise.noise
import org.erwinkok.libp2p.transport.tcp.tcp
import org.erwinkok.multiformat.multibase.bases.Base64
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.erwinkok.result.flatMap
import org.erwinkok.result.getOrElse
import org.erwinkok.result.getOrThrow
import org.erwinkok.result.onFailure
import org.erwinkok.result.onSuccess
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private val logger = KotlinLogging.logger {}

private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
    val cause = throwable.cause ?: throwable
    val message = cause.message ?: cause.toString()
    logger.error { "Error: $message" }
}

class Main {
    private lateinit var host: Host
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)

    suspend fun createHost(): Result<Unit> {
        val localIdentity = getLocalIdentity()
            .getOrElse { return Err(it) }
        host = host {
            identity(localIdentity)
            addressManager {
                listenAddresses {
                    multiAddress("/ip4/1.2.3.4/tcp/10333")
                    multiAddress("/ip4/0.0.0.0/tcp/10333")
                }
            }
            muxers {
                mplex()
            }
            securityTransport {
                noise()
            }
            transports {
                tcp()
            }
            peerstore {
                gcInterval = 1.hours
            }
            swarm {
                dialTimeout = 10.minutes
            }
            datastore(MapDatastore(scope))
            keychain {
                password = "bladieblabladieblablabla"
                dek {
                    salt = "zxbfvasfasdfasdfasdfawefasdfasef"
                }
            }
            protocols {
                ping()
                stream("/chat/1.0.0") {
                }
            }
        }.getOrElse { return Err(it) }
        return Ok(Unit)
    }

    private fun getLocalIdentity(): Result<LocalIdentity> {
        return Base64.decodeStringStd("CAESQAx4e4ImYj8jgFqH/gMB2rB9lSBd8NGwq5VOmqUR34t/+w0DyBacb8UhyavKrETOQ7uAIE5V99/65nwZJtSStw4")
            .flatMap { CryptoUtil.unmarshalPrivateKey(it) }
            .flatMap { LocalIdentity.fromPrivateKey(it) }
            .onSuccess { localIdentity ->
                logger.info { "PeerId of local node is: ${localIdentity.peerId}" }
                logger.info { "PrivateKey: " + Base64.encodeToStringStd(localIdentity.privateKey.bytes().getOrThrow()) }
                logger.info { "PublicKey: " + Base64.encodeToStringStd(localIdentity.publicKey.bytes().getOrThrow()) }
            }
    }

    private suspend fun addPeerAddress(host: Host, peerAddress: String): AddrInfo {
        val localAddress = InetMultiaddress.fromString(peerAddress).getOrThrow()
        val info = AddrInfo.addrInfoFromP2pAddr(localAddress).getOrThrow()
        val addresses = info.addrInfoToP2pAddrs().getOrThrow()
        host.peerstore.addAddresses(info.peerId, addresses, PermanentAddrTTL)
        return info
    }

    suspend fun run(): Job {
        val localAddress = addPeerAddress(host, "/ip4/127.0.0.1/tcp/4001/p2p/12D3KooWMomeNGwR72R3nZDtPV4vHgEex6xfS7auhyWESUMdF3bH")
//        val ipfsCrawlerAddress = addPeerAddress(host, "/ip4/104.131.131.82/tcp/4001/p2p/QmaCpDMGvV2BGHeYERUEnRQAwe3N8SzbUtfsmvsqQLuvuJ")
        // https://fleets.status.im/
//        val wakuAddress = addPeerAddress(host, "/ip4/134.209.139.210/tcp/30303/p2p/16Uiu2HAmPLe7Mzm8TsYUubgCAW1aJoeFScxrLj8ppHFivPo97bUZ")
        // eth nodes
//            "/ip4/47.52.188.149/tcp/30703/ethv4/16Uiu2HAm9Vatqr4GfVCqnyeaPtCF3q8fz8kDDUgqXVfFG7ZfSA7w",
//            "/ip4/206.189.243.164/tcp/30703/ethv4/16Uiu2HAmBCh5bgYr6V3fDuLqUzvtSAsFTQJCQ3TVHT8ta8bTu2Jm",
//            "/ip4/35.188.168.137/tcp/30703/ethv4/16Uiu2HAm3MUqtGjmetyZ9L4SN2R8oHDWvACUcec25LjtDD5euiRH"

        return scope.launch {
            host.setStreamHandler(ProtocolId.from("/chat/1.0.0")) {
                chatHandler(it)
            }

            host.connect(localAddress)
//            host.connect(ipfsCrawlerAddress)
//            host.connect(wakuAddress)
//            host.newStream(localAddress.peerId, ProtocolId.from("/ipfs/id/1.0.0"))
//                .onFailure { logger.error { "Error opening new stream to $localAddress: ${errorMessage(it)}" } }
//                .onSuccess {
//                    logger.info { "Success opening stream" }
//                    it.close()
//                }
        }
    }

    private suspend fun chatHandler(stream: Stream) {
        logger.info { "NEW CHAT STREAM!!" }
        while (true) {
            val bytes = ByteArray(1024)
            val size = stream.input.readAvailable(bytes, 0, bytes.size)
            if (size > 0) {
                val message = String(bytes, 0, size)
                logger.info { "$size: $message" }
                stream.output.writeFully(bytes)
            }
        }
    }
}

fun main() {
    runBlocking {
        logger.info { "Starting..." }

        val main = Main()
        main.createHost()
            .onFailure {
                logger.error { "Could not create host: ${errorMessage(it)}" }
                return@runBlocking
            }
        val job = main.run()
        job.invokeOnCompletion {
            logger.info { "The main job is completed/cancelled!" }
        }
        delay(300000)
        job.join()
    }
}
