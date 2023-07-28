// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network.swarm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.host.builder.SwarmConfig
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.address.AddressUtil
import org.erwinkok.libp2p.core.network.address.IpUtil
import org.erwinkok.libp2p.core.network.connectiongater.ConnectionGater
import org.erwinkok.libp2p.core.network.transport.Resolver
import org.erwinkok.libp2p.core.peerstore.Peerstore
import org.erwinkok.libp2p.core.peerstore.Peerstore.Companion.TempAddrTTL
import org.erwinkok.result.Err
import org.erwinkok.result.Error
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.erwinkok.result.flatMap
import org.erwinkok.result.getOr
import org.erwinkok.result.getOrElse
import org.erwinkok.result.map
import org.erwinkok.result.onFailure
import org.erwinkok.result.onSuccess

private val logger = KotlinLogging.logger {}

class SwarmDialer(
    private val scope: CoroutineScope,
    internal val swarmTransport: SwarmTransport,
    private val swarm: Swarm,
    private val peerstore: Peerstore,
    private val connectionGater: ConnectionGater?,
    private val swarmConfig: SwarmConfig,
) : AwaitableClosable {
    private val _context = Job(scope.coroutineContext[Job])
    private val mutex = Mutex()
    private val workers = mutableMapOf<PeerId, DialWorker>()

    override val jobContext: Job get() = _context

    suspend fun dial(peerId: PeerId): Result<SwarmConnection> {
        return getOrCreateDialWorker(peerId)
            .flatMap { dialWorker ->
                val result = dialWorker.dial()
                if (dialWorker.isCompleted) {
                    dialWorker.close()
                    workers.remove(peerId)
                }
                result
            }
    }

    suspend fun addressesForDial(peerId: PeerId): Result<List<InetMultiaddress>> {
        val peerAddresses = peerstore.addresses(peerId)
        if (peerAddresses.isEmpty()) {
            return Err("No addresses for peer: $peerId")
        }
        val resolvedByTransport = mutableListOf<InetMultiaddress>()
        for (address in peerAddresses) {
            val resolver = swarm.transportForDialing(address).getOr(null) as? Resolver
            if (resolver != null) {
                resolver.resolve(address)
                    .onSuccess {
                        resolvedByTransport.addAll(it)
                    }
                    .onFailure {
                        logger.warn { "Could not resolve multiaddress $address by transport $resolver: ${errorMessage(it)}" }
                    }
            } else {
                resolvedByTransport.add(address)
            }
        }
        val resolved = resolveAddresses(peerId, resolvedByTransport)
            .getOrElse { return Err(it) }
        val goodAddresses = IpUtil.unique(filterKnownUndiables(peerId, resolved))
        if (goodAddresses.isEmpty()) {
            return Err("No good addresses for peer: $peerId")
        }
        peerstore.addAddresses(peerId, goodAddresses, TempAddrTTL)
        return Ok(goodAddresses)
    }

    private suspend fun resolveAddresses(peerId: PeerId, addresses: List<InetMultiaddress>): Result<List<InetMultiaddress>> {
        // TODO
        return Ok(addresses)
    }

    private fun canDial(address: InetMultiaddress): Boolean {
        return swarmTransport.transportForDialing(address)
            .map { it.canDial(address) }
            .getOrElse { false }
    }

    // filterKnownUndiables takes a list of multiaddresses, and removes those that we definitely don't want to dial: addresses configured to be blocked,
    // IPv6 link-local addresses, addresses without a dial-capable transport, addresses that we know to be our own, and addresses with a better transport
    // available. This is an optimization to avoid wasting time on dials that we know are going to fail or for which we have a better alternative.
    private fun filterKnownUndiables(peerId: PeerId, addresses: List<InetMultiaddress>): List<InetMultiaddress> {
        val ourAddresses = swarm.interfaceListenAddresses().getOr(listOf())
        val diableAddresses = AddressUtil.filterAddresses(addresses, ::canDial)
        val lowPriorityAddresses = filterLowPriorityAddresses(diableAddresses)
        val nonBlackHoledAddresses = filterAddresses(lowPriorityAddresses)
        return AddressUtil.filterAddresses(
            nonBlackHoledAddresses,
            listOf(
                { address -> ourAddresses.none { it == address } },
                { address -> !IpUtil.isIp6LinkLocal(address) },
                { address -> connectionGater == null || connectionGater.interceptAddressDial(peerId, address) },
            ),
        )
    }

    private fun filterAddresses(addresses: List<InetMultiaddress>): List<InetMultiaddress> {
        // TODO
        return addresses
    }

    private fun filterLowPriorityAddresses(addresses: List<InetMultiaddress>): List<InetMultiaddress> {
        // TODO
        return addresses
    }

    override fun close() {
        workers.values.forEach { it.close() }
        _context.cancel()
    }

    private suspend fun getOrCreateDialWorker(peerId: PeerId): Result<DialWorker> {
        mutex.withLock {
            if (_context.isCompleted) {
                return Err("DialSynchronizer is closed")
            }
            val peerDialer = workers.computeIfAbsent(peerId) {
                val networkPeer = swarm.getOrCreatePeer(peerId)
                DialWorker(scope, peerId, networkPeer, this, swarmConfig, connectionGater)
            }
            return Ok(peerDialer)
        }
    }

    companion object {
        val ErrDialTimeout = Error("dial timed out")
    }
}
