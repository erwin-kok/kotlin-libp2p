// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.host

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import mu.KotlinLogging
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.event.EventBus
import org.erwinkok.libp2p.core.event.EvtLocalProtocolsUpdated
import org.erwinkok.libp2p.core.network.Connectedness
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.Network
import org.erwinkok.libp2p.core.network.Stream
import org.erwinkok.libp2p.core.peerstore.Peerstore
import org.erwinkok.libp2p.core.peerstore.Peerstore.Companion.TempAddrTTL
import org.erwinkok.libp2p.core.record.AddressInfo
import org.erwinkok.multiformat.multistream.MultistreamMuxer
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.erwinkok.result.getOrElse
import org.erwinkok.result.map
import org.erwinkok.result.onFailure
import org.erwinkok.result.onSuccess

private val logger = KotlinLogging.logger {}

class BasicHost(
    val scope: CoroutineScope,
    val localIdentity: LocalIdentity,
    override val network: Network,
    override val peerstore: Peerstore,
    override val multistreamMuxer: MultistreamMuxer<Stream>,
    override val eventBus: EventBus,
) : AwaitableClosable, Host {
    private val _context = SupervisorJob(scope.coroutineContext[Job])

    override val jobContext: Job
        get() = _context

    override val id: PeerId
        get() = localIdentity.peerId

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
        connect(AddressInfo.fromPeerId(peerId))
            .onFailure { return Err(it) }
        val stream = network.newStream(peerId)
            .getOrElse { return Err(it) }
        val preferredProtocol = peerstore.firstSupportedProtocol(peerId, protocols)
            .getOrElse {
                stream.reset()
                return Err(it)
            }
        if (preferredProtocol != ProtocolId.Null) {
            stream.setProtocol(preferredProtocol)
            MultistreamMuxer.selectOneOf(mutableSetOf(preferredProtocol), stream)
        } else {
            MultistreamMuxer.selectOneOf(protocols.toSet(), stream)
                .map { selected ->
                    stream.setProtocol(selected)
                    peerstore.addProtocols(peerId, mutableSetOf(selected))
                }
        }
        return Ok(stream)
    }

    override suspend fun connect(addressInfo: AddressInfo): Result<Unit> {
        val addresses = addressInfo.p2pAddresses()
            .getOrElse { return Err(it) }
        peerstore.addAddresses(addressInfo.peerId, addresses, TempAddrTTL)
        if (network.connectedness(addressInfo.peerId) == Connectedness.Connected) {
            return Ok(Unit)
        }
        return dialPeer(addressInfo.peerId)
    }

    private suspend fun dialPeer(peerId: PeerId): Result<Unit> {
        logger.debug { "[$localIdentity] dialing $peerId" }
        network.dialPeer(peerId)
            .onSuccess { logger.debug { "[$localIdentity] finished dialing $peerId" } }
            .onFailure { logger.error { "[$localIdentity] Error dialing host: ${errorMessage(it)}" } }
        return Ok(Unit)
    }

    override fun addresses(): List<InetMultiaddress> {
        return network.listenAddresses()
    }

    override fun close() {
        eventBus.close()
        peerstore.close()
        _context.cancel()
    }
}
