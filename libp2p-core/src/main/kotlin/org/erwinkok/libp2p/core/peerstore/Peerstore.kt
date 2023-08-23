// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.peerstore

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.base.EpochTimeProvider
import org.erwinkok.libp2p.core.datastore.BatchingDatastore
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.host.RemoteIdentity
import org.erwinkok.libp2p.core.host.builder.PeerstoreConfig
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.record.AddressInfo
import org.erwinkok.libp2p.core.record.Envelope
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class Peerstore private constructor(
    scope: CoroutineScope,
    val datastore: BatchingDatastore,
    private val keyStore: KeyStore,
    private val addressStore: AddressStore,
    private val metricsStore: MetricsStore,
    private val protocolsStore: ProtocolsStore,
) : AwaitableClosable {
    private val _context = Job(scope.coroutineContext[Job])
    val metadataStore = MetadataStore(datastore)

    override val jobContext: Job get() = _context

    fun peerInfoIds(addressInfos: List<AddressInfo>): List<PeerId> {
        return addressInfos.map(AddressInfo::peerId)
    }

    suspend fun peerInfo(peers: Set<PeerId>): List<AddressInfo> {
        return peers.map { i -> peerInfo(i) }
    }

    suspend fun peerInfo(peerId: PeerId): AddressInfo {
        return AddressInfo.fromPeerIdAndAddresses(peerId, addresses(peerId))
    }

    //
    // AddressStore
    //

    suspend fun addAddress(peerId: PeerId, address: InetMultiaddress, ttl: Duration) {
        return addressStore.addAddress(peerId, address, ttl)
    }

    suspend fun addAddresses(peerId: PeerId, addresses: List<InetMultiaddress>, ttl: Duration) {
        return addressStore.addAddresses(peerId, addresses, ttl)
    }

    suspend fun setAddress(peerId: PeerId, address: InetMultiaddress, ttl: Duration) {
        return addressStore.setAddress(peerId, address, ttl)
    }

    suspend fun setAddresses(peerId: PeerId, addresses: List<InetMultiaddress>, ttl: Duration) {
        return addressStore.setAddresses(peerId, addresses, ttl)
    }

    suspend fun updateAddresses(peerId: PeerId, oldTTL: Duration, newTTL: Duration) {
        return addressStore.updateAddresses(peerId, oldTTL, newTTL)
    }

    suspend fun addresses(peerId: PeerId): List<InetMultiaddress> {
        return addressStore.addresses(peerId)
    }

    suspend fun addressStream(peerId: PeerId): SharedFlow<InetMultiaddress> {
        return addressStore.addressStream(peerId)
    }

    suspend fun clearAddresses(peerId: PeerId) {
        return addressStore.clearAddresses(peerId)
    }

    suspend fun peersWithAddresses(): Set<PeerId> {
        return addressStore.peersWithAddresses()
    }

    suspend fun consumePeerRecord(recordEnvelope: Envelope, ttl: Duration): Result<Boolean> {
        return addressStore.consumePeerRecord(recordEnvelope, ttl)
    }

    suspend fun getPeerRecord(peerId: PeerId): Envelope? {
        return addressStore.getPeerRecord(peerId)
    }

    //
    // KeyStore
    //

    suspend fun addRemoteIdentity(remoteIdentity: RemoteIdentity): Result<Unit> {
        return keyStore.addRemoteIdentity(remoteIdentity)
    }

    suspend fun addLocalIdentity(localIdentity: LocalIdentity): Result<Unit> {
        return keyStore.addLocalIdentity(localIdentity)
    }

    suspend fun remoteIdentity(peerId: PeerId): RemoteIdentity? {
        return keyStore.remoteIdentity(peerId)
    }

    suspend fun localIdentity(peerId: PeerId): LocalIdentity? {
        return keyStore.localIdentity(peerId)
    }

    suspend fun peersWithKeys(): Set<PeerId> {
        return keyStore.peersWithKeys()
    }

    //
    // MetricsStore
    //

    suspend fun recordLatency(peerId: PeerId, next: Long) {
        metricsStore.recordLatency(peerId, next)
    }

    suspend fun latencyEWMA(peerId: PeerId): Long {
        return metricsStore.latencyEWMA(peerId)
    }

    //
    // ProtocolsStore
    //

    suspend fun getProtocols(peerId: PeerId): Result<Set<ProtocolId>> {
        return protocolsStore.getProtocols(peerId)
    }

    suspend fun addProtocols(peerId: PeerId, protocols: Set<ProtocolId>): Result<Unit> {
        return protocolsStore.addProtocols(peerId, protocols)
    }

    suspend fun setProtocols(peerId: PeerId, protocols: Set<ProtocolId>): Result<Unit> {
        return protocolsStore.setProtocols(peerId, protocols)
    }

    suspend fun removeProtocols(peerId: PeerId, protocols: Set<ProtocolId>): Result<Unit> {
        return protocolsStore.removeProtocols(peerId, protocols)
    }

    suspend fun supportsProtocols(peerId: PeerId, protocols: Set<ProtocolId>): Result<Set<ProtocolId>> {
        return protocolsStore.supportsProtocols(peerId, protocols)
    }

    suspend fun firstSupportedProtocol(peerId: PeerId, protocols: Set<ProtocolId>): Result<ProtocolId> {
        return protocolsStore.firstSupportedProtocol(peerId, protocols)
    }

    //
    // MetadataStore
    //

    suspend inline fun <reified T> get(peerId: PeerId, key: String): Result<T> {
        return metadataStore.get(peerId, key)
    }

    suspend inline fun <reified T> put(peerId: PeerId, key: String, value: T): Result<Unit> {
        return metadataStore.put(peerId, key, value)
    }

    suspend fun peers(): Set<PeerId> {
        val set = mutableSetOf<PeerId>()
        set.addAll(peersWithKeys())
        set.addAll(peersWithAddresses())
        return set
    }

    // Note that this method does NOT remove the peer from the AddressStore!
    suspend fun removePeer(peerId: PeerId) {
        keyStore.removePeer(peerId)
        metricsStore.removePeer(peerId)
        metadataStore.removePeer(peerId)
        protocolsStore.removePeer(peerId)
    }

    override fun close() {
        addressStore.close()
        datastore.close()
        _context.complete()
    }

    companion object {
        val AddressTTL = 1.hours
        val TempAddrTTL = 2.minutes
        val ProviderAddrTTL = 30.minutes
        val RecentlyConnectedAddrTTL = 30.minutes
        val OwnObservedAddrTTL = 30.minutes
        val PermanentAddrTTL = 3650.days
        val ConnectedAddrTTL = 3650.days

        fun create(scope: CoroutineScope, datastore: BatchingDatastore, peerstoreConfig: PeerstoreConfig? = null, timeProvider: EpochTimeProvider = EpochTimeProvider.system): Result<Peerstore> {
            return Result.zip(
                { KeyStore.create(datastore, peerstoreConfig) },
                { AddressStore.create(scope, datastore, timeProvider, peerstoreConfig) },
                { MetricsStore.create() },
                { ProtocolsStore.create(datastore, peerstoreConfig) },
            ) { keyStore, addressStore, metricsStore, protocolsStore ->
                Ok(Peerstore(scope, datastore, keyStore, addressStore, metricsStore, protocolsStore))
            }
        }
    }
}
