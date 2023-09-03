// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
@file:OptIn(DelicateCoroutinesApi::class)

package org.erwinkok.libp2p.core.protocol.identify

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.event.EvtLocalReachabilityChanged
import org.erwinkok.libp2p.core.event.EvtNatDeviceTypeChanged
import org.erwinkok.libp2p.core.host.Host
import org.erwinkok.libp2p.core.network.Direction
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.NatDeviceType
import org.erwinkok.libp2p.core.network.Network
import org.erwinkok.libp2p.core.network.NetworkConnection
import org.erwinkok.libp2p.core.network.NetworkProtocol
import org.erwinkok.libp2p.core.network.Reachability
import org.erwinkok.libp2p.core.network.Subscriber
import org.erwinkok.libp2p.core.network.address.IpUtil
import org.erwinkok.libp2p.core.peerstore.Peerstore.Companion.OwnObservedAddrTTL
import org.erwinkok.libp2p.core.protocol.identify.IdService.Companion.hasConsistentTransport
import org.erwinkok.result.getOrElse
import org.erwinkok.result.map
import org.erwinkok.util.Tuple
import org.erwinkok.util.Tuple2
import java.time.Duration
import java.time.Instant
import kotlin.math.min
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

private val logger = KotlinLogging.logger {}

class ObservedAddressManager(
    scope: CoroutineScope,
    private val host: Host,
) : AwaitableClosable, Subscriber {
    private val _context = Job(scope.coroutineContext[Job])
    private val activeConnectionsMutex = Mutex()
    private val activeConnections = mutableMapOf<NetworkConnection, InetMultiaddress>()
    private val mutex = Mutex()
    private val addresses = mutableMapOf<InetMultiaddress, MutableList<ObservedAddress>>()
    private val observationChannel = Channel<NewObservation>(observedAddrManagerWorkerChannelSize)
    private var reachability = Reachability.ReachabilityUnknown
    private var currentUdpNatDeviceType = NatDeviceType.NatDeviceTypeUnknown
    private var currentTcpNatDeviceType = NatDeviceType.NatDeviceTypeUnknown

    override val jobContext: Job get() = _context

    var ttl = OwnObservedAddrTTL
        set(value) {
            runBlocking {
                mutex.withLock {
                    field = value
                }
            }
        }
        get() = runBlocking {
            mutex.withLock {
                field
            }
        }

    init {
        host.eventBus.subscribe<EvtLocalReachabilityChanged>(this, scope) {
            mutex.withLock {
                reachability = it.reachability
            }
        }
        host.network.subscribe(this)
        scope.launch(_context + CoroutineName("observed-address-manager-refresh")) {
            delay(OwnObservedAddrTTL / 2)
            refresh()
        }
        scope.launch(_context + CoroutineName("observed-address-manager-gc")) {
            delay(GCInterval)
            gc()
        }
        scope.launch(_context + CoroutineName("observed-address-manager-observation")) {
            while (!observationChannel.isClosedForReceive) {
                try {
                    val observation = observationChannel.receive()
                    maybeRecordObservation(observation.connection, observation.observed)
                } catch (e: ClosedReceiveChannelException) {
                    break
                }
            }
        }
    }

    fun addressesFor(address: InetMultiaddress): List<InetMultiaddress> {
        return runBlocking {
            mutex.withLock {
                val observedAddresses = addresses[address]
                if (observedAddresses != null) {
                    filter(observedAddresses)
                } else {
                    listOf()
                }
            }
        }
    }

    fun addresses(): List<InetMultiaddress> {
        return runBlocking {
            mutex.withLock {
                if (addresses.isEmpty()) {
                    listOf()
                } else {
                    val observedAddresses = addresses.values.flatten()
                    filter(observedAddresses)
                }
            }
        }
    }

    fun record(connection: NetworkConnection, observed: InetMultiaddress) {
        val result = observationChannel.trySend(NewObservation(connection, observed))
        if (result.isFailure) {
            logger.debug { "dropping address observation due to full buffer from ${connection.remoteAddress}" }
        }
    }

    override fun close() {
        observationChannel.close()
        _context.cancel()
    }

    override fun disconnected(network: Network, connection: NetworkConnection) {
        runBlocking {
            removeConnection(connection)
        }
    }

    private fun filter(observedAddresses: List<ObservedAddress>): List<InetMultiaddress> {
        val now = Instant.now()
        val filteredAddresses = observedAddresses.filter { Duration.between(it.lastSeen, now) <= ttl.toJavaDuration() && it.isActivated }.groupBy { it.groupKey }
        val addresses = mutableListOf<InetMultiaddress>()
        for (oa in filteredAddresses.values) {
            val sorted = oa.sortedWith { a, b ->
                if (a.numInbound > b.numInbound) {
                    -1
                } else if (a.numInbound == b.numInbound && a.seenBy.size > b.seenBy.size) {
                    -1
                } else {
                    1
                }
            }
            for (i in 0 until min(maxObservedAddressesPerIPAndTransport, sorted.size)) {
                addresses.add(sorted[i].address)
            }
        }
        return addresses
    }

    private suspend fun gc() {
        logger.info { "Garbage collect observed address manager" }
        val now = Instant.now()
        mutex.withLock {
            for ((local, observedAddresses) in addresses) {
                val filteredAddresses = mutableListOf<ObservedAddress>()
                for (address in observedAddresses) {
                    for ((k, ob) in address.seenBy) {
                        if (Duration.between(ob.seenTime, now) > (ttl * ActivationThreshold).toJavaDuration()) {
                            address.seenBy.remove(k)
                            if (ob.inbound) {
                                address.numInbound--
                            }
                        }
                    }
                    if (Duration.between(address.lastSeen, now) <= ttl.toJavaDuration()) {
                        filteredAddresses.add(address)
                    }
                }
                if (filteredAddresses.isNotEmpty()) {
                    addresses[local] = filteredAddresses
                } else {
                    addresses.remove(local)
                }
            }
        }
    }

    private suspend fun refresh() {
        logger.info { "Refresh observed address manager" }
        val recycledObservations = activeConnectionsMutex.withLock {
            activeConnections.map { NewObservation(it.key, it.value) }
        }
        mutex.withLock {
            recycledObservations.forEach { recordObservationUnlocked(it.connection, it.observed) }
        }
    }

    private suspend fun addConnection(connection: NetworkConnection, observed: InetMultiaddress) {
        activeConnectionsMutex.withLock {
            val c = host.network.connectionsToPeer(connection.remoteIdentity.peerId).firstOrNull { it == connection }
            if (c != null) {
                activeConnections[connection] = observed
            }
        }
    }

    private suspend fun removeConnection(connection: NetworkConnection) {
        activeConnectionsMutex.withLock {
            activeConnections.remove(connection)
        }
    }

    private fun shouldRecordObservation(connection: NetworkConnection, observed: InetMultiaddress): Boolean {
        if (IpUtil.isIpLoopback(observed)) {
            return false
        }
        if (IpUtil.isNat64Ipv4ConvertedIpv6Address(observed)) {
            return false
        }
        val interfaceAddresses = host.network.interfaceListenAddresses()
            .map { it.map { address -> normalizeMultiAddress(address) } }
            .getOrElse {
                logger.info { "failed to get interface listen addresses" }
                return false
            }
        val local = normalizeMultiAddress(connection.localAddress)
        val listenAddresses = host.network.listenAddresses().map { normalizeMultiAddress(it) }
        if (!interfaceAddresses.contains(local) && !listenAddresses.contains(local)) {
            return false
        }
        val hostAddresses = host.addresses().map { normalizeMultiAddress(it) }
        if (!hasConsistentTransport(observed, hostAddresses) && !hasConsistentTransport(observed, listenAddresses)) {
            logger.debug { "observed multiaddress doesn't match the transports of any announced addresses, from=${connection.remoteAddress}, observed=$observed" }
            return false
        }
        return true
    }

    private fun normalizeMultiAddress(address: InetMultiaddress): InetMultiaddress {
        // TODO: Let the host normalize the multiaddress
        return address
    }

    private suspend fun maybeRecordObservation(connection: NetworkConnection, observed: InetMultiaddress) {
        val shouldRecord = shouldRecordObservation(connection, observed)
        if (shouldRecord) {
            logger.debug { "added own observed listen address: $observed" }
            mutex.withLock {
                recordObservationUnlocked(connection, observed)
                if (reachability == Reachability.ReachabilityPrivate) {
                    emitAllNatTypes()
                }
            }
            addConnection(connection, observed)
        }
    }

    private fun recordObservationUnlocked(connection: NetworkConnection, observed: InetMultiaddress) {
        val now = Instant.now()
        val observerString = observerGroup(connection.remoteAddress)
        val observation = Observation(now, connection.statistic.direction == Direction.DirInbound)
        val observedAddress = addresses[connection.localAddress]?.firstOrNull { it.address == observed }
        if (observedAddress != null) {
            val wasInbound = observedAddress.seenBy[observerString]?.inbound ?: false
            val isInbound = observation.inbound
            observation.inbound = isInbound || wasInbound
            if (!wasInbound && isInbound) {
                observedAddress.numInbound++
            }
            observedAddress.seenBy[observerString] = observation
            observedAddress.lastSeen = now
            return
        }
        val newObservedAddress = ObservedAddress(observed)
        newObservedAddress.seenBy[observerString] = observation
        newObservedAddress.lastSeen = now
        if (observation.inbound) {
            newObservedAddress.numInbound++
        }
        val list = addresses.computeIfAbsent(connection.localAddress) { mutableListOf() }
        list.add(newObservedAddress)
    }

    private suspend fun emitAllNatTypes() {
        val observed = addresses.values.flatten()
        val (hasChangedTcp, natTypeTcp) = emitSpecificNATType(observed, NetworkProtocol.TCP, currentTcpNatDeviceType)
        if (hasChangedTcp) {
            currentTcpNatDeviceType = natTypeTcp
        }
        val (hasChangedUdp, natTypeUdp) = emitSpecificNATType(observed, NetworkProtocol.UDP, currentUdpNatDeviceType)
        if (hasChangedUdp) {
            currentUdpNatDeviceType = natTypeUdp
        }
    }

    private suspend fun emitSpecificNATType(addresses: List<ObservedAddress>, networkProtocol: NetworkProtocol, currentNATType: NatDeviceType): Tuple2<Boolean, NatDeviceType> {
        val now = Instant.now()
        val seenBy = mutableSetOf<String>()
        var count = 0
        val filteredAddresses = addresses.filter { it.address.networkProtocol == networkProtocol }
        for (oa in filteredAddresses) {
            // if we have an activated addresses, it's a Cone NAT.
            if (Duration.between(oa.lastSeen, now) <= ttl.toJavaDuration() && oa.isActivated) {
                if (currentNATType != NatDeviceType.NatDeviceTypeCone) {
                    host.eventBus.publish(EvtNatDeviceTypeChanged(networkProtocol, NatDeviceType.NatDeviceTypeCone))
                    return Tuple(true, NatDeviceType.NatDeviceTypeCone)
                }

                // our current NAT Device Type is already CONE, nothing to do here.
                return Tuple(false, NatDeviceType.NatDeviceTypeUnknown)
            }
            // An observed address on an outbound connection that has ONLY been seen by one peer
            if (Duration.between(oa.lastSeen, now) <= ttl.toJavaDuration() && oa.numInbound == 0 && oa.seenBy.size == 1) {
                count++
                seenBy.addAll(oa.seenBy.keys)
            }
        }
        // If four different peers observe a different address for us on each of four outbound connections, we
        // are MOST probably behind a Symmetric NAT.
        if (count >= ActivationThreshold && seenBy.size >= ActivationThreshold) {
            if (currentNATType != NatDeviceType.NatDeviceTypeSymmetric) {
                host.eventBus.publish(EvtNatDeviceTypeChanged(networkProtocol, NatDeviceType.NatDeviceTypeSymmetric))
                return Tuple(true, NatDeviceType.NatDeviceTypeSymmetric)
            }
        }
        return Tuple(false, NatDeviceType.NatDeviceTypeUnknown)
    }

    private fun observerGroup(remoteAddress: InetMultiaddress): String {
        return remoteAddress.hostName?.toString() ?: remoteAddress.toString()
    }

    companion object {
        var ActivationThreshold = 4
        var GCInterval = 10.minutes
        var observedAddrManagerWorkerChannelSize = 16
        var maxObservedAddressesPerIPAndTransport = 2
    }
}
