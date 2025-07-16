// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.host

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.event.EventBus
import org.erwinkok.libp2p.core.event.EvtLocalProtocolsUpdated
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.Network
import org.erwinkok.libp2p.core.network.Stream
import org.erwinkok.libp2p.core.network.StreamHandler
import org.erwinkok.libp2p.core.network.swarm.SwarmConnection
import org.erwinkok.libp2p.core.peerstore.Peerstore
import org.erwinkok.libp2p.core.peerstore.Peerstore.Companion.PermanentAddrTTL
import org.erwinkok.libp2p.core.record.AddressInfo
import org.erwinkok.libp2p.core.record.Envelope
import org.erwinkok.libp2p.core.record.PeerRecord
import org.erwinkok.multiformat.multistream.MultistreamMuxer
import org.erwinkok.multiformat.multistream.ProtocolHandlerInfo
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.erwinkok.result.flatMap
import org.erwinkok.result.getOrElse
import org.erwinkok.result.map
import org.erwinkok.result.onFailure
import org.erwinkok.result.onSuccess
import java.time.Duration
import java.time.Instant

private val logger = KotlinLogging.logger {}

class BlankHost private constructor(
    val scope: CoroutineScope,
    override val network: Network,
    override val eventBus: EventBus,
) : AwaitableClosable, Host {
    private val context = SupervisorJob(scope.coroutineContext[Job])

    override val jobContext: Job
        get() = context

    override val id: PeerId
        get() = network.localPeerId

    override val peerstore: Peerstore
        get() = network.peerstore

    override val multistreamMuxer = MultistreamMuxer<Stream>()

    init {
        network.streamHandler = ::handleStream
    }

    override fun addresses(): List<InetMultiaddress> {
        val addresses = network.interfaceListenAddresses()
            .getOrElse {
                logger.debug { "error retrieving network interface addrs: ${errorMessage(it)}" }
                return listOf()
            }
        return addresses
    }

    override suspend fun connect(addressInfo: AddressInfo): Result<Unit> {
        peerstore.addAddresses(addressInfo.peerId, addressInfo.addresses, Peerstore.TempAddrTTL)
        val connections = network.connectionsToPeer(addressInfo.peerId)
        if (connections.isNotEmpty()) {
            return Ok(Unit)
        }
        return network.dialPeer(addressInfo.peerId).map { }
    }

    override fun setStreamHandler(protocolId: ProtocolId, handler: StreamHandler) {
        multistreamMuxer.addHandler(protocolId) { protocol, stream ->
            stream.setProtocol(protocol)
            handler(stream)
            Ok(Unit)
        }
        eventBus.tryPublish(EvtLocalProtocolsUpdated(listOf(protocolId), listOf()))
    }

    override fun setStreamHandlerMatch(protocolId: ProtocolId, matcher: (ProtocolId) -> Boolean, handler: StreamHandler) {
        multistreamMuxer.addHandlerWithFunc(protocolId, matcher) { protocol, stream ->
            stream.setProtocol(protocol)
            handler(stream)
            Ok(Unit)
        }
        eventBus.tryPublish(EvtLocalProtocolsUpdated(listOf(protocolId), listOf()))
    }

    override fun removeStreamHandler(protocolId: ProtocolId) {
        multistreamMuxer.removeHandler(protocolId)
        eventBus.tryPublish(EvtLocalProtocolsUpdated(listOf(), listOf(protocolId)))
    }

    override suspend fun newStream(peerId: PeerId, protocols: Set<ProtocolId>): Result<Stream> {
        val stream = network.newStream(peerId)
            .getOrElse { return Err(it) }
        MultistreamMuxer.selectOneOf(protocols, stream)
            .onSuccess { selected ->
                stream.setProtocol(selected)
                peerstore.addProtocols(peerId, setOf(selected))
            }
            .onFailure {
                logger.info { "Peer $peerId does not support any of the requested protocols ${protocols.joinToString()}" }
                stream.reset()
                return Err(it)
            }
        return Ok(stream)
    }

    override fun close() {
        eventBus.close()
        network.close()
        context.complete()
    }

    private suspend fun handleStream(stream: Stream) {
        val before = Instant.now()
        val result = withTimeoutOrNull(SwarmConnection.NegotiationTimeout) {
            multistreamMuxer.negotiate(stream)
                .onFailure { e ->
                    val took = Duration.between(before, Instant.now())
                    logger.warn { "protocol mux failed: ${e.message} (took $took)" }
                    stream.reset()
                }
                .onSuccess { (_, protocol, handler): ProtocolHandlerInfo<Stream> ->
                    stream.setProtocol(protocol)
                    if (handler != null) {
                        scope.launch(context + CoroutineName("swarm-stream-${stream.id} ($protocol)")) {
                            handler(protocol, stream)
                        }
                    } else {
                        stream.reset()
                        logger.warn { "no handler registered for $protocol" }
                    }
                }
        }
        if (result == null) {
            stream.reset()
            logger.warn { "negotiation timeout in when determining protocol" }
        }
    }

    companion object {
        suspend fun create(
            scope: CoroutineScope,
            network: Network,
            eventBus: EventBus = EventBus(),
        ): Result<Host> {
            val host = BlankHost(scope, network, eventBus)
            val localIdentity = host.peerstore.localIdentity(host.id) ?: return Err("LocalIdentity not stored in peerstore")
            PeerRecord.fromPeerIdAndAddresses(host.id, host.addresses())
                .flatMap { record -> Envelope.seal(record, localIdentity.privateKey) }
                .flatMap { envelope -> host.peerstore.consumePeerRecord(envelope, PermanentAddrTTL) }
                .onFailure { Err("Error creating BlankHost: ${errorMessage(it)}") }
            return Ok(host)
        }
    }
}
