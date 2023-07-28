// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
@file:OptIn(ExperimentalCoroutinesApi::class)

package org.erwinkok.libp2p.core.peerstore

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.erwinkok.libp2p.core.datastore.MapDatastore
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.host.builder.PeerstoreConfig
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.address.AddressUtilTest.Companion.assertInetMultiaddressEqual
import org.erwinkok.libp2p.core.peerstore.Peerstore.Companion.PermanentAddrTTL
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.coAssertErrorResult
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours

internal class PeerstoreTest {
    @Test
    fun addressStream() = runTest {
        val memoryDatastore = MapDatastore(this)
        val peerId = randomPeerId()
        val multiaddresses = generateRandomInetMultiaddress(100)
        val peerstore = Peerstore.create(this, memoryDatastore, PeerstoreConfig()).expectNoErrors()
        peerstore.addAddresses(peerId, multiaddresses.subList(0, 10), 1.hours)
        val flow1 = peerstore.addressStream(peerId)
        for (i in 10 until 20) {
            peerstore.addAddress(peerId, multiaddresses[i], 1.hours)
        }
        val values1 = mutableListOf<InetMultiaddress>()
        val job1 = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            flow1.toList(values1)
        }
        assertInetMultiaddressEqual(multiaddresses.subList(0, 20), values1)

        val flow2 = peerstore.addressStream(peerId)
        val values2 = mutableListOf<InetMultiaddress>()
        val job2 = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            flow2.toList(values2)
        }

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            for (i in 20 until 60) {
                peerstore.addAddress(peerId, multiaddresses[i], 1.hours)
            }
        }.join()

        job1.cancel()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            for (i in 60 until 80) {
                peerstore.addAddress(peerId, multiaddresses[i], 1.hours)
            }
        }.join()

        assertInetMultiaddressEqual(multiaddresses.subList(0, 60), values1)
        assertInetMultiaddressEqual(multiaddresses.subList(0, 80), values2)

        job2.cancel()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            for (i in 80 until 100) {
                peerstore.addAddress(peerId, multiaddresses[i], 1.hours)
            }
        }.join()

        assertInetMultiaddressEqual(multiaddresses.subList(0, 60), values1)
        assertInetMultiaddressEqual(multiaddresses.subList(0, 80), values2)

        peerstore.close()
        memoryDatastore.close()
    }

    @Test
    fun addressStreamDuplicates() = runTest {
        val memoryDatastore = MapDatastore(this)
        val peerId = randomPeerId()
        val multiaddresses = generateRandomInetMultiaddress(10)
        val peerstore = Peerstore.create(this, memoryDatastore, PeerstoreConfig()).expectNoErrors()
        val flow = peerstore.addressStream(peerId)
        for (i in 0 until 10) {
            peerstore.addAddress(peerId, multiaddresses[i], 1.hours)
            peerstore.addAddress(peerId, multiaddresses[Random.nextInt(10)], 1.hours)
            peerstore.addAddress(peerId, multiaddresses[Random.nextInt(10)], 1.hours)
        }
        val values = mutableListOf<InetMultiaddress>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            flow.toList(values)
        }
        assertInetMultiaddressEqual(multiaddresses, values)
        peerstore.close()
        memoryDatastore.close()
    }

    @Test
    fun testProtocolStore() = runTest {
        val memoryDatastore = MapDatastore(this)
        val peerstore = Peerstore.create(this, memoryDatastore, PeerstoreConfig()).expectNoErrors()
        val peerId = randomPeerId()
        val protos1 = setOf(ProtocolId.of("a"), ProtocolId.of("b"), ProtocolId.of("c"), ProtocolId.of("d"))
        peerstore.addProtocols(peerId, protos1).expectNoErrors()
        val actualProtos = peerstore.getProtocols(peerId).expectNoErrors()
        assertEquals(protos1, actualProtos)

        val supported1 = peerstore.supportsProtocols(peerId, setOf(ProtocolId.of("q"), ProtocolId.of("w"), ProtocolId.of("a"), ProtocolId.of("y"), ProtocolId.of("b"))).expectNoErrors()
        assertEquals(setOf(ProtocolId.of("a"), ProtocolId.of("b")), supported1)

        val b1 = peerstore.firstSupportedProtocol(peerId, setOf(ProtocolId.of("q"), ProtocolId.of("w"), ProtocolId.of("a"), ProtocolId.of("y"), ProtocolId.of("b"))).expectNoErrors()
        assertEquals(ProtocolId.of("a"), b1)

        val b2 = peerstore.firstSupportedProtocol(peerId, setOf(ProtocolId.of("q"), ProtocolId.of("x"), ProtocolId.of("z"))).expectNoErrors()
        assertEquals(ProtocolId.Null, b2)

        val b3 = peerstore.firstSupportedProtocol(peerId, setOf(ProtocolId.of("a"))).expectNoErrors()
        assertEquals(ProtocolId.of("a"), b3)

        val protos2 = setOf(ProtocolId.of("other"), ProtocolId.of("yet another"), ProtocolId.of("one more"))
        peerstore.setProtocols(peerId, protos2).expectNoErrors()

        val supported2 = peerstore.supportsProtocols(peerId, setOf(ProtocolId.of("q"), ProtocolId.of("w"), ProtocolId.of("a"), ProtocolId.of("y"), ProtocolId.of("b"))).expectNoErrors()
        assertEquals(setOf<ProtocolId>(), supported2)

        val supported3 = peerstore.getProtocols(peerId).expectNoErrors()
        assertEquals(protos2, supported3)

        peerstore.removeProtocols(peerId, setOf(ProtocolId.of("yet another")))

        val supported4 = peerstore.getProtocols(peerId).expectNoErrors()
        assertEquals(setOf(ProtocolId.of("other"), ProtocolId.of("one more")), supported4)

        peerstore.close()
        memoryDatastore.close()
    }

    @Test
    fun testProtocolStoreRemovePeer() = runTest {
        val memoryDatastore = MapDatastore(this)
        val peerstore = Peerstore.create(this, memoryDatastore, PeerstoreConfig()).expectNoErrors()
        val peerId = randomPeerId()
        val protos = setOf(ProtocolId.of("a"), ProtocolId.of("b"))
        peerstore.setProtocols(peerId, protos).expectNoErrors()
        val actualProtos1 = peerstore.getProtocols(peerId).expectNoErrors()
        assertEquals(protos, actualProtos1)

        peerstore.removePeer(peerId)

        val actualProtos2 = peerstore.getProtocols(peerId).expectNoErrors()
        assertEquals(setOf<ProtocolId>(), actualProtos2)

        peerstore.close()
        memoryDatastore.close()
    }

    @Test
    fun testBasic() = runTest {
        val memoryDatastore = MapDatastore(this)
        val peerstore = Peerstore.create(this, memoryDatastore, PeerstoreConfig()).expectNoErrors()
        val multiaddresses = generateRandomInetMultiaddress(10)
        val pids = mutableListOf<PeerId>()
        for (address in multiaddresses) {
            val peerId = randomPeerId()
            peerstore.addAddress(peerId, address, PermanentAddrTTL)
            pids.add(peerId)
        }
        val peers = peerstore.peers()
        assertEquals(10, peers.size)

        val pinfo = peerstore.peerInfo(pids[0])
        assertEquals(multiaddresses[0], pinfo.addresses[0])

        peerstore.close()
        memoryDatastore.close()
    }

    @Test
    fun testMetadata() = runTest {
        val memoryDatastore = MapDatastore(this)
        val peerstore = Peerstore.create(this, memoryDatastore, PeerstoreConfig()).expectNoErrors()
        val pids = listOf(randomPeerId(), randomPeerId(), randomPeerId())
        for (p in pids) {
            peerstore.put(p, "AgentVersion", "string").expectNoErrors()
            peerstore.put(p, "bar", 1).expectNoErrors()
        }
        for (p in pids) {
            val s = peerstore.get<String>(p, "AgentVersion").expectNoErrors()
            assertEquals("string", s)
            val i = peerstore.get<Int>(p, "bar").expectNoErrors()
            assertEquals(1, i)
        }

        peerstore.close()
        memoryDatastore.close()
    }

    @Test
    fun testMetadataRemovePeer() = runTest {
        val memoryDatastore = MapDatastore(this)
        val peerstore = Peerstore.create(this, memoryDatastore, PeerstoreConfig()).expectNoErrors()
        val peerId1 = randomPeerId()
        val peerId2 = randomPeerId()
        peerstore.put(peerId1, "AgentVersion", "string").expectNoErrors()
        peerstore.put(peerId1, "bar", 1).expectNoErrors()
        peerstore.put(peerId2, "AgentVersion", "string").expectNoErrors()

        peerstore.removePeer(peerId1)

        coAssertErrorResult("datastore: key not found") { peerstore.get<String>(peerId1, "AgentVersion") }
        coAssertErrorResult("datastore: key not found") { peerstore.get<Int>(peerId1, "bar") }

        val s = peerstore.get<String>(peerId2, "AgentVersion").expectNoErrors()
        assertEquals("string", s)

        peerstore.close()
        memoryDatastore.close()
    }

    @Test
    fun testProtocolStoreLimits() = runTest {
        val memoryDatastore = MapDatastore(this)
        val peerstoreConfig = PeerstoreConfig()
        peerstoreConfig.maxProtocols = 10
        val peerstore = Peerstore.create(this, memoryDatastore, peerstoreConfig).expectNoErrors()
        val peerId = randomPeerId()
        val protocols = (0 until 10).map { ProtocolId.of("Protocol$it") }.toMutableSet()

        peerstore.setProtocols(peerId, protocols).expectNoErrors()
        protocols.add(ProtocolId.of("proto"))
        coAssertErrorResult("too many protocols") { peerstore.setProtocols(peerId, protocols) }

        val list = protocols.toList()
        val p1 = list.subList(0, 5).toSet()
        val p2 = list.subList(5, 10).toSet()
        peerstore.setProtocols(peerId, p1).expectNoErrors()
        peerstore.addProtocols(peerId, p2).expectNoErrors()
        coAssertErrorResult("too many protocols") { peerstore.addProtocols(peerId, setOf(ProtocolId.of("proto"))) }

        peerstore.close()
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
}
