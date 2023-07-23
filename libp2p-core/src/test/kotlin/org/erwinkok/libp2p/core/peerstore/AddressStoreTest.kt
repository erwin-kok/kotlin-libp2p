// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
@file:OptIn(ExperimentalCoroutinesApi::class)

package org.erwinkok.libp2p.core.peerstore

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.erwinkok.libp2p.core.base.EpochTimeProvider
import org.erwinkok.libp2p.core.datastore.MapDatastore
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.host.builder.PeerstoreConfig
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.peerstore.Peerstore.Companion.PermanentAddrTTL
import org.erwinkok.libp2p.core.record.Envelope
import org.erwinkok.libp2p.core.record.PeerRecord
import org.erwinkok.libp2p.crypto.KeyType
import org.erwinkok.result.coAssertErrorResult
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Collections
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal class AddressStoreTest {
    @Test
    fun addSingleAddress() = runTest {
        val memoryDatastore = MapDatastore(this)
        val peerId = randomPeerId()
        val multiaddresses = generateRandomInetMultiaddress(1)
        val addressStore = AddressStore.create(this, memoryDatastore, EpochTimeProvider.test, PeerstoreConfig()).expectNoErrors()
        addressStore.addAddresses(peerId, multiaddresses, 1.hours)
        assertInetMultiaddressEqual(multiaddresses, addressStore.addresses(peerId))
        addressStore.close()
        memoryDatastore.close()
    }

    @Test
    fun idempotentAddSingleAddress() = runTest {
        val memoryDatastore = MapDatastore(this)
        val peerId = randomPeerId()
        val multiaddresses = generateRandomInetMultiaddress(1)
        val addressStore = AddressStore.create(this, memoryDatastore, EpochTimeProvider.test, PeerstoreConfig()).expectNoErrors()
        addressStore.addAddresses(peerId, multiaddresses, 1.hours)
        addressStore.addAddresses(peerId, multiaddresses, 1.hours)
        assertInetMultiaddressEqual(multiaddresses, addressStore.addresses(peerId))
        addressStore.close()
        memoryDatastore.close()
    }

    @Test
    fun addMultipleAddresses() = runTest {
        val memoryDatastore = MapDatastore(this)
        val peerId = randomPeerId()
        val multiaddresses = generateRandomInetMultiaddress(3)
        val addressStore = AddressStore.create(this, memoryDatastore, EpochTimeProvider.test, PeerstoreConfig()).expectNoErrors()
        addressStore.addAddresses(peerId, multiaddresses, 1.hours)
        assertInetMultiaddressEqual(multiaddresses, addressStore.addresses(peerId))
        addressStore.close()
        memoryDatastore.close()
    }

    @Test
    fun idempotentAddMultipleAddresses() = runTest {
        val memoryDatastore = MapDatastore(this)
        val peerId = randomPeerId()
        val multiaddresses = generateRandomInetMultiaddress(3)
        val addressStore = AddressStore.create(this, memoryDatastore, EpochTimeProvider.test, PeerstoreConfig()).expectNoErrors()
        addressStore.addAddresses(peerId, multiaddresses, 1.hours)
        addressStore.addAddresses(peerId, multiaddresses, 1.hours)
        assertInetMultiaddressEqual(multiaddresses, addressStore.addresses(peerId))
        addressStore.close()
        memoryDatastore.close()
    }

    @Test
    fun addingAnExistingAddressWithALaterExpirationExtendsItsTtl() = runTest {
        val memoryDatastore = MapDatastore(this)
        val peerId = randomPeerId()
        val multiaddresses = generateRandomInetMultiaddress(3)
        val timeProvider = EpochTimeProvider.test
        val addressStore = AddressStore.create(this, memoryDatastore, EpochTimeProvider.test, PeerstoreConfig()).expectNoErrors()
        addressStore.addAddresses(peerId, multiaddresses, 1.seconds)
        addressStore.addAddresses(peerId, subList(multiaddresses, 2), 1.hours)
        timeProvider.advanceTime(10.seconds)
        assertInetMultiaddressEqual(subList(multiaddresses, 2), addressStore.addresses(peerId))
        addressStore.updateAddresses(peerId, 1.hours, Duration.ZERO)
        assertInetMultiaddressEqual(listOf(), addressStore.addresses(peerId))
        addressStore.close()
        memoryDatastore.close()
    }

    @Test
    fun addingAnExistingAddressWithAnEarlierExpirationNeverReducesTheExpiration() = runTest {
        val memoryDatastore = MapDatastore(this)
        val peerId = randomPeerId()
        val multiaddresses = generateRandomInetMultiaddress(3)
        val timeProvider = EpochTimeProvider.test
        val addressStore = AddressStore.create(this, memoryDatastore, EpochTimeProvider.test, PeerstoreConfig()).expectNoErrors()
        addressStore.addAddresses(peerId, multiaddresses, 1.hours)
        addressStore.addAddresses(peerId, subList(multiaddresses, 2), 1.seconds)
        timeProvider.advanceTime(1.seconds)
        assertInetMultiaddressEqual(multiaddresses, addressStore.addresses(peerId))
        addressStore.close()
        memoryDatastore.close()
    }

    @Test
    fun addingAnExistingAddressWithAnEarlierExpirationNeverReducesTheTtl() = runTest {
        val memoryDatastore = MapDatastore(this)
        val peerId = randomPeerId()
        val multiaddresses = generateRandomInetMultiaddress(1)
        val timeProvider = EpochTimeProvider.test
        val addressStore = AddressStore.create(this, memoryDatastore, EpochTimeProvider.test, PeerstoreConfig()).expectNoErrors()
        addressStore.addAddresses(peerId, multiaddresses, 40.seconds)
        timeProvider.advanceTime(2.seconds)
        addressStore.addAddresses(peerId, multiaddresses, 30.seconds)
        timeProvider.advanceTime(1.seconds)
        addressStore.addAddresses(peerId, multiaddresses, 10.seconds)
        assertInetMultiaddressEqual(multiaddresses, addressStore.addresses(peerId))
        addressStore.updateAddresses(peerId, 40.seconds, Duration.ZERO)
        assertInetMultiaddressEqual(listOf(), addressStore.addresses(peerId))
        addressStore.close()
        memoryDatastore.close()
    }

    @Test
    fun accessingAnEmptyPeerId() = runTest {
        val memoryDatastore = MapDatastore(this)
        val peerId = PeerId.Null
        val multiaddresses = generateRandomInetMultiaddress(5)
        val addressStore = AddressStore.create(this, memoryDatastore, EpochTimeProvider.test, PeerstoreConfig()).expectNoErrors()
        addressStore.addAddresses(peerId, multiaddresses, 1.hours)
        assertInetMultiaddressEqual(multiaddresses, addressStore.addresses(peerId))
        addressStore.close()
        memoryDatastore.close()
    }

    @Test
    fun addAP2pAddressWithValidPeerId() = runTest {
        val memoryDatastore = MapDatastore(this)
        val peerId = randomPeerId()
        val multiaddress = generateRandomInetMultiaddress(1)
        val addressStore = AddressStore.create(this, memoryDatastore, EpochTimeProvider.test, PeerstoreConfig()).expectNoErrors()
        addressStore.addAddress(peerId, multiaddress[0].withPeerId(peerId), 1.hours)
        assertInetMultiaddressEqual(multiaddress, addressStore.addresses(peerId))
        addressStore.close()
        memoryDatastore.close()
    }

    @Test
    fun addAP2pAddressWithInvalidPeerId() = runTest {
        val memoryDatastore = MapDatastore(this)
        val peerId1 = randomPeerId()
        val peerId2 = randomPeerId()
        val multiaddress = generateRandomInetMultiaddress(1)
        val addressStore = AddressStore.create(this, memoryDatastore, EpochTimeProvider.test, PeerstoreConfig()).expectNoErrors()
        addressStore.addAddress(peerId2, multiaddress[0].withPeerId(peerId1), 1.hours)
        assertInetMultiaddressEqual(listOf(), addressStore.addresses(peerId2))
        addressStore.close()
        memoryDatastore.close()
    }

    @Test
    fun testClearWorks() = runTest {
        val memoryDatastore = MapDatastore(this)
        val peerId0 = randomPeerId()
        val peerId1 = randomPeerId()
        val multiaddresses = generateRandomInetMultiaddress(5)
        val addressStore = AddressStore.create(this, memoryDatastore, EpochTimeProvider.test, PeerstoreConfig()).expectNoErrors()
        addressStore.addAddresses(peerId0, subList(multiaddresses, 0, 3), 1.hours)
        addressStore.addAddresses(peerId1, subList(multiaddresses, 3), 1.hours)
        assertInetMultiaddressEqual(subList(multiaddresses, 0, 3), addressStore.addresses(peerId0))
        assertInetMultiaddressEqual(subList(multiaddresses, 3), addressStore.addresses(peerId1))
        addressStore.clearAddresses(peerId0)
        assertInetMultiaddressEqual(listOf(), addressStore.addresses(peerId0))
        assertInetMultiaddressEqual(subList(multiaddresses, 3), addressStore.addresses(peerId1))
        addressStore.clearAddresses(peerId1)
        assertInetMultiaddressEqual(listOf(), addressStore.addresses(peerId0))
        assertInetMultiaddressEqual(listOf(), addressStore.addresses(peerId1))
        addressStore.close()
        memoryDatastore.close()
    }

    @Test
    fun testSetNegativeTtlClears() = runTest {
        val memoryDatastore = MapDatastore(this)
        val peerId = randomPeerId()
        val multiaddresses = generateRandomInetMultiaddress(100)
        val addressStore = AddressStore.create(this, memoryDatastore, EpochTimeProvider.test, PeerstoreConfig()).expectNoErrors()
        addressStore.setAddresses(peerId, multiaddresses, 1.hours)
        assertInetMultiaddressEqual(multiaddresses, addressStore.addresses(peerId))

        // remove two addresses.
        addressStore.setAddress(peerId, multiaddresses[50], (-1).seconds)
        addressStore.setAddress(peerId, multiaddresses[75], (-1).seconds)

        // calculate the survivors
        val survivors = subList(multiaddresses, 0, 50).toMutableList()
        survivors.addAll(subList(multiaddresses, 51, 75))
        survivors.addAll(subList(multiaddresses, 76))
        assertInetMultiaddressEqual(survivors, addressStore.addresses(peerId))

        // remove _all_ the addresses
        addressStore.setAddresses(peerId, survivors, (-1).hours)
        assertEquals(0, addressStore.addresses(peerId).size, "expected empty address list after clearing all addresses")

        // add half, but try to remove more than we added
        addressStore.setAddresses(peerId, subList(multiaddresses, 0, 50), 1.hours)
        addressStore.setAddresses(peerId, multiaddresses, (-1).seconds)
        assertEquals(0, addressStore.addresses(peerId).size, "expected empty address list after clearing all addresses")

        // try to remove the same addr multiple times
        addressStore.setAddresses(peerId, subList(multiaddresses, 0, 5), 1.hours)
        val repeated = Collections.nCopies(10, multiaddresses[0])
        addressStore.setAddresses(peerId, repeated, (-1).hours)
        assertEquals(4, addressStore.addresses(peerId).size, "expected 4 addrs after removing one, got " + addressStore.addresses(peerId).size)

        addressStore.close()
        memoryDatastore.close()
    }

    @Test
    fun testUpdateTtlOfPeerWithNoAddresses() = runTest {
        val memoryDatastore = MapDatastore(this)
        val addressStore = AddressStore.create(this, memoryDatastore, EpochTimeProvider.test, PeerstoreConfig()).expectNoErrors()
        val peerId = randomPeerId()
        addressStore.updateAddresses(peerId, 1.hours, 1.minutes)
        addressStore.close()
        memoryDatastore.close()
    }

    @Test
    fun testUpdateTo0ClearsAddresses() = runTest {
        val memoryDatastore = MapDatastore(this)
        val addressStore = AddressStore.create(this, memoryDatastore, EpochTimeProvider.test, PeerstoreConfig()).expectNoErrors()
        val peerId = randomPeerId()
        val multiaddresses = generateRandomInetMultiaddress(1)
        addressStore.setAddresses(peerId, multiaddresses, 1.hours)
        addressStore.updateAddresses(peerId, 1.hours, Duration.ZERO)
        assertEquals(0, addressStore.addresses(peerId).size)
        addressStore.close()
        memoryDatastore.close()
    }

    @Test
    fun testUpdateTtlsSuccessfully() = runTest {
        val memoryDatastore = MapDatastore(this)
        val timeProvider = EpochTimeProvider.test
        val addressStore = AddressStore.create(this, memoryDatastore, timeProvider, PeerstoreConfig()).expectNoErrors()
        val peerId1 = randomPeerId()
        val peerId2 = randomPeerId()
        val multiaddresses1 = generateRandomInetMultiaddress(2)
        val multiaddresses2 = generateRandomInetMultiaddress(2)
        addressStore.setAddress(peerId1, multiaddresses1[0], 1.hours)
        addressStore.setAddress(peerId1, multiaddresses1[1], 1.minutes)
        addressStore.setAddress(peerId2, multiaddresses2[0], 1.hours)
        addressStore.setAddress(peerId2, multiaddresses2[1], 1.minutes)

        assertInetMultiaddressEqual(multiaddresses1, addressStore.addresses(peerId1))
        assertInetMultiaddressEqual(multiaddresses2, addressStore.addresses(peerId2))

        addressStore.updateAddresses(peerId1, 1.hours, 10.seconds)

        assertInetMultiaddressEqual(multiaddresses1, addressStore.addresses(peerId1))
        assertInetMultiaddressEqual(multiaddresses2, addressStore.addresses(peerId2))

        timeProvider.advanceTime(15.seconds)

        assertInetMultiaddressEqual(listOf(multiaddresses1[1]), addressStore.addresses(peerId1))
        assertInetMultiaddressEqual(multiaddresses2, addressStore.addresses(peerId2))

        addressStore.updateAddresses(peerId2, 1.hours, 10.seconds)

        assertInetMultiaddressEqual(listOf(multiaddresses1[1]), addressStore.addresses(peerId1))
        assertInetMultiaddressEqual(multiaddresses2, addressStore.addresses(peerId2))

        timeProvider.advanceTime(15.seconds)

        assertInetMultiaddressEqual(listOf(multiaddresses1[1]), addressStore.addresses(peerId1))
        assertInetMultiaddressEqual(listOf(multiaddresses2[1]), addressStore.addresses(peerId2))

        addressStore.close()
        memoryDatastore.close()
    }

    @Test
    fun testAddressesExpire() = runTest {
        val memoryDatastore = MapDatastore(this)
        val timeProvider = EpochTimeProvider.test
        val addressStore = AddressStore.create(this, memoryDatastore, timeProvider, PeerstoreConfig()).expectNoErrors()
        val peerId1 = randomPeerId()
        val peerId2 = randomPeerId()
        val multiaddresses1 = generateRandomInetMultiaddress(3)
        val multiaddresses2 = generateRandomInetMultiaddress(2)

        addressStore.addAddresses(peerId1, multiaddresses1, 1.hours)
        addressStore.addAddresses(peerId2, multiaddresses2, 1.hours)

        assertInetMultiaddressEqual(multiaddresses1, addressStore.addresses(peerId1))
        assertInetMultiaddressEqual(multiaddresses2, addressStore.addresses(peerId2))

        addressStore.addAddresses(peerId1, multiaddresses1, 2.hours)
        addressStore.addAddresses(peerId2, multiaddresses2, 2.hours)

        assertInetMultiaddressEqual(multiaddresses1, addressStore.addresses(peerId1))
        assertInetMultiaddressEqual(multiaddresses2, addressStore.addresses(peerId2))

        addressStore.setAddress(peerId1, multiaddresses1[0], 100.microseconds)
        timeProvider.advanceTime(100.milliseconds)
        assertInetMultiaddressEqual(listOf(multiaddresses1[1], multiaddresses1[2]), addressStore.addresses(peerId1))
        assertInetMultiaddressEqual(multiaddresses2, addressStore.addresses(peerId2))

        addressStore.setAddress(peerId1, multiaddresses1[2], 100.microseconds)
        timeProvider.advanceTime(100.milliseconds)
        assertInetMultiaddressEqual(listOf(multiaddresses1[1]), addressStore.addresses(peerId1))
        assertInetMultiaddressEqual(multiaddresses2, addressStore.addresses(peerId2))

        addressStore.setAddress(peerId2, multiaddresses2[0], 100.microseconds)
        timeProvider.advanceTime(100.milliseconds)
        assertInetMultiaddressEqual(listOf(multiaddresses1[1]), addressStore.addresses(peerId1))
        assertInetMultiaddressEqual(listOf(multiaddresses2[1]), addressStore.addresses(peerId2))

        addressStore.setAddress(peerId2, multiaddresses2[1], 100.microseconds)
        timeProvider.advanceTime(100.milliseconds)
        assertInetMultiaddressEqual(listOf(multiaddresses1[1]), addressStore.addresses(peerId1))
        assertInetMultiaddressEqual(listOf(), addressStore.addresses(peerId2))

        addressStore.setAddress(peerId1, multiaddresses1[1], 100.microseconds)
        timeProvider.advanceTime(100.milliseconds)
        assertInetMultiaddressEqual(listOf(), addressStore.addresses(peerId1))
        assertInetMultiaddressEqual(listOf(), addressStore.addresses(peerId2))

        addressStore.close()
        memoryDatastore.close()
    }

    @Test
    fun testClearWithIterator() = runTest {
        val memoryDatastore = MapDatastore(this)
        val timeProvider = EpochTimeProvider.test
        val addressStore = AddressStore.create(this, memoryDatastore, timeProvider, PeerstoreConfig()).expectNoErrors()
        val peerId1 = randomPeerId()
        val peerId2 = randomPeerId()
        val multiaddresses = generateRandomInetMultiaddress(100)

        addressStore.addAddresses(peerId1, multiaddresses.subList(0, 50), PermanentAddrTTL)
        addressStore.addAddresses(peerId2, multiaddresses.subList(50, 100), PermanentAddrTTL)

        val all = mutableSetOf<InetMultiaddress>()
        all.addAll(addressStore.addresses(peerId1))
        all.addAll(addressStore.addresses(peerId2))
        assertEquals(100, all.size)

        addressStore.clearAddresses(peerId1)
        all.clear()
        all.addAll(addressStore.addresses(peerId1))
        all.addAll(addressStore.addresses(peerId2))
        assertEquals(50, all.size)

        addressStore.clearAddresses(peerId2)
        all.clear()
        all.addAll(addressStore.addresses(peerId1))
        all.addAll(addressStore.addresses(peerId2))
        assertEquals(0, all.size)

        addressStore.close()
        memoryDatastore.close()
    }

    @Test
    fun testPeersWithAddrs() = runTest {
        val memoryDatastore = MapDatastore(this)
        val timeProvider = EpochTimeProvider.test
        val addressStore = AddressStore.create(this, memoryDatastore, timeProvider, PeerstoreConfig()).expectNoErrors()

        assertEquals(0, addressStore.peersWithAddresses().size)

        val peerId1 = randomPeerId()
        val peerId2 = randomPeerId()
        val multiaddresses = generateRandomInetMultiaddress(10)
        addressStore.addAddresses(peerId1, multiaddresses.subList(0, 5), PermanentAddrTTL)
        addressStore.addAddresses(peerId2, multiaddresses.subList(5, 10), PermanentAddrTTL)

        assertEquals(2, addressStore.peersWithAddresses().size)

        addressStore.close()
        memoryDatastore.close()
    }

    @Test
    fun testCertifiedAddresses() = runTest {
        val memoryDatastore = MapDatastore(this)
        val timeProvider = EpochTimeProvider.test
        val addressStore = AddressStore.create(this, memoryDatastore, timeProvider, PeerstoreConfig()).expectNoErrors()

        val privateKey = LocalIdentity.random(KeyType.ED25519, 256).expectNoErrors().privateKey
        val peerId = PeerId.fromPrivateKey(privateKey).expectNoErrors()
        val multiaddresses = generateRandomInetMultiaddress(10)
        val certifiedAddresses = multiaddresses.subList(0, 5).toMutableList()
        val uncertifiedAddresses = multiaddresses.subList(5, 10).toMutableList()

        val record1 = PeerRecord.fromPeerIdAndAddresses(peerId, certifiedAddresses).expectNoErrors()
        val signedRecord1 = Envelope.seal(record1, privateKey).expectNoErrors()
        val record2 = PeerRecord.fromPeerIdAndAddresses(peerId, certifiedAddresses).expectNoErrors()
        val signedRecord2 = Envelope.seal(record2, privateKey).expectNoErrors()

        addressStore.addAddresses(peerId, uncertifiedAddresses, 1.hours)
        assertInetMultiaddressEqual(uncertifiedAddresses, addressStore.addresses(peerId))

        val accepted1 = addressStore.consumePeerRecord(signedRecord2, 1.hours).expectNoErrors()
        assertTrue(accepted1, "should have accepted signed peer record")

        assertInetMultiaddressEqual(multiaddresses, addressStore.addresses(peerId))

        assertEquals(1, addressStore.peersWithAddresses().size)

        val accepted2 = addressStore.consumePeerRecord(signedRecord1, 1.hours).expectNoErrors()
        assertFalse(accepted2, "We should have failed to accept a record with an old sequence number")

        addressStore.addAddresses(peerId, uncertifiedAddresses, 1.hours)
        assertInetMultiaddressEqual(multiaddresses, addressStore.addresses(peerId))

        val record3 = addressStore.getPeerRecord(peerId)
        assertNotNull(record3)
        assertEquals(signedRecord2, record3)

        certifiedAddresses.removeAt(2)
        certifiedAddresses.removeAt(3)

        val record4 = PeerRecord.fromPeerIdAndAddresses(peerId, certifiedAddresses).expectNoErrors()
        val signedRecord4 = Envelope.seal(record4, privateKey).expectNoErrors()
        val accepted3 = addressStore.consumePeerRecord(signedRecord4, 1.hours).expectNoErrors()
        assertTrue(accepted3, "should have accepted signed peer record")
        assertInetMultiaddressEqual(multiaddresses, addressStore.addresses(peerId))

        addressStore.setAddresses(peerId, multiaddresses, (-1).seconds)
        assertEquals(0, addressStore.addresses(peerId).size)

        assertNull(addressStore.getPeerRecord(peerId))

        val accepted4 = addressStore.consumePeerRecord(signedRecord4, 1.seconds).expectNoErrors()
        assertTrue(accepted4, "should have accepted signed peer record")
        assertInetMultiaddressEqual(certifiedAddresses, addressStore.addresses(peerId))

        timeProvider.advanceTime(2.seconds)

        assertNull(addressStore.getPeerRecord(peerId))

        val privateKey2 = LocalIdentity.random(KeyType.ED25519, 256).expectNoErrors().privateKey
        val signedRecord5 = Envelope.seal(record4, privateKey2).expectNoErrors()
        coAssertErrorResult("signing key does not match PeerId in PeerRecord") { addressStore.consumePeerRecord(signedRecord5, 1.seconds) }

        addressStore.close()
        memoryDatastore.close()
    }

    @Test
    fun testMultipleStreams() = runTest {
        val memoryDatastore = MapDatastore(this)
        val peerId = randomPeerId()
        val multiaddresses = generateRandomInetMultiaddress(10)
        val addressStore = AddressStore.create(this, memoryDatastore, EpochTimeProvider.test, PeerstoreConfig()).expectNoErrors()
        val values = mutableListOf<InetMultiaddress>()
        val flow = addressStore.addressStream(peerId)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            flow.toList(values)
        }
        addressStore.addAddresses(peerId, multiaddresses, 1.hours)
        assertInetMultiaddressEqual(multiaddresses, values)
        addressStore.close()
        memoryDatastore.close()
    }

    private fun randomPeerId() = LocalIdentity.random().expectNoErrors().peerId

    private fun generateRandomInetMultiaddress(nr: Int): List<InetMultiaddress> {
        val result = mutableListOf<InetMultiaddress>()
        repeat(nr) {
            val ip = Random.nextInt(256)
            val port = Random.nextInt(65536)
            val address = InetMultiaddress.fromString("/ip4/1.1.1.$ip/tcp/$port").expectNoErrors()
            result.add(address)
        }
        return result
    }

    private fun assertInetMultiaddressEqual(expected: List<InetMultiaddress>, actual: List<InetMultiaddress>) {
        assertEquals(expected.size, actual.size, "Expected and actual list sizes differ")
        for (multiaddress in actual) {
            assertTrue(expected.contains(multiaddress), "Multiaddress lists are not equal")
        }
    }

    private fun <T> subList(list: List<T>, begin: Int): List<T> {
        return list.subList(begin, list.size).toList()
    }

    private fun <T> subList(list: List<T>, begin: Int, end: Int): List<T> {
        return list.subList(begin, end).toList()
    }
}
