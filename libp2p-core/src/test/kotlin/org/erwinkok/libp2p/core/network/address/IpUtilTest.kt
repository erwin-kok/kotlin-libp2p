// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.network.address

import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.address.AddressUtilTest.Companion.assertInetMultiaddressEqual
import org.erwinkok.result.expectNoErrors
import org.erwinkok.util.Tuple2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream

internal class IpUtilTest {
    @TestFactory
    fun testThinWaist(): Stream<DynamicTest> {
        return listOf(
            Tuple2("/ip4/127.0.0.1/udp/1234", true),
            Tuple2("/ip4/127.0.0.1/tcp/1234", true),
            Tuple2("/ip6/::1/tcp/80", true),
            Tuple2("/ip6/::1/udp/80", true),
            Tuple2("/ip6/::1", true),
            Tuple2("/ip6zone/hello/ip6/fe80::1/tcp/80", true),
            Tuple2("/ip6zone/hello/ip6/fe80::1", true),
            Tuple2("/tcp/1234", false),
            Tuple2("/ip6zone/hello", false),
        ).map { (address, value) ->
            DynamicTest.dynamicTest("Test: $address") {
                val m = InetMultiaddress.fromString(address).expectNoErrors()
                assertEquals(value, IpUtil.isThinWaist(m))
            }
        }.stream()
    }

    @Test
    fun testIsPublicAddr() {
        val a1 = InetMultiaddress.fromString("/ip4/192.168.1.1/tcp/80").expectNoErrors()
        assertFalse(IpUtil.isPublicAddress(a1))
        assertTrue(IpUtil.isPrivateAddress(a1))
        val a2 = InetMultiaddress.fromString("/ip4/1.1.1.1/tcp/80").expectNoErrors()
        assertTrue(IpUtil.isPublicAddress(a2))
        assertFalse(IpUtil.isPrivateAddress(a2))
    }

    @Test
    fun testContains() {
        val a1 = InetMultiaddress.fromString("/ip4/127.0.0.1/tcp/1234").expectNoErrors()
        val a2 = InetMultiaddress.fromString("/ip4/1.1.1.1/tcp/999").expectNoErrors()
        val a3 = InetMultiaddress.fromString("/ip4/1.2.3.4/udp/443/quic").expectNoErrors()
        val a4 = InetMultiaddress.fromString("/ip4/1.2.3.4/udp/443/quic-v1").expectNoErrors()
        val multiaddresses = listOf(a1, a2, a3, a4)
        assertTrue(IpUtil.contains(a1, multiaddresses))
        assertTrue(IpUtil.contains(a2, multiaddresses))
        assertTrue(IpUtil.contains(a3, multiaddresses))
        assertTrue(IpUtil.contains(a4, multiaddresses))
        assertFalse(IpUtil.contains(InetMultiaddress.fromString("/ip4/4.3.2.1/udp/1234/utp").expectNoErrors(), multiaddresses))
        assertFalse(IpUtil.contains(a1, listOf()))
    }

    @TestFactory
    fun testUniqueAddresses(): Stream<DynamicTest> {
        val tcpAddr = InetMultiaddress.fromString("/ip4/127.0.0.1/tcp/1234").expectNoErrors()
        val quicAddr = InetMultiaddress.fromString("/ip4/127.0.0.1/udp/1234/quic-v1").expectNoErrors()
        val wsAddr = InetMultiaddress.fromString("/ip4/127.0.0.1/tcp/1234/ws").expectNoErrors()

        return listOf(
            Tuple2(listOf(tcpAddr), listOf(tcpAddr)),
            Tuple2(listOf(tcpAddr, tcpAddr, tcpAddr), listOf(tcpAddr)),
            Tuple2(listOf(tcpAddr, quicAddr, tcpAddr), listOf(tcpAddr, quicAddr)),
            Tuple2(listOf(tcpAddr, quicAddr, wsAddr), listOf(tcpAddr, quicAddr, wsAddr))
        ).map { (inp, out) ->
            DynamicTest.dynamicTest("Test: $inp") {
                val dedup = IpUtil.unique(inp)
                assertInetMultiaddressEqual(out, dedup)
            }
        }.stream()
    }
}
