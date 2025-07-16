// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.peerstore

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.base.EpochTimeProvider
import org.erwinkok.libp2p.core.datastore.BatchingDatastore
import org.erwinkok.libp2p.core.datastore.Datastore
import org.erwinkok.libp2p.core.datastore.Key
import org.erwinkok.libp2p.core.datastore.Key.Companion.key
import org.erwinkok.libp2p.core.datastore.query.OrderByKey
import org.erwinkok.libp2p.core.datastore.query.Query
import org.erwinkok.libp2p.core.datastore.query.queryFilterError
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.host.builder.PeerstoreConfig
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.peerstore.addressbook.AddressBookRecord
import org.erwinkok.libp2p.core.peerstore.cache.Cache
import org.erwinkok.libp2p.core.peerstore.cache.CaffeineCache
import org.erwinkok.libp2p.core.peerstore.cache.NoopCache
import org.erwinkok.libp2p.core.record.Envelope
import org.erwinkok.libp2p.core.record.PeerRecord
import org.erwinkok.multiformat.multibase.bases.Base32
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.erwinkok.result.flatMap
import org.erwinkok.result.getOr
import org.erwinkok.result.getOrElse
import org.erwinkok.result.map
import org.erwinkok.result.onFailure
import org.erwinkok.result.onSuccess
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

class AddressStore private constructor(
    private val scope: CoroutineScope,
    private val datastore: BatchingDatastore,
    private val timeProvider: EpochTimeProvider,
    private val gcInitialDelay: Duration,
    private val gcPurgeInterval: Duration,
    cacheSize: Long,
) : AwaitableClosable {
    private val _context = Job(scope.coroutineContext[Job])
    private val addressStreamManager = AddressStreamManager()
    private val cache: Cache<PeerId, AddressBookRecord> = if (cacheSize > 0) {
        CaffeineCache(cacheSize)
    } else {
        NoopCache()
    }

    override val jobContext: Job get() = _context

    init {
        if (gcPurgeInterval.isPositive()) {
            gcPurge()
        }
    }

    suspend fun addAddress(peerId: PeerId, address: InetMultiaddress, ttl: Duration) {
        addAddresses(peerId, listOf(address), ttl)
    }

    suspend fun addAddresses(peerId: PeerId, addresses: List<InetMultiaddress>, ttl: Duration) {
        loadRecord(peerId, cache = true, update = false)
            .map {
                it.addAddresses(addresses, now(), ttl)
            }
    }

    suspend fun setAddress(peerId: PeerId, address: InetMultiaddress, ttl: Duration) {
        setAddresses(peerId, listOf(address), ttl)
    }

    suspend fun setAddresses(peerId: PeerId, addresses: List<InetMultiaddress>, ttl: Duration) {
        loadRecord(peerId, cache = true, update = false)
            .map {
                it.setAddresses(addresses, now(), ttl)
            }
    }

    suspend fun updateAddresses(peerId: PeerId, oldTTL: Duration, newTTL: Duration) {
        loadRecord(peerId, cache = true, update = false)
            .map {
                it.updateAddresses(now(), oldTTL, newTTL)
            }
    }

    suspend fun addresses(peerId: PeerId): List<InetMultiaddress> {
        val record = loadRecord(peerId, cache = true, update = true)
            .getOrElse {
                logger.error { "Unable to load record: ${errorMessage(it)}" }
                return listOf()
            }
        return record.addresses()
    }

    suspend fun addressStream(peerId: PeerId): SharedFlow<InetMultiaddress> {
        return addressStreamManager.addressStream(peerId)
    }

    suspend fun clearAddresses(peerId: PeerId) {
        cache.remove(peerId)
        val key = peerToKey(peerId)
        datastore.delete(key)
            .onFailure {
                logger.error { "Failed to clear addresses for peer $peerId" }
            }
    }

    suspend fun peersWithAddresses(): Set<PeerId> {
        return uniquePeerIds()
            .getOrElse {
                logger.warn { "Could not retrieve keys from datastore: ${errorMessage(it)}: " }
                return setOf()
            }
            .toSet()
    }

    //
    // CertifiedAddressBook
    //
    suspend fun consumePeerRecord(recordEnvelope: Envelope, ttl: Duration): Result<Boolean> {
        val record = recordEnvelope.record()
            .getOrElse { return Err(it) }
        if (record !is PeerRecord) {
            return Err("unable to process envelope: not a PeerRecord")
        }
        if (!record.peerId.matchesPublicKey(recordEnvelope.publicKey)) {
            return Err("signing key does not match PeerId in PeerRecord")
        }
        val rawBytes = recordEnvelope.marshal()
            .getOrElse { return Err(it) }
        val addressRecord = loadRecord(record.peerId, cache = true, update = false)
            .getOrElse {
                logger.error { "Unable to load record: ${errorMessage(it)}" }
                return Err(it)
            }
        val accept = addressRecord.consumePeerRecord(record, rawBytes, now(), ttl)
            .getOrElse { return Err(it) }
        return Ok(accept)
    }

    suspend fun getPeerRecord(peerId: PeerId): Envelope? {
        val record = loadRecord(peerId, cache = true, update = false)
            .getOrElse {
                logger.error { "Unable to load record: ${errorMessage(it)}" }
                return null
            }
        return record.getPeerRecord()
    }

    override fun close() {
        _context.cancel()
    }

    private fun now(): Instant {
        return timeProvider.time()
    }

    private suspend fun loadRecord(peerId: PeerId, cache: Boolean, update: Boolean): Result<AddressBookRecord> {
        val addressRecord = this.cache.get(peerId)
        addressRecord?.mutex?.withLock {
            if (addressRecord.clean(now()) && update) {
                addressRecord.flush(datastore)
                    .onFailure { return Err(it) }
            }
            return Ok(addressRecord)
        }
        val newAddressRecord = datastore.get(peerToKey(peerId))
            .flatMap {
                AddressBookRecord.deserialize(addressStreamManager, datastore, it)
            }.getOrElse {
                if (it != Datastore.ErrNotFound) {
                    logger.warn { "Error occurred while loading AddressBookRecord for peer $peerId: ${errorMessage(it)}" }
                    return Err(it)
                }
                AddressBookRecord(addressStreamManager, datastore, peerId)
            }
        // Its a new and local record, no need to lock
        if (newAddressRecord.clean(now()) && update) {
            newAddressRecord.flush(datastore)
        }
        if (cache) {
            this.cache.add(peerId, newAddressRecord)
        }
        return Ok(newAddressRecord)
    }

    // "//peers/addresses/<peer-id>"
    private fun uniquePeerIds(): Result<Flow<PeerId>> {
        val keys = datastore.query(Query(prefix = AddressBookBase, keysOnly = true))
            .getOrElse { return Err(it) }
        val peers = keys
            .queryFilterError { logger.error { "Error retrieving peer ids: ${errorMessage(it)}" } }
            .map { it.key.name }
            .mapNotNull {
                Base32.decodeStdNoPad(it)
                    .flatMap { peerBytes -> PeerId.fromBytes(peerBytes) }
                    .onFailure { logger.warn { "Can not convert PeerId from bytes in KeyStore" } }
                    .getOr(null)
            }
        return Ok(peers)
    }

    private fun gcPurge() {
        scope.launch(_context + CoroutineName("address-store-gc")) {
            try {
                delay(gcInitialDelay)
                while (_context.isActive) {
                    logger.info { "Purging AddressBook GC entries..." }
                    CyclicBatch.create(datastore, DefaultOpsPerCyclicBatch)
                        .onSuccess { batch ->
                            datastore.query(PurgeStoreQuery)
                                .onSuccess { queryResult ->
                                    queryResult
                                        .queryFilterError { logger.error { "Error retrieving key: ${errorMessage(it)}" } }
                                        .mapNotNull { entry -> entry.value }
                                        .mapNotNull { bytes ->
                                            AddressBookRecord.deserialize(addressStreamManager, datastore, bytes)
                                                .getOrElse {
                                                    logger.error { "Could not convert bytes to AddressBookRecord: ${errorMessage(it)}" }
                                                    null
                                                }
                                        }
                                        .onCompletion { batch.commit() }
                                        .collect { addressBookRecord ->
                                            // Its a new and local record, no need to lock
                                            if (addressBookRecord.clean(now())) {
                                                addressBookRecord.flush(batch)
                                                cache.remove(addressBookRecord.peerId)
                                            }
                                        }
                                }
                                .onFailure {
                                    logger.warn { "Could not query datastore in AddressStore GC" }
                                }
                        }
                        .onFailure {
                            logger.warn { "Failed to create cyclic batch" }
                        }
                    delay(gcPurgeInterval)
                }
            } catch (_: CancellationException) {
                // Do nothing, just return...
            }
        }
    }

    companion object {
        private const val AddressBookBase = "/peers/addresses"
        const val DefaultCacheSize = 1024L
        const val DefaultOpsPerCyclicBatch = 20
        val DefaultGcInitialDelay = 60.seconds
        val DefaultGcPurgeInterval = 2.hours
        val PurgeStoreQuery = Query(prefix = AddressBookBase, orders = listOf(OrderByKey()), keysOnly = false)

        fun create(scope: CoroutineScope, datastore: BatchingDatastore, timeProvider: EpochTimeProvider, peerstoreConfig: PeerstoreConfig? = null): Result<AddressStore> {
            val gcInitialDelay: Duration = peerstoreConfig?.gcInitialDelay ?: DefaultGcInitialDelay
            if (gcInitialDelay.isNegative()) {
                return Err("Negative Gc Initial Delay provided")
            }
            val gcPurgeInterval: Duration = peerstoreConfig?.gcPurgeInterval ?: DefaultGcPurgeInterval
            if (gcPurgeInterval.isNegative()) {
                return Err("Negative Gc Purge Interval provided")
            }
            val cacheSize: Long = peerstoreConfig?.cacheSize ?: DefaultCacheSize
            if (cacheSize < 0) {
                return Err("Negative Cache Size Provided")
            }
            return Ok(AddressStore(scope, datastore, timeProvider, gcInitialDelay, gcPurgeInterval, cacheSize))
        }

        internal fun peerToKey(peerId: PeerId): Key {
            val peerIdb32 = Base32.encodeStdLowerNoPad(peerId.idBytes())
            return key("$AddressBookBase/$peerIdb32")
        }
    }
}
