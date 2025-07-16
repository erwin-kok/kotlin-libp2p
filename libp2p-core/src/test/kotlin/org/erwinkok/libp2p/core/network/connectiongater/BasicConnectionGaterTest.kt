// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network.connectiongater

import inet.ipaddr.IPAddressString
import kotlinx.coroutines.test.runTest
import org.erwinkok.libp2p.core.datastore.MapDatastore
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.network.ConnectionMultiaddress
import org.erwinkok.libp2p.core.network.Direction
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BasicConnectionGaterTest {
    private val nullAddress = InetMultiaddress.fromString("").expectNoErrors()

    data class MockConnectionMultiaddress(override val localAddress: InetMultiaddress, override val remoteAddress: InetMultiaddress) : ConnectionMultiaddress

    @Test
    fun testConnectionGater() = runTest {
        val ds = MapDatastore(this)

        val peerA = LocalIdentity.random().expectNoErrors().peerId
        val peerB = LocalIdentity.random().expectNoErrors().peerId

        val ip1 = IPAddressString("1.2.3.4").address
        val ipNet1 = IPAddressString("1.2.3.0/24").address

        var cg = BasicConnectionGater.create(this, ds)

        // test peer blocking
        assertTrue(cg.interceptPeerDial(peerA))
        assertTrue(cg.interceptPeerDial(peerB))
        assertTrue(cg.interceptSecured(Direction.DirInbound, peerA, MockConnectionMultiaddress(nullAddress, nullAddress)))
        assertTrue(cg.interceptSecured(Direction.DirInbound, peerB, MockConnectionMultiaddress(nullAddress, nullAddress)))

        cg.blockPeer(peerA)

        assertFalse(cg.interceptPeerDial(peerA))
        assertTrue(cg.interceptPeerDial(peerB))
        assertFalse(cg.interceptSecured(Direction.DirInbound, peerA, MockConnectionMultiaddress(nullAddress, nullAddress)))
        assertTrue(cg.interceptSecured(Direction.DirInbound, peerB, MockConnectionMultiaddress(nullAddress, nullAddress)))

        // test addr and subnet blocking
        assertTrue(cg.interceptAddressDial(peerB, InetMultiaddress.fromString("/ip4/1.2.3.4/tcp/1234").expectNoErrors()))
        assertTrue(cg.interceptAccept(MockConnectionMultiaddress(nullAddress, InetMultiaddress.fromString("/ip4/1.2.3.4/tcp/1234").expectNoErrors())))
        assertTrue(cg.interceptAddressDial(peerB, InetMultiaddress.fromString("/ip4/1.2.3.5/tcp/1234").expectNoErrors()))
        assertTrue(cg.interceptAccept(MockConnectionMultiaddress(nullAddress, InetMultiaddress.fromString("/ip4/1.2.3.5/tcp/1234").expectNoErrors())))
        assertTrue(cg.interceptAddressDial(peerB, InetMultiaddress.fromString("/ip4/2.3.4.5/tcp/1234").expectNoErrors()))
        assertTrue(cg.interceptAccept(MockConnectionMultiaddress(nullAddress, InetMultiaddress.fromString("/ip4/2.3.4.5/tcp/1234").expectNoErrors())))

        cg.blockAddress(ip1)

        assertFalse(cg.interceptAddressDial(peerB, InetMultiaddress.fromString("/ip4/1.2.3.4/tcp/1234").expectNoErrors()))
        assertFalse(cg.interceptAccept(MockConnectionMultiaddress(nullAddress, InetMultiaddress.fromString("/ip4/1.2.3.4/tcp/1234").expectNoErrors())))
        assertTrue(cg.interceptAddressDial(peerB, InetMultiaddress.fromString("/ip4/1.2.3.5/tcp/1234").expectNoErrors()))
        assertTrue(cg.interceptAccept(MockConnectionMultiaddress(nullAddress, InetMultiaddress.fromString("/ip4/1.2.3.5/tcp/1234").expectNoErrors())))
        assertTrue(cg.interceptAddressDial(peerB, InetMultiaddress.fromString("/ip4/2.3.4.5/tcp/1234").expectNoErrors()))
        assertTrue(cg.interceptAccept(MockConnectionMultiaddress(nullAddress, InetMultiaddress.fromString("/ip4/2.3.4.5/tcp/1234").expectNoErrors())))

        cg.blockSubnet(ipNet1)

        assertFalse(cg.interceptAddressDial(peerB, InetMultiaddress.fromString("/ip4/1.2.3.5/tcp/1234").expectNoErrors()))
        assertFalse(cg.interceptAccept(MockConnectionMultiaddress(nullAddress, InetMultiaddress.fromString("/ip4/1.2.3.5/tcp/1234").expectNoErrors())))
        assertTrue(cg.interceptAddressDial(peerB, InetMultiaddress.fromString("/ip4/2.3.4.5/tcp/1234").expectNoErrors()))
        assertTrue(cg.interceptAccept(MockConnectionMultiaddress(nullAddress, InetMultiaddress.fromString("/ip4/2.3.4.5/tcp/1234").expectNoErrors())))

        // make a new gater reusing the datastore to test persistence
        cg.close()

        cg = BasicConnectionGater.create(this, ds)

        assertEquals(1, cg.listBlockedPeers().size)
        assertEquals(1, cg.listBlockedAddresses().size)
        assertEquals(1, cg.listBlockedSubnets().size)

        assertFalse(cg.interceptPeerDial(peerA))
        assertTrue(cg.interceptPeerDial(peerB))
        assertFalse(cg.interceptSecured(Direction.DirInbound, peerA, MockConnectionMultiaddress(nullAddress, nullAddress)))
        assertTrue(cg.interceptSecured(Direction.DirInbound, peerB, MockConnectionMultiaddress(nullAddress, nullAddress)))
        assertFalse(cg.interceptAccept(MockConnectionMultiaddress(nullAddress, InetMultiaddress.fromString("/ip4/1.2.3.4/tcp/1234").expectNoErrors())))
        assertFalse(cg.interceptAddressDial(peerB, InetMultiaddress.fromString("/ip4/1.2.3.5/tcp/1234").expectNoErrors()))
        assertFalse(cg.interceptAccept(MockConnectionMultiaddress(nullAddress, InetMultiaddress.fromString("/ip4/1.2.3.5/tcp/1234").expectNoErrors())))
        assertTrue(cg.interceptAddressDial(peerB, InetMultiaddress.fromString("/ip4/2.3.4.5/tcp/1234").expectNoErrors()))
        assertTrue(cg.interceptAccept(MockConnectionMultiaddress(nullAddress, InetMultiaddress.fromString("/ip4/2.3.4.5/tcp/1234").expectNoErrors())))

        // undo the blocks to ensure that we can unblock stuff
        cg.unblockPeer(peerA)
        cg.unblockAddress(ip1)
        cg.unblockSubnet(ipNet1)

        assertTrue(cg.interceptPeerDial(peerA))
        assertTrue(cg.interceptPeerDial(peerB))
        assertTrue(cg.interceptSecured(Direction.DirInbound, peerA, MockConnectionMultiaddress(nullAddress, nullAddress)))
        assertTrue(cg.interceptSecured(Direction.DirInbound, peerB, MockConnectionMultiaddress(nullAddress, nullAddress)))
        assertTrue(cg.interceptAddressDial(peerB, InetMultiaddress.fromString("/ip4/1.2.3.4/tcp/1234").expectNoErrors()))
        assertTrue(cg.interceptAccept(MockConnectionMultiaddress(nullAddress, InetMultiaddress.fromString("/ip4/1.2.3.4/tcp/1234").expectNoErrors())))
        assertTrue(cg.interceptAddressDial(peerB, InetMultiaddress.fromString("/ip4/1.2.3.5/tcp/1234").expectNoErrors()))
        assertTrue(cg.interceptAccept(MockConnectionMultiaddress(nullAddress, InetMultiaddress.fromString("/ip4/1.2.3.5/tcp/1234").expectNoErrors())))
        assertTrue(cg.interceptAddressDial(peerB, InetMultiaddress.fromString("/ip4/2.3.4.5/tcp/1234").expectNoErrors()))
        assertTrue(cg.interceptAccept(MockConnectionMultiaddress(nullAddress, InetMultiaddress.fromString("/ip4/2.3.4.5/tcp/1234").expectNoErrors())))

        // make a new gater reusing the datastore to test persistence of unblocks
        cg.close()

        cg = BasicConnectionGater.create(this, ds)

        assertTrue(cg.interceptPeerDial(peerA))
        assertTrue(cg.interceptPeerDial(peerB))
        assertTrue(cg.interceptSecured(Direction.DirInbound, peerA, MockConnectionMultiaddress(nullAddress, nullAddress)))
        assertTrue(cg.interceptSecured(Direction.DirInbound, peerB, MockConnectionMultiaddress(nullAddress, nullAddress)))
        assertTrue(cg.interceptAddressDial(peerB, InetMultiaddress.fromString("/ip4/1.2.3.4/tcp/1234").expectNoErrors()))
        assertTrue(cg.interceptAccept(MockConnectionMultiaddress(nullAddress, InetMultiaddress.fromString("/ip4/1.2.3.4/tcp/1234").expectNoErrors())))
        assertTrue(cg.interceptAddressDial(peerB, InetMultiaddress.fromString("/ip4/1.2.3.5/tcp/1234").expectNoErrors()))
        assertTrue(cg.interceptAccept(MockConnectionMultiaddress(nullAddress, InetMultiaddress.fromString("/ip4/1.2.3.5/tcp/1234").expectNoErrors())))
        assertTrue(cg.interceptAddressDial(peerB, InetMultiaddress.fromString("/ip4/2.3.4.5/tcp/1234").expectNoErrors()))
        assertTrue(cg.interceptAccept(MockConnectionMultiaddress(nullAddress, InetMultiaddress.fromString("/ip4/2.3.4.5/tcp/1234").expectNoErrors())))

        cg.close()
        ds.close()
    }
}
