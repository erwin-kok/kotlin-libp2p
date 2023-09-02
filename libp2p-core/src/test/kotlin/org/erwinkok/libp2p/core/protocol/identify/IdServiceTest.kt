// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.protocol.identify

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.erwinkok.libp2p.core.base.EpochTimeProvider
import org.erwinkok.libp2p.core.event.EvtPeerIdentificationCompleted
import org.erwinkok.libp2p.core.host.BlankHost
import org.erwinkok.libp2p.core.host.Host
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.host.RemoteIdentity
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.Network
import org.erwinkok.libp2p.core.network.NetworkConnection
import org.erwinkok.libp2p.core.network.Subscriber
import org.erwinkok.libp2p.core.network.address.AddressUtilTest.Companion.assertInetMultiaddressEqual
import org.erwinkok.libp2p.core.network.swarm.SwarmTestBuilder
import org.erwinkok.libp2p.core.peerstore.Peerstore.Companion.RecentlyConnectedAddrTTL
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

class IdServiceTest {
    @Test
    fun idService() = runTest {
        withContext(Dispatchers.Default) {
            val swarm1 = SwarmTestBuilder.create(this)
            val swarm2 = SwarmTestBuilder.create(this)
            val h1 = BlankHost.create(this, swarm1).expectNoErrors()
            val h2 = BlankHost.create(this, swarm2).expectNoErrors()
            val ids1 = IdService(this, h1)
            val ids2 = IdService(this, h2)
            ids1.start()
            ids2.start()
            h1.eventBus.subscribe<EvtPeerIdentificationCompleted>(this, this, Dispatchers.Unconfined) {
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

            val sentDisconnect1 = waitForDisconnectNotification(this, swarm1)
            val sentDisconnect2 = waitForDisconnectNotification(this, swarm2)

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

    private fun waitForDisconnectNotification(scope: CoroutineScope, network: Network): Job {
        return scope.launch {
            var disconnected = false
            network.subscribe(object : Subscriber {
                override fun disconnected(network: Network, connection: NetworkConnection) {
                    disconnected = true
                }
            })
            while (!disconnected) {
                yield()
            }
        }
    }
}