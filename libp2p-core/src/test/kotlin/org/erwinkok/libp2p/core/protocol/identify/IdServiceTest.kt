// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.protocol.identify

import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.erwinkok.libp2p.core.base.EpochTimeProvider
import org.erwinkok.libp2p.core.event.EvtLocalAddressesUpdated
import org.erwinkok.libp2p.core.event.EvtPeerIdentificationCompleted
import org.erwinkok.libp2p.core.event.EvtPeerIdentificationFailed
import org.erwinkok.libp2p.core.event.EvtPeerProtocolsUpdated
import org.erwinkok.libp2p.core.host.BlankHost
import org.erwinkok.libp2p.core.host.Host
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.host.RemoteIdentity
import org.erwinkok.libp2p.core.identify.pb.DbIdentify
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.Network
import org.erwinkok.libp2p.core.network.NetworkConnection
import org.erwinkok.libp2p.core.network.Subscriber
import org.erwinkok.libp2p.core.network.address.AddressUtilTest.Companion.assertInetMultiaddressEqual
import org.erwinkok.libp2p.core.network.swarm.SwarmTestBuilder
import org.erwinkok.libp2p.core.network.writeUnsignedVarInt
import org.erwinkok.libp2p.core.peerstore.Peerstore.Companion.PermanentAddrTTL
import org.erwinkok.libp2p.core.peerstore.Peerstore.Companion.RecentlyConnectedAddrTTL
import org.erwinkok.libp2p.core.record.AddressInfo
import org.erwinkok.libp2p.core.record.Envelope
import org.erwinkok.libp2p.core.record.PeerRecord
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class IdServiceTest {
    private val IdentifyId = ProtocolId.of("/ipfs/id/1.0.0")
    private val PushId = ProtocolId.of("/ipfs/id/push/1.0.0")
    private val TestingId = ProtocolId.of("/p2p/_testing")

    @Test
    fun idService() = runTest {
        withContext(Dispatchers.Default) {
            val h1 = BlankHost.create(this, SwarmTestBuilder.create(this)).expectNoErrors()
            val h2 = BlankHost.create(this, SwarmTestBuilder.create(this)).expectNoErrors()
            val ids1 = IdService(this, h1)
            val ids2 = IdService(this, h2)
            ids1.start()
            ids2.start()

            val channel = Channel<Unit>(1)
            h1.eventBus.subscribe<EvtPeerIdentificationCompleted>(this, this, Dispatchers.Unconfined) {
                channel.send(Unit)
            }

            testKnowsAddrs(h1, h2.id, listOf())
            testKnowsAddrs(h2, h1.id, listOf())

            val ma = InetMultiaddress.fromString("/ip4/1.2.3.4/tcp/1234").expectNoErrors()
            h2.peerstore.addAddress(h1.id, ma, RecentlyConnectedAddrTTL)
            val h2pi = h2.peerstore.peerInfo(h2.id)
            h1.connect(h2pi)
            val h1t2c = h1.network.connectionsToPeer(h2.id)
            assertTrue(h1t2c.isNotEmpty())

            ids1.identifyConnection(h1t2c[0])
            testKnowsAddrs(h1, h2.id, h2.addresses())
            testHasAgentVersion(h1, h2.id)
            testHasPublicKey(h2, h1.id, h1.peerstore.remoteIdentity(h1.id))

            val c = h2.network.connectionsToPeer(h1.id)
            assertTrue(c.isNotEmpty())
            ids2.identifyConnection(c[0])

            testKnowsAddrs(h2, h1.id, h1.addresses())
            testHasAgentVersion(h2, h1.id)
            testHasPublicKey(h2, h1.id, h1.peerstore.remoteIdentity(h1.id))

            val sentDisconnect1 = waitForDisconnectNotification(this, h1.network)
            val sentDisconnect2 = waitForDisconnectNotification(this, h2.network)

            h1.network.closePeer(h2.id)
            h2.network.closePeer(h1.id)

            assertTrue(h1.network.connectionsToPeer(h2.id).isEmpty())
            assertTrue(h2.network.connectionsToPeer(h1.id).isEmpty())

            testKnowsAddrs(h2, h1.id, h1.addresses())
            testKnowsAddrs(h1, h2.id, h2.addresses())

            sentDisconnect1.join()
            sentDisconnect2.join()

            EpochTimeProvider.test.advanceTime(1.hours)

            testKnowsAddrs(h1, h2.id, listOf())
            testKnowsAddrs(h2, h1.id, listOf())

            ids2.close()
            ids1.close()
            h2.close()
            h1.close()

            channel.receive()
        }
    }

    @Test
    fun protoMatching() {
        val tcp1 = InetMultiaddress.fromString("/ip4/1.2.3.4/tcp/1234").expectNoErrors()
        val tcp2 = InetMultiaddress.fromString("/ip4/1.2.3.4/tcp/2345").expectNoErrors()
        val tcp3 = InetMultiaddress.fromString("/ip4/1.2.3.4/tcp/4567").expectNoErrors()
        val utp = InetMultiaddress.fromString("/ip4/1.2.3.4/udp/1234/utp").expectNoErrors()
        assertTrue(IdService.hasConsistentTransport(tcp1, listOf(tcp2, tcp3, utp)))
        assertFalse(IdService.hasConsistentTransport(utp, listOf(tcp2, tcp3)))
    }

    @Test
    fun identifyPushWhileIdentifyingConnection() = runTest {
        withContext(Dispatchers.Default) {
            val h1 = BlankHost.create(this, SwarmTestBuilder.create(this)).expectNoErrors()
            val h2 = BlankHost.create(this, SwarmTestBuilder.create(this)).expectNoErrors()
            val ids1 = IdService(this, h1)
            val ids2 = IdService(this, h2)
            ids1.start()
            ids2.start()

            val channel = Channel<Unit>(1)
            h1.removeStreamHandler(IdentifyId)
            h1.setStreamHandler(IdentifyId) { stream ->
                channel.receive()
                val protocols = h1.multistreamMuxer.protocols().map { it.id }
                val bytes =
                    DbIdentify
                        .Identify
                        .newBuilder()
                        .addAllProtocols(protocols)
                        .build()
                        .toByteArray()
                stream.output.writeUnsignedVarInt(bytes.size)
                stream.output.writeFully(bytes)
                stream.output.flush()
                stream.close()
            }

            h2.connect(AddressInfo.fromPeerIdAndAddresses(h1.id, h1.addresses())).expectNoErrors()

            val connection = h2.network.connectionsToPeer(h1.id)
            assertTrue(connection.isNotEmpty())

            val job = launch {
                ids2.identifyConnection(connection[0])
            }

            val result = Channel<EvtPeerProtocolsUpdated>(1)
            h1.eventBus.subscribe<EvtPeerProtocolsUpdated>(this, this, Dispatchers.Unconfined) {
                result.send(it)
            }

            val result1 = withTimeoutOrNull(2.seconds) {
                result.receive()
            }
            assertNull(result1)

            h2.setStreamHandler(TestingId) {}

            channel.send(Unit)

            val result2 = withTimeoutOrNull(5.seconds) {
                result.receive()
            }

            assertNotNull(result2)
            assertEquals(h2.id, result2?.peerId)
            assertEquals(3, result2?.added?.size)
            assertEquals(setOf(IdentifyId, TestingId, PushId), result2?.added)
            assertEquals(0, result2?.removed?.size)

            job.join()

            ids2.close()
            ids1.close()
            h2.close()
            h1.close()
        }
    }

    @Test
    fun identifyPushOnAddrChange() = runTest {
        withContext(Dispatchers.Default) {
            val h1 = BlankHost.create(this, SwarmTestBuilder.create(this)).expectNoErrors()
            val h2 = BlankHost.create(this, SwarmTestBuilder.create(this)).expectNoErrors()
            val ids1 = IdService(this, h1)
            val ids2 = IdService(this, h2)
            ids1.start()
            ids2.start()

            testKnowsAddrs(h1, h2.id, listOf())
            testKnowsAddrs(h2, h1.id, listOf())

            h1.connect(h2.peerstore.peerInfo(h2.id)).expectNoErrors()

            val connection = h1.network.connectionsToPeer(h2.id)
            // h1 should immediately see a connection from h2
            assertTrue(connection.isNotEmpty())
            // wait for h2 to Identify itself, so we are sure h2 has seen the connection.
            ids1.identifyConnection(h1.network.connectionsToPeer(h2.id)[0])

            // h2 should now see the connection, and we should wait for h1 to Identify itself to h2.
            assertTrue(h2.network.connectionsToPeer(h1.id).isNotEmpty())
            ids2.identifyConnection(h2.network.connectionsToPeer(h1.id)[0])

            testKnowsAddrs(h1, h2.id, h2.peerstore.addresses(h2.id))
            testKnowsAddrs(h2, h1.id, h1.peerstore.addresses(h1.id))

            // change addr on host 1 and ensure host2 gets a push
            var lad = InetMultiaddress.fromString("/ip4/127.0.0.1/tcp/1234").expectNoErrors()
            h1.network.addListener(lad).expectNoErrors()
            assertTrue(h1.addresses().contains(lad))

            val h2AddrStream = h2.peerstore.addressStream(h1.id)
            emitAddrChangeEvt(h1)
            // Wait for h2 to process the new addr
            waitForAddressInStream(h2AddrStream, lad, 10.seconds, "h2 did not receive address change")

            assertTrue(h2.peerstore.addresses(h1.id).contains(lad))

            // change addr on host2 and ensure host 1 gets a push
            lad = InetMultiaddress.fromString("/ip4/127.0.0.1/tcp/1235").expectNoErrors()
            h2.network.addListener(lad)
            assertTrue(h2.addresses().contains(lad))
            val h1AddrStream = h1.peerstore.addressStream(h2.id)
            emitAddrChangeEvt(h2)

            // Wait for h1 to process the new addr
            waitForAddressInStream(h1AddrStream, lad, 10.seconds, "h1 did not receive address change")

            assertTrue(h1.peerstore.addresses(h2.id).contains(lad))

            // change addr on host2 again
            val lad2 = InetMultiaddress.fromString("/ip4/127.0.0.1/tcp/1236").expectNoErrors()
            h2.network.addListener(lad2)
            assertTrue(h2.addresses().contains(lad2))
            emitAddrChangeEvt(h2)

            // Wait for h1 to process the new addr
            waitForAddressInStream(h1AddrStream, lad2, 10.seconds, "h1 did not receive address change")

            assertTrue(h1.peerstore.addresses(h2.id).contains(lad2))

            ids2.close()
            ids1.close()
            h2.close()
            h1.close()
        }
    }

    @Test
    fun sendPush() = runTest {
        withContext(Dispatchers.Default) {
            val h1 = BlankHost.create(this, SwarmTestBuilder.create(this)).expectNoErrors()
            val h2 = BlankHost.create(this, SwarmTestBuilder.create(this)).expectNoErrors()
            val ids1 = IdService(this, h1)
            val ids2 = IdService(this, h2)
            ids1.start()
            ids2.start()

            h1.connect(AddressInfo.fromPeerIdAndAddresses(h2.id, h2.addresses())).expectNoErrors()

            // wait for them to Identify each other
            ids1.identifyConnection(h1.network.connectionsToPeer(h2.id)[0])
            ids2.identifyConnection(h2.network.connectionsToPeer(h1.id)[0])

            h1.setStreamHandler(ProtocolId.of("rand")) {}

            while (true) {
                val supported = h2.peerstore.supportsProtocols(h1.id, setOf(ProtocolId.of("rand"))).expectNoErrors()
                if (supported.contains(ProtocolId.of("rand"))) {
                    break
                }
                delay(10.milliseconds)
            }

            h1.removeStreamHandler(ProtocolId.of("rand"))

            while (true) {
                val supported = h2.peerstore.supportsProtocols(h1.id, setOf(ProtocolId.of("rand"))).expectNoErrors()
                if (!supported.contains(ProtocolId.of("rand"))) {
                    break
                }
                delay(10.milliseconds)
            }

            ids2.close()
            ids1.close()
            h2.close()
            h1.close()
        }
    }

    @Test
    fun largeIdentifyMessage() = runTest(timeout = 1.minutes) {
        withContext(Dispatchers.Default) {
            val h1 = BlankHost.create(this, SwarmTestBuilder.create(this)).expectNoErrors()
            val h2 = BlankHost.create(this, SwarmTestBuilder.create(this)).expectNoErrors()

            repeat(500) {
                h1.setStreamHandler(ProtocolId.of("rand$it")) {}
                h2.setStreamHandler(ProtocolId.of("rand$it")) {}
            }

            val ids1 = IdService(this, h1)
            val ids2 = IdService(this, h2)
            ids1.start()
            ids2.start()

            val channel = Channel<Unit>(1)
            h1.eventBus.subscribe<EvtPeerIdentificationCompleted>(this, this, Dispatchers.Unconfined) {
                channel.send(Unit)
            }

            testKnowsAddrs(h1, h2.id, listOf())
            testKnowsAddrs(h2, h1.id, listOf())

            val ma = InetMultiaddress.fromString("/ip4/1.2.3.4/tcp/1234").expectNoErrors()
            h2.peerstore.addAddress(h1.id, ma, RecentlyConnectedAddrTTL)
            val h2pi = h2.peerstore.peerInfo(h2.id)
            h1.connect(h2pi)
            val h1t2c = h1.network.connectionsToPeer(h2.id)
            assertTrue(h1t2c.isNotEmpty())

            ids1.identifyConnection(h1t2c[0])
            testKnowsAddrs(h1, h2.id, h2.addresses())
            testHasAgentVersion(h1, h2.id)
            testHasPublicKey(h2, h1.id, h1.peerstore.remoteIdentity(h1.id))

            val c = h2.network.connectionsToPeer(h1.id)
            assertTrue(c.isNotEmpty())
            ids2.identifyConnection(c[0])

            testKnowsAddrs(h2, h1.id, h1.addresses())
            testHasAgentVersion(h2, h1.id)
            testHasPublicKey(h2, h1.id, h1.peerstore.remoteIdentity(h1.id))

            val sentDisconnect1 = waitForDisconnectNotification(this, h1.network)
            val sentDisconnect2 = waitForDisconnectNotification(this, h2.network)

            h1.network.closePeer(h2.id)
            h2.network.closePeer(h1.id)

            assertTrue(h1.network.connectionsToPeer(h2.id).isEmpty())
            assertTrue(h2.network.connectionsToPeer(h1.id).isEmpty())

            testKnowsAddrs(h2, h1.id, h1.addresses())
            testKnowsAddrs(h1, h2.id, h2.addresses())

            sentDisconnect1.join()
            sentDisconnect2.join()

            EpochTimeProvider.test.advanceTime(1.hours)

            testKnowsAddrs(h1, h2.id, listOf())
            testKnowsAddrs(h2, h1.id, listOf())

            ids2.close()
            ids1.close()
            h2.close()
            h1.close()

            channel.receive()
        }
    }

    @Test
    fun largePushMessage() = runTest {
        withContext(Dispatchers.Default) {
            val h1 = BlankHost.create(this, SwarmTestBuilder.create(this)).expectNoErrors()
            val h2 = BlankHost.create(this, SwarmTestBuilder.create(this)).expectNoErrors()

            repeat(500) {
                h1.setStreamHandler(ProtocolId.of("rand$it")) {}
                h2.setStreamHandler(ProtocolId.of("rand$it")) {}
            }

            val ids1 = IdService(this, h1)
            val ids2 = IdService(this, h2)
            ids1.start()
            ids2.start()

            testKnowsAddrs(h1, h2.id, listOf())
            testKnowsAddrs(h2, h1.id, listOf())

            h1.connect(h2.peerstore.peerInfo(h2.id)).expectNoErrors()

            val connection = h1.network.connectionsToPeer(h2.id)
            // h1 should immediately see a connection from h2
            assertTrue(connection.isNotEmpty())
            // wait for h2 to Identify itself, so we are sure h2 has seen the connection.
            ids1.identifyConnection(h1.network.connectionsToPeer(h2.id)[0])

            // h2 should now see the connection, and we should wait for h1 to Identify itself to h2.
            assertTrue(h2.network.connectionsToPeer(h1.id).isNotEmpty())
            ids2.identifyConnection(h2.network.connectionsToPeer(h1.id)[0])

            testKnowsAddrs(h1, h2.id, h2.peerstore.addresses(h2.id))
            testKnowsAddrs(h2, h1.id, h1.peerstore.addresses(h1.id))

            // change addr on host 1 and ensure host2 gets a push
            var lad = InetMultiaddress.fromString("/ip4/127.0.0.1/tcp/1234").expectNoErrors()
            h1.network.addListener(lad).expectNoErrors()
            assertTrue(h1.addresses().contains(lad))

            val h2AddrStream = h2.peerstore.addressStream(h1.id)
            emitAddrChangeEvt(h1)
            // Wait for h2 to process the new addr
            waitForAddressInStream(h2AddrStream, lad, 10.seconds, "h2 did not receive address change")

            assertTrue(h2.peerstore.addresses(h1.id).contains(lad))

            // change addr on host2 and ensure host 1 gets a pus
            lad = InetMultiaddress.fromString("/ip4/127.0.0.1/tcp/1235").expectNoErrors()
            h2.network.addListener(lad)
            assertTrue(h2.addresses().contains(lad))
            val h1AddrStream = h1.peerstore.addressStream(h2.id)
            emitAddrChangeEvt(h2)

            // Wait for h1 to process the new addr
            waitForAddressInStream(h1AddrStream, lad, 10.seconds, "h1 did not receive address change")

            assertTrue(h1.peerstore.addresses(h2.id).contains(lad))

            // change addr on host2 again
            val lad2 = InetMultiaddress.fromString("/ip4/127.0.0.1/tcp/1236").expectNoErrors()
            h2.network.addListener(lad2)
            assertTrue(h2.addresses().contains(lad2))
            emitAddrChangeEvt(h2)

            // Wait for h1 to process the new addr
            waitForAddressInStream(h1AddrStream, lad2, 10.seconds, "h1 did not receive address change")

            assertTrue(h1.peerstore.addresses(h2.id).contains(lad2))

            ids2.close()
            ids1.close()
            h2.close()
            h1.close()
        }
    }

    @Test
    fun identifyResponseReadTimeout() = runTest {
        val oldTimeout = IdService.StreamReadTimeout
        IdService.StreamReadTimeout = 2.seconds
        withContext(Dispatchers.Default) {
            val h1 = BlankHost.create(this, SwarmTestBuilder.create(this)).expectNoErrors()
            val h2 = BlankHost.create(this, SwarmTestBuilder.create(this)).expectNoErrors()
            val ids1 = IdService(this, h1)
            val ids2 = IdService(this, h2)
            ids1.start()
            ids2.start()

            h2.removeStreamHandler(IdentifyId)
            h2.setStreamHandler(IdentifyId) {
                delay(3.seconds)
            }

            val channel = Channel<EvtPeerIdentificationFailed>(1)
            h1.eventBus.subscribe<EvtPeerIdentificationFailed>(this, this, Dispatchers.Unconfined) {
                channel.send(it)
            }

            h1.connect(h2.peerstore.peerInfo(h2.id)).expectNoErrors()
            ids1.identifyConnection(h1.network.connectionsToPeer(h2.id)[0])

            val result = channel.receive()
            assertNotNull(result)
            assertEquals(h2.id, result.peerId)
            assertEquals("Timeout occurred while reading identify message", result.reason.message)

            ids2.close()
            ids1.close()
            h2.close()
            h1.close()
        }
        IdService.StreamReadTimeout = oldTimeout
    }

    private suspend fun testKnowsAddrs(h: Host, p: PeerId, expected: List<InetMultiaddress>) {
        assertInetMultiaddressEqual(expected, h.peerstore.addresses(p))
    }

    private suspend fun testHasAgentVersion(h: Host, p: PeerId) {
        assertEquals("erwinkok.org/libp2p", h.peerstore.get<String>(p, "AgentVersion").expectNoErrors())
    }

    private suspend fun testHasPublicKey(h: Host, p: PeerId, remoteIdentity: RemoteIdentity?) {
        val k = h.peerstore.remoteIdentity(p)
        assertNotNull(remoteIdentity?.publicKey)
        assertNotNull(k?.publicKey)
        assertEquals(remoteIdentity?.publicKey, k?.publicKey)
        val p2 = PeerId.fromPublicKey(k?.publicKey!!).expectNoErrors()
        assertEquals(p, p2)
    }

    private suspend fun emitAddrChangeEvt(h: Host) {
        val key = h.peerstore.localIdentity(h.id)
        assertNotNull(key)
        val record = PeerRecord.fromPeerIdAndAddresses(h.id, h.addresses()).expectNoErrors()
        val signed = Envelope.seal(record, key!!.privateKey).expectNoErrors()
        h.peerstore.consumePeerRecord(signed, PermanentAddrTTL).expectNoErrors()
        h.eventBus.publish(EvtLocalAddressesUpdated(false))
    }

    private fun waitForDisconnectNotification(scope: CoroutineScope, network: Network): Job {
        return scope.launch {
            var disconnected = false
            network.subscribe(
                object : Subscriber {
                    override fun disconnected(network: Network, connection: NetworkConnection) {
                        disconnected = true
                    }
                },
            )
            while (!disconnected) {
                yield()
            }
        }
    }

    private suspend fun waitForAddressInStream(stream: SharedFlow<InetMultiaddress>, address: InetMultiaddress, timeout: Duration, failMessage: String) {
        val result = withTimeoutOrNull(timeout) {
            stream.firstOrNull { it == address }
        }
        assertNotNull(result, failMessage)
    }
}
