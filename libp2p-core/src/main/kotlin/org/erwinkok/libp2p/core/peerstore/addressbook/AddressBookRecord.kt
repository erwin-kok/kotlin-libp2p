// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.peerstore.addressbook

import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.erwinkok.libp2p.core.datastore.Datastore
import org.erwinkok.libp2p.core.datastore.Write
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.peerstore.AddressStore
import org.erwinkok.libp2p.core.peerstore.AddressStreamManager
import org.erwinkok.libp2p.core.peerstore.pb.DbPeerstore
import org.erwinkok.libp2p.core.record.Envelope
import org.erwinkok.libp2p.core.record.PeerRecord
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
import kotlin.time.Duration.Companion.milliseconds

private val logger = KotlinLogging.logger {}

class AddressBookRecord(private val addressStreamManager: AddressStreamManager, private val datastore: Datastore, val peerId: PeerId) {
    val mutex = Mutex()
    val addresses = mutableListOf<AddressEntry>()
    var certifiedRecord: CertifiedRecord? = null
    var dirty = false

    suspend fun addAddresses(addresses: List<InetMultiaddress>, now: Instant, ttl: Duration) {
        if (ttl.isPositive()) {
            val cleanAddresses = cleanAddresses(addresses, peerId)
            setAddresses(cleanAddresses, now, ttl, TTLWriteMode.TTLExtent)
        }
    }

    suspend fun setAddresses(addresses: List<InetMultiaddress>, now: Instant, ttl: Duration) {
        val cleanAddresses = cleanAddresses(addresses, peerId)
        if (ttl.isPositive()) {
            setAddresses(cleanAddresses, now, ttl, TTLWriteMode.TTLOverride)
        } else {
            deleteAddresses(cleanAddresses, now)
        }
    }

    suspend fun updateAddresses(now: Instant, oldTTL: Duration, newTTL: Duration) {
        mutex.withLock {
            val newExpiry = now.plusMillis(newTTL.inWholeMilliseconds)
            val updatedAddresses = addresses.mapNotNull { entry ->
                if (entry.ttl == oldTTL) {
                    dirty = true
                    if (newTTL.isPositive()) {
                        AddressEntry(entry.address, newExpiry, newTTL)
                    } else {
                        null
                    }
                } else {
                    entry
                }
            }
            addresses.clear()
            addresses.addAll(updatedAddresses)
            if (clean(now)) {
                flush(datastore)
            }
        }
    }

    suspend fun addresses(): List<InetMultiaddress> {
        mutex.withLock {
            return addresses.map { it.address }
        }
    }

    suspend fun consumePeerRecord(record: PeerRecord, rawBytes: ByteArray, now: Instant, ttl: Duration): Result<Boolean> {
        // ensure that the sequence from envelope is >= any previously received seq no
        // update when equal to extend the ttls
        if (latestPeerRecordSequence() > record.seq) {
            return Ok(false)
        }
        val addresses = cleanAddresses(record.addresses, record.peerId)
        setAddresses(addresses, now, ttl, TTLWriteMode.TTLExtent)
        mutex.withLock {
            certifiedRecord = CertifiedRecord(record.seq, rawBytes)
            dirty = true
            flush(datastore)
        }
        return Ok(true)
    }

    suspend fun getPeerRecord(): Envelope? {
        mutex.withLock {
            val certifiedRecord = certifiedRecord
            if (certifiedRecord == null || certifiedRecord.raw.isEmpty() || addresses.isEmpty()) {
                return null
            }
            val state = Envelope.consumeEnvelope(certifiedRecord.raw, PeerRecord.PeerRecordEnvelopeDomain)
                .getOrElse {
                    logger.warn { "error unmarshalling stored signed peer record for peer $peerId: ${errorMessage(it)}" }
                    return null
                }
            return state.envelope
        }
    }

    // Must be called within a lock
    fun clean(now: Instant): Boolean {
        if (addresses.isEmpty()) {
            // Mark this entry as dirty to indicate a flush is necessary.
            // The flush will delete the record.
            return true
        }
        dirty = dirty or addresses.removeAll { now > it.expiry }
        return dirty
    }

    // Must be called within a lock
    suspend fun flush(write: Write): Result<Unit> {
        return if (dirty) {
            val key = AddressStore.peerToKey(peerId)
            if (addresses.isEmpty()) {
                write.delete(key)
                    .onSuccess {
                        dirty = false
                    }
            } else {
                serialize()
                    .flatMap { write.put(key, it) }
                    .onSuccess { dirty = false }
            }
        } else {
            Ok(Unit)
        }
    }

    suspend fun delete(datastore: Datastore) {
        mutex.withLock {
            datastore.delete(AddressStore.peerToKey(peerId))
                .onFailure {
                    logger.warn { "failed to delete item from store for peer $peerId" }
                }
        }
    }

    // Must be called within a lock
    private fun serialize(): Result<ByteArray> {
        return try {
            val dbAddressBookRecord = DbPeerstore.AddressBookRecord.newBuilder()
                .setPeerId(ByteString.copyFrom(peerId.idBytes()))
                .addAllAddresses(convertAddressEntries(addresses))
            val cr = certifiedRecord
            if (cr != null) {
                dbAddressBookRecord.certifiedRecord = convertCertifiedRecord(cr)
            }
            Ok(dbAddressBookRecord.build().toByteArray())
        } catch (e: Exception) {
            Err("Could not serialize AddressRecord: ${errorMessage(e)}")
        }
    }

    private suspend fun setAddresses(newAddresses: List<InetMultiaddress>, now: Instant, ttl: Duration, mode: TTLWriteMode) {
        if (newAddresses.isNotEmpty()) {
            mutex.withLock {
                val newExp = now.plusMillis(ttl.inWholeMilliseconds)
                val addressMap = this.addresses.associateBy { it.address }.toMutableMap()
                newAddresses.forEach { address ->
                    val oldRecord = addressMap[address]
                    val newRecord = if (oldRecord != null) {
                        when (mode) {
                            TTLWriteMode.TTLExtent -> AddressEntry(address, max(newExp, oldRecord.expiry), max(ttl, oldRecord.ttl))
                            TTLWriteMode.TTLOverride -> AddressEntry(address, newExp, ttl)
                        }
                    } else {
                        addressStreamManager.emit(peerId, address)
                        AddressEntry(address, newExp, ttl)
                    }
                    addressMap[address] = newRecord
                }
                addresses.clear()
                addresses.addAll(addressMap.values)
                dirty = true
                if (clean(now)) {
                    flush(datastore)
                }
            }
        }
    }

    private fun max(a: Instant, b: Instant): Instant {
        return if (a.toEpochMilli() > b.toEpochMilli()) a else b
    }

    private fun max(a: Duration, b: Duration): Duration {
        return if (a.inWholeMilliseconds > b.inWholeMilliseconds) a else b
    }

    private suspend fun deleteAddresses(addrs: List<InetMultiaddress>, now: Instant) {
        mutex.withLock {
            if (addresses.isNotEmpty() && addrs.isNotEmpty()) {
                dirty = dirty or addresses.removeAll { addrs.contains(it.address) }
            }
            if (clean(now)) {
                flush(datastore)
            }
        }
    }

    private suspend fun latestPeerRecordSequence(): Long {
        mutex.withLock {
            if (addresses.isEmpty()) {
                return 0
            }
            val cr = certifiedRecord
            if (cr == null || cr.raw.isEmpty()) {
                return 0
            }
            return cr.sequence
        }
    }

    companion object {
        fun deserialize(addressStreamManager: AddressStreamManager, datastore: Datastore, bytes: ByteArray): Result<AddressBookRecord> {
            return try {
                fromDbAddressBookRecord(addressStreamManager, datastore, DbPeerstore.AddressBookRecord.parseFrom(bytes))
            } catch (_: InvalidProtocolBufferException) {
                Err("Could not parse protocol buffer")
            }
        }

        private fun fromDbAddressBookRecord(addressStreamManager: AddressStreamManager, datastore: Datastore, dbAddressBookRecord: DbPeerstore.AddressBookRecord): Result<AddressBookRecord> {
            val peerId = convertPeerId(dbAddressBookRecord)
                .getOrElse { return Err(it) }
            val addresses = convertAddressEntries(dbAddressBookRecord)
            val addressBookRecord = AddressBookRecord(addressStreamManager, datastore, peerId)
            addressBookRecord.addresses.addAll(addresses)
            addressBookRecord.certifiedRecord = convertCertifiedRecord(dbAddressBookRecord)
            return Ok(addressBookRecord)
        }

        private fun convertPeerId(dbAddressBookRecord: DbPeerstore.AddressBookRecord): Result<PeerId> {
            return PeerId.fromBytes(dbAddressBookRecord.peerId.toByteArray())
        }

        private fun convertAddressEntries(dbAddressBookRecord: DbPeerstore.AddressBookRecord): MutableList<AddressEntry> {
            return dbAddressBookRecord.addressesList.mapNotNull { convertAddressEntry(it) }.sortedBy { it.expiry }.toMutableList()
        }

        private fun convertAddressEntries(addresses: MutableList<AddressEntry>): List<DbPeerstore.AddressBookRecord.AddressEntry> {
            return addresses.map { convertAddressEntry(it) }.sortedBy { it.expiry }
        }

        private fun convertAddressEntry(addressEntry: DbPeerstore.AddressBookRecord.AddressEntry): AddressEntry? {
            return InetMultiaddress.fromBytes(addressEntry.address.toByteArray())
                .map { address -> AddressEntry(address, Instant.ofEpochMilli(addressEntry.expiry), addressEntry.ttl.milliseconds) }
                .getOr(null)
        }

        private fun convertAddressEntry(addressEntry: AddressEntry): DbPeerstore.AddressBookRecord.AddressEntry {
            return DbPeerstore.AddressBookRecord.AddressEntry.newBuilder()
                .setAddress(ByteString.copyFrom(addressEntry.address.bytes))
                .setExpiry(addressEntry.expiry.toEpochMilli())
                .setTtl(addressEntry.ttl.inWholeMilliseconds)
                .build()
        }

        private fun convertCertifiedRecord(addressBookRecord: DbPeerstore.AddressBookRecord): CertifiedRecord? {
            return if (addressBookRecord.hasCertifiedRecord()) {
                val dbCertifiedRecord = addressBookRecord.certifiedRecord
                CertifiedRecord(dbCertifiedRecord.sequence, dbCertifiedRecord.raw.toByteArray())
            } else {
                null
            }
        }

        private fun convertCertifiedRecord(certifiedRecord: CertifiedRecord): DbPeerstore.AddressBookRecord.CertifiedRecord {
            return DbPeerstore.AddressBookRecord.CertifiedRecord.newBuilder()
                .setSequence(certifiedRecord.sequence)
                .setRaw(ByteString.copyFrom(certifiedRecord.raw))
                .build()
        }

        fun cleanAddresses(addresses: List<InetMultiaddress>, peerId: PeerId): List<InetMultiaddress> {
            return addresses.mapNotNull { address ->
                val pid = address.peerId.getOr(null)
                if (pid == null || pid == peerId) {
                    address.withoutPeerId()
                } else {
                    logger.warn { "p2p address was passed to AddressBook with unexpected PeerId. Received = $pid, expected = $peerId" }
                    null
                }
            }
        }
    }
}
