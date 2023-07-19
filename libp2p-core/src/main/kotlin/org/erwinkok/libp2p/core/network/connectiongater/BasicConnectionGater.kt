// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network.connectiongater

import inet.ipaddr.IPAddress
import inet.ipaddr.IPAddressString
import io.ktor.utils.io.core.Closeable
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.mapNotNull
import mu.KotlinLogging
import org.erwinkok.libp2p.core.datastore.Datastore
import org.erwinkok.libp2p.core.datastore.Key.Companion.key
import org.erwinkok.libp2p.core.datastore.namespace.Namespace
import org.erwinkok.libp2p.core.datastore.query.Query
import org.erwinkok.libp2p.core.datastore.query.queryFilterError
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.ConnectionMultiaddress
import org.erwinkok.libp2p.core.network.Direction
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.transport.TransportConnection
import org.erwinkok.result.errorMessage
import org.erwinkok.result.getOrElse
import org.erwinkok.result.onFailure
import org.erwinkok.result.onSuccess

private val logger = KotlinLogging.logger {}

class BasicConnectionGater private constructor(scope: CoroutineScope, child: Datastore? = null) : Closeable, ConnectionGater {
    private val blockedPeers = mutableSetOf<PeerId>()
    private val blockedAddresses = mutableSetOf<IPAddress>()
    private val blockedSubnets = mutableSetOf<IPAddress>()
    private val lock = ReentrantLock()
    private val ds: Datastore?

    init {
        ds = if (child != null) {
            Namespace.wrap(scope, child, key(ns))
        } else {
            null
        }
    }

    override fun interceptPeerDial(peerId: PeerId): Boolean {
        return lock.withLock {
            !blockedPeers.contains(peerId)
        }
    }

    override fun interceptAddressDial(peerId: PeerId, multiaddress: InetMultiaddress): Boolean {
        lock.withLock {
            val address = multiaddress.hostName?.address ?: return true
            return isAllowed(address)
        }
    }

    override fun interceptAccept(connection: ConnectionMultiaddress): Boolean {
        lock.withLock {
            val address = connection.remoteAddress.hostName?.address ?: return true
            return isAllowed(address)
        }
    }

    override fun interceptSecured(direction: Direction, peerId: PeerId, connection: ConnectionMultiaddress): Boolean {
        if (direction == Direction.DirOutbound) {
            return true
        }
        return lock.withLock {
            !blockedPeers.contains(peerId)
        }
    }

    override fun interceptUpgraded(connection: TransportConnection): Boolean {
        return true
    }

    suspend fun blockPeer(peerId: PeerId) {
        ds?.put(key(keyPeer + peerId.toString()), peerId.idBytes())
        lock.withLock {
            blockedPeers.add(peerId)
        }
    }

    suspend fun unblockPeer(peerId: PeerId) {
        ds?.delete(key(keyPeer + peerId.toString()))
        lock.withLock {
            blockedPeers.remove(peerId)
        }
    }

    fun listBlockedPeers(): List<PeerId> {
        return lock.withLock {
            blockedPeers.toList()
        }
    }

    suspend fun blockAddress(address: IPAddress) {
        ds?.put(key(keyAddr + toKeyFormat(address)), toKeyFormat(address).toByteArray())
        lock.withLock {
            blockedAddresses.add(address)
        }
    }

    suspend fun unblockAddress(address: IPAddress) {
        ds?.delete(key(keyAddr + toKeyFormat(address)))
        lock.withLock {
            blockedAddresses.remove(address)
        }
    }

    fun listBlockedAddresses(): List<IPAddress> {
        return lock.withLock {
            blockedAddresses.toList()
        }
    }

    suspend fun blockSubnet(address: IPAddress) {
        ds?.put(key(keySubnet + toKeyFormat(address)), toKeyFormat(address).toByteArray())
        lock.withLock {
            blockedSubnets.add(address)
        }
    }

    suspend fun unblockSubnet(address: IPAddress) {
        ds?.delete(key(keySubnet + toKeyFormat(address)))
        lock.withLock {
            blockedSubnets.remove(address)
        }
    }

    fun listBlockedSubnets(): List<IPAddress> {
        return lock.withLock {
            blockedSubnets.toList()
        }
    }

    override fun close() {
        ds?.close()
    }

    private suspend fun loadRules() {
        if (ds != null) {
            lock.withLock {
                // load blocked peers
                ds.query(Query(prefix = keyPeer))
                    .onSuccess {
                        it
                            .queryFilterError { logger.error { "error querying datastore for blocked peers: ${errorMessage(it)}" } }
                            .mapNotNull { entry -> entry.value }
                            .mapNotNull { bytes ->
                                PeerId.fromBytes(bytes)
                                    .getOrElse {
                                        logger.error { "Could not convert bytes to PeerId: ${errorMessage(it)}" }
                                        null
                                    }
                            }
                            .collect { peerId ->
                                blockedPeers.add(peerId)
                            }
                    }
                    .onFailure {
                        logger.warn { "error querying datastore for blocked peers: ${errorMessage(it)}" }
                    }

                // load blocked addresses
                ds.query(Query(prefix = keyAddr))
                    .onSuccess {
                        it
                            .queryFilterError { logger.error { "error querying datastore for blocked addrs: ${errorMessage(it)}" } }
                            .mapNotNull { entry -> entry.value }
                            .mapNotNull { bytes -> IPAddressString(String(bytes)).address }
                            .collect { address ->
                                blockedAddresses.add(address)
                            }
                    }
                    .onFailure {
                        logger.warn { "error querying datastore for blocked peers: ${errorMessage(it)}" }
                    }

                // load blocked addrs
                ds.query(Query(prefix = keySubnet))
                    .onSuccess {
                        it
                            .queryFilterError { logger.error { "error querying datastore for blocked subnets: ${errorMessage(it)}" } }
                            .mapNotNull { entry -> entry.value }
                            .mapNotNull { bytes -> fromKeyFormat(String(bytes)) }
                            .collect { subnet ->
                                blockedSubnets.add(subnet)
                            }
                    }
                    .onFailure {
                        logger.warn { "error querying datastore for blocked subnets: ${errorMessage(it)}" }
                    }
            }
        }
    }

    private fun toKeyFormat(address: IPAddress): String {
        return if (address.isPrefixed) {
            val prefix = address.prefixLength
            val addressWithoutPrefix = address.withoutPrefixLength()
            val x = "${addressWithoutPrefix.toNormalizedString()}#$prefix"
            println(x)
            x
        } else {
            address.toNormalizedString()
        }
    }

    private fun fromKeyFormat(address: String): IPAddress? {
        if (address.contains('#')) {
            val parts = address.split('#')
            if (parts.size != 2) {
                logger.warn { "Malformed key format for subnet: $address" }
                return null
            }
            val addressWithoutPrefix = IPAddressString(parts[0]).address
            if (addressWithoutPrefix == null) {
                logger.warn { "Malformed key format for subnet: $address" }
                return null
            }
            val prefix = parts[1].toIntOrNull()
            if (prefix == null) {
                logger.warn { "Malformed key format for subnet: $address" }
                return null
            }
            val x = addressWithoutPrefix.setPrefixLength(prefix)
            println(x)
            return x
        } else {
            return IPAddressString(address).address
        }
    }

    private fun isAllowed(address: IPAddress): Boolean {
        if (blockedAddresses.contains(address)) {
            return false
        }
        for (subnet in blockedSubnets) {
            if (subnet.contains(address)) {
                return false
            }
        }
        return true
    }

    companion object {
        private const val ns = "/libp2p/net/connectiongater"
        private const val keyPeer = "/peer/"
        private const val keyAddr = "/addr/"
        private const val keySubnet = "/subnet/"

        suspend fun create(scope: CoroutineScope, child: Datastore? = null): BasicConnectionGater {
            val cg = BasicConnectionGater(scope, child)
            cg.loadRules()
            return cg
        }
    }
}
