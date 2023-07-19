// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.record

import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.result.assertErrorResult
import org.erwinkok.result.expectNoErrors
import org.erwinkok.result.flatMap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class AddressInfoTest {
    private val testId = PeerId.fromString("QmS3zcG7LhYZYSJMhyRZvTddvbNUqtt8BJpaSs6mi1K5Va").expectNoErrors()
    private val maddrPeer = InetMultiaddress.fromString("/p2p/$testId").expectNoErrors()
    private val maddrTpt = InetMultiaddress.fromString("/ip4/127.0.0.1/tcp/1234").expectNoErrors()
    private val maddrFull = InetMultiaddress.fromString("$maddrTpt/p2p/$testId").expectNoErrors()

    @Test
    fun fromMultiaddress() {
        val addressInfo = InetMultiaddress.fromString("/ip4/127.0.0.1/tcp/1234/p2p/QmS3zcG7LhYZYSJMhyRZvTddvbNUqtt8BJpaSs6mi1K5Va").flatMap { AddressInfo.fromP2pAddress(it) }.expectNoErrors()
        assertEquals(1, addressInfo.addresses.size)
        assertEquals(InetMultiaddress.fromString("/ip4/127.0.0.1/tcp/1234").expectNoErrors(), addressInfo.addresses[0])
        assertEquals("QmS3zcG7LhYZYSJMhyRZvTddvbNUqtt8BJpaSs6mi1K5Va", addressInfo.peerId.toString())
    }

    @Test
    fun fromMultiaddressNoTransport() {
        val fromString = InetMultiaddress.fromString("/p2p/QmS3zcG7LhYZYSJMhyRZvTddvbNUqtt8BJpaSs6mi1K5Va")
        val addressInfo = fromString.flatMap { AddressInfo.fromP2pAddress(it) }.expectNoErrors()
        assertEquals(0, addressInfo.addresses.size)
        assertEquals("QmS3zcG7LhYZYSJMhyRZvTddvbNUqtt8BJpaSs6mi1K5Va", addressInfo.peerId.toString())
    }

    @Test
    fun fromMultiaddressNoMultihash() {
        assertErrorResult("/ip4/127.0.0.1/tcp/1234 does not contain a multihash") { InetMultiaddress.fromString("/ip4/127.0.0.1/tcp/1234").flatMap { AddressInfo.fromP2pAddress(it) } }
    }

    @Test
    fun fromMultiple() {
        val m1 = InetMultiaddress.fromString("/ip4/128.199.219.111/tcp/4001/ipfs/QmSoLV4Bbm51jM9C4gDYZQ9Cy3U6aXMJDAbzgu2fzaDs64").expectNoErrors()
        val m2 = InetMultiaddress.fromString("/ip4/104.236.76.40/tcp/4001/ipfs/QmSoLV4Bbm51jM9C4gDYZQ9Cy3U6aXMJDAbzgu2fzaDs64").expectNoErrors()
        val m3 = InetMultiaddress.fromString("/ipfs/QmSoLer265NRgSp2LA3dPaeykiS1J6DifTC88f5uVQKNAd").expectNoErrors()
        val m4 = InetMultiaddress.fromString("/ip4/178.62.158.247/tcp/4001/ipfs/QmSoLer265NRgSp2LA3dPaeykiS1J6DifTC88f5uVQKNAd").expectNoErrors()
        val m5 = InetMultiaddress.fromString("/ipfs/QmSoLPppuBtQSGwKDZT2M73ULpjvfd3aZ6ha4oFGL1KrGM").expectNoErrors()
        val addressInfos = AddressInfo.fromP2pAddresses(m1, m2, m3, m4, m5).expectNoErrors()
        val expected = listOf(
            addressInfo("QmSoLV4Bbm51jM9C4gDYZQ9Cy3U6aXMJDAbzgu2fzaDs64", "/ip4/128.199.219.111/tcp/4001", "/ip4/104.236.76.40/tcp/4001"),
            addressInfo("QmSoLer265NRgSp2LA3dPaeykiS1J6DifTC88f5uVQKNAd", "/ip4/178.62.158.247/tcp/4001"),
            addressInfo("QmSoLPppuBtQSGwKDZT2M73ULpjvfd3aZ6ha4oFGL1KrGM"),
        )
        assertTrue(expected.size == addressInfos.size && expected.containsAll(addressInfos) && addressInfos.containsAll(expected))
    }

    @Test
    fun testSplitAddr() {
        assertEquals(maddrTpt, AddressInfo.transport(maddrFull))
        assertEquals(testId, maddrFull.peerId.expectNoErrors())
        assertNull(AddressInfo.transport(maddrPeer))
        assertEquals(testId, maddrPeer.peerId.expectNoErrors())
    }

    @Test
    fun peerIdWithIpfsMultiaddress() {
        val multiaddress = InetMultiaddress.fromString("/ip4/127.0.0.1/ipfs/QmcgpsyWgH8Y8ajJz1Cu72KnS5uo2Aa2LpzU7kinSupNKC").expectNoErrors()
        val transport = AddressInfo.transport(multiaddress)
        val peerId = multiaddress.peerId.expectNoErrors()
        assertEquals("/ip4/127.0.0.1", transport.toString(), "Transport's do not match")
        assertEquals("QmcgpsyWgH8Y8ajJz1Cu72KnS5uo2Aa2LpzU7kinSupNKC", peerId.toString(), "Multihash's do not match")
    }

    @Test
    fun peerIdWithP2pMultiaddress() {
        val multiaddress = InetMultiaddress.fromString("/ip4/127.0.0.1/p2p/QmcgpsyWgH8Y8ajJz1Cu72KnS5uo2Aa2LpzU7kinSupNKC").expectNoErrors()
        val transport = AddressInfo.transport(multiaddress)
        val peerId = multiaddress.peerId.expectNoErrors()
        assertEquals("/ip4/127.0.0.1", transport.toString(), "Transport's do not match")
        assertEquals("QmcgpsyWgH8Y8ajJz1Cu72KnS5uo2Aa2LpzU7kinSupNKC", peerId.toString(), "Multihash's do not match")
    }

    @Test
    fun noTransportMultiaddress() {
        val multiaddress = InetMultiaddress.fromString("/p2p/QmS3zcG7LhYZYSJMhyRZvTddvbNUqtt8BJpaSs6mi1K5Va").expectNoErrors()
        val transport = AddressInfo.transport(multiaddress)
        val peerId = multiaddress.peerId.expectNoErrors()
        assertNull(transport, "Expected no transport")
        assertEquals("QmS3zcG7LhYZYSJMhyRZvTddvbNUqtt8BJpaSs6mi1K5Va", peerId.toString(), "Multihash's do not match")
    }

    @Test
    fun noMultihashMultiaddress() {
        val multiaddress = InetMultiaddress.fromString("/ip4/184.12.32.8/tcp/1234").expectNoErrors()
        assertErrorResult("/ip4/184.12.32.8/tcp/1234 does not contain a multihash") { multiaddress.peerId }
    }

    @Test
    fun testAddressInfoFromP2pAddr() {
        var ai = AddressInfo.fromP2pAddresses(maddrFull).expectNoErrors()
        assertEquals(1, ai.size)
        var addrInfo = ai[0]
        assertEquals(maddrTpt, addrInfo.addresses[0])
        assertEquals(testId, addrInfo.peerId)
        ai = AddressInfo.fromP2pAddresses(listOf(maddrPeer)).expectNoErrors()
        assertEquals(1, ai.size)
        addrInfo = ai[0]
        assertEquals(0, addrInfo.addresses.size)
        assertEquals(testId, addrInfo.peerId)
    }

    @Test
    fun testAddressInfosFromP2pAddrs() {
        assertEquals(0, AddressInfo.fromP2pAddresses().expectNoErrors().size)
        val addrs = listOf(
            InetMultiaddress.fromString("/ip4/128.199.219.111/tcp/4001/ipfs/QmSoLV4Bbm51jM9C4gDYZQ9Cy3U6aXMJDAbzgu2fzaDs64").expectNoErrors(),
            InetMultiaddress.fromString("/ip4/104.236.76.40/tcp/4001/ipfs/QmSoLV4Bbm51jM9C4gDYZQ9Cy3U6aXMJDAbzgu2fzaDs64").expectNoErrors(),
            InetMultiaddress.fromString("/ipfs/QmSoLer265NRgSp2LA3dPaeykiS1J6DifTC88f5uVQKNAd").expectNoErrors(),
            InetMultiaddress.fromString("/ip4/178.62.158.247/tcp/4001/ipfs/QmSoLer265NRgSp2LA3dPaeykiS1J6DifTC88f5uVQKNAd").expectNoErrors(),
            InetMultiaddress.fromString("/ipfs/QmSoLPppuBtQSGwKDZT2M73ULpjvfd3aZ6ha4oFGL1KrGM").expectNoErrors(),
        )
        val expected = mutableMapOf(
            "QmSoLV4Bbm51jM9C4gDYZQ9Cy3U6aXMJDAbzgu2fzaDs64" to
                mutableListOf(
                    InetMultiaddress.fromString("/ip4/128.199.219.111/tcp/4001").expectNoErrors(),
                    InetMultiaddress.fromString("/ip4/104.236.76.40/tcp/4001").expectNoErrors(),
                ),
            "QmSoLer265NRgSp2LA3dPaeykiS1J6DifTC88f5uVQKNAd" to
                mutableListOf(
                    InetMultiaddress.fromString("/ip4/178.62.158.247/tcp/4001").expectNoErrors(),
                ),
            "QmSoLPppuBtQSGwKDZT2M73ULpjvfd3aZ6ha4oFGL1KrGM" to
                mutableListOf(),
        )
        val infos = AddressInfo.fromP2pAddresses(addrs).expectNoErrors()
        for (info in infos) {
            val peerId = info.peerId.toString()
            assertTrue(expected.containsKey(peerId))
            val exaddrs = expected[peerId]!!
            assertEquals(info.addresses.size, exaddrs.size)
            for (i in info.addresses.indices) {
                assertEquals(info.addresses[i], exaddrs[i])
            }
        }
    }

    private fun addressInfo(id: String, vararg ma: String): AddressInfo {
        val peerId = PeerId.fromString(id).expectNoErrors()
        return AddressInfo.fromPeerIdAndAddresses(peerId, ma.map { address -> InetMultiaddress.fromString(address).expectNoErrors() })
    }
}
