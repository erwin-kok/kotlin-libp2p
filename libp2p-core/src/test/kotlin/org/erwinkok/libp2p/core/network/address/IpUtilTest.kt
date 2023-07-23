// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.network.address

import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.address.IpUtil.isPrivateAddress
import org.erwinkok.libp2p.core.network.address.IpUtil.isPublicAddress
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
        assertFalse(isPublicAddress(a1))
        assertTrue(isPrivateAddress(a1))
        val a2 = InetMultiaddress.fromString("/ip4/1.1.1.1/tcp/80").expectNoErrors()
        assertTrue(isPublicAddress(a2))
        assertFalse(isPrivateAddress(a2))
    }
}
