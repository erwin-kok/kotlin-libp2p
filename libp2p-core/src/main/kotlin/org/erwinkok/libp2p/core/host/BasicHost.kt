// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.host

import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import mu.KotlinLogging
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.event.EventBus
import org.erwinkok.libp2p.core.event.EvtLocalProtocolsUpdated
import org.erwinkok.libp2p.core.host.builder.HostConfig
import org.erwinkok.libp2p.core.network.Connectedness
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.Network
import org.erwinkok.libp2p.core.network.Stream
import org.erwinkok.libp2p.core.network.address.AddressUtil
import org.erwinkok.libp2p.core.network.address.IpUtil
import org.erwinkok.libp2p.core.network.address.NetworkInterface
import org.erwinkok.libp2p.core.peerstore.Peerstore
import org.erwinkok.libp2p.core.peerstore.Peerstore.Companion.TempAddrTTL
import org.erwinkok.libp2p.core.protocol.ping.PingService
import org.erwinkok.libp2p.core.record.AddressInfo
import org.erwinkok.multiformat.multistream.MultistreamMuxer
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.erwinkok.result.getOrElse
import org.erwinkok.result.map
import org.erwinkok.result.mapBoth
import org.erwinkok.result.onFailure
import org.erwinkok.result.onSuccess
import kotlin.concurrent.withLock

private val logger = KotlinLogging.logger {}

class BasicHost(
    val scope: CoroutineScope,
    val localIdentity: LocalIdentity,
    hostConfig: HostConfig,
    override val network: Network,
    override val peerstore: Peerstore,
    override val multistreamMuxer: MultistreamMuxer<Stream>,
    override val eventBus: EventBus,
) : AwaitableClosable, Host {
    private val _context = SupervisorJob(scope.coroutineContext[Job])
    private val addressMutex = ReentrantLock()
    private val filteredInterfaceAddresses = mutableListOf<InetMultiaddress>()
    private val allInterfaceAddresses = mutableListOf<InetMultiaddress>()

    val pingService: PingService?

    override val jobContext: Job
        get() = _context

    override val id: PeerId
        get() = localIdentity.peerId

    init {
        updateLocalIpAddress()

        pingService = if (hostConfig.enablePing) {
            PingService(scope, this)
        } else {
            null
        }
    }

    private fun updateLocalIpAddress() {
        addressMutex.withLock {
            filteredInterfaceAddresses.clear()
            allInterfaceAddresses.clear()
            NetworkInterface.interfaceMultiaddresses()
                .onFailure {
                    logger.warn { "failed to resolve local interface addresses: ${errorMessage(it)}" }
                    filteredInterfaceAddresses.add(IpUtil.IP4Loopback)
                    filteredInterfaceAddresses.add(IpUtil.IP6Loopback)
                    allInterfaceAddresses.addAll(filteredInterfaceAddresses)
                }
                .onSuccess {
                    val filteredAddresses = it.filter { i -> !IpUtil.isIp6LinkLocal(i) }
                    allInterfaceAddresses.addAll(filteredAddresses)
                    if (filteredInterfaceAddresses.isEmpty()) {
                        filteredInterfaceAddresses.addAll(allInterfaceAddresses)
                    } else {
                        filteredInterfaceAddresses.addAll(allInterfaceAddresses.filter { m -> IpUtil.isIpLoopback(m) })
                    }
                }
        }
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
        connect(AddressInfo.fromPeerId(peerId))
            .onFailure { return Err(it) }
        val stream = network.newStream(peerId)
            .getOrElse { return Err(it) }
        val preferredProtocol = preferredProtocol(peerId, protocols)
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

    private suspend fun preferredProtocol(peerId: PeerId, protocols: Set<ProtocolId>): Result<ProtocolId> {
        val supported = peerstore.supportsProtocols(peerId, protocols)
            .getOrElse { return Err(it) }
        val preferred = supported.firstOrNull() ?: ProtocolId.Null
        return Ok(preferred)
    }

    override suspend fun connect(addressInfo: AddressInfo): Result<Unit> {
        peerstore.addAddresses(addressInfo.peerId, addressInfo.addresses, TempAddrTTL)
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
        return allAddresses()
    }

    fun allAddresses(): List<InetMultiaddress> {
        val listenAddresses = network.listenAddresses()
        if (listenAddresses.isEmpty()) {
            return listenAddresses
        }
        val localFilteredInterfaceAddresses = addressMutex.withLock {
            filteredInterfaceAddresses
        }
        val finalAddresses = AddressUtil.resolveUnspecifiedAddresses(listenAddresses, localFilteredInterfaceAddresses)
            .mapBoth(
                {
                    it
                },
                {
                    logger.debug { "failed to resolve listen addresses" }
                    listOf()
                }
            )
        return IpUtil.unique(finalAddresses)
    }

    override fun close() {
        pingService?.close()
        eventBus.close()
        peerstore.close()
        network.close()
        _context.complete()
    }
}
