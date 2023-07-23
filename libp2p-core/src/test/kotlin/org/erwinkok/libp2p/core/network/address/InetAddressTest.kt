// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.network.address

import inet.ipaddr.HostName
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.NetworkProtocol
import org.erwinkok.result.assertErrorResult
import org.erwinkok.result.expectNoErrors
import org.erwinkok.util.Tuple2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream

internal class InetAddressTest {
    @Test
    fun fromIp4() {
        val multiaddress = InetMultiaddress.fromHostNameAndProtocol(HostName("10.20.30.40"), NetworkProtocol.UNKNOWN).expectNoErrors()
        assertEquals("/ip4/10.20.30.40", multiaddress.toString())
    }

    @Test
    fun fromIp6() {
        val multiaddress = InetMultiaddress.fromHostNameAndProtocol(HostName("2001:4860:0:2001::68"), NetworkProtocol.UNKNOWN).expectNoErrors()
        assertEquals("/ip6/2001:4860:0:2001::68", multiaddress.toString())
    }

    @Test
    fun fromTcp() {
        val multiaddress = InetMultiaddress.fromHostNameAndProtocol(HostName("10.20.30.40:1234"), NetworkProtocol.TCP).expectNoErrors()
        assertEquals("/ip4/10.20.30.40/tcp/1234", multiaddress.toString())
    }

    @Test
    fun fromUdp() {
        val multiaddress = InetMultiaddress.fromHostNameAndProtocol(HostName("10.20.30.40:1234"), NetworkProtocol.UDP).expectNoErrors()
        assertEquals("/ip4/10.20.30.40/udp/1234", multiaddress.toString())
    }

    @TestFactory
    fun testDialArgs(): Stream<DynamicTest> {
        return listOf(
            Tuple2("/ip4/127.0.0.1/udp/1234", "Inet: 127.0.0.1:1234 (UDP)"),
            Tuple2("/ip4/127.0.0.1/tcp/4321", "Inet: 127.0.0.1:4321 (TCP)"),
            Tuple2("/ip6/::1/udp/1234", "Inet: [::1]:1234 (UDP)"),
            Tuple2("/ip6/::1/tcp/4321", "Inet: [::1]:4321 (TCP)"),
            Tuple2("/ip6/::1", "Inet: ::1"), // Just an IP
            Tuple2("/ip4/1.2.3.4", "Inet: 1.2.3.4"), // Just an IP
            Tuple2("/ip6zone/foo/ip6/::1/tcp/4321", "Inet: [::1%foo]:4321 (TCP)"), // zone
            Tuple2("/ip6zone/foo/ip6/::1/udp/4321", "Inet: [::1%foo]:4321 (UDP)"), // zone
            Tuple2("/ip6zone/foo/ip6/::1", "Inet: ::1%foo"), // no TCP
            //           Tuple2("/ip6zone/foo/ip6/::1/ip6zone/bar", "Inet: ::1%foo"), // IP over IP
            Tuple2("/dns/abc.com/tcp/1234", "Inet: abc.com:1234 (TCP)"), // DNS4:port
            Tuple2("/dns4/abc.com/tcp/1234", "Inet: abc.com:1234 (TCP)"), // DNS4:port
            Tuple2("/dns4/abc.com", "Inet: abc.com"), // Just DNS4
            Tuple2("/dns6/abc.com/udp/1234", "Inet: abc.com:1234 (UDP)"), // DNS6:port
            Tuple2("/dns6/abc.com", "Inet: abc.com") // Just DNS6
        ).map { (maddress, expectedHost) ->
            DynamicTest.dynamicTest("Test: $maddress") {
                val multiaddress = InetMultiaddress.fromString(maddress).expectNoErrors()
                assertEquals(expectedHost, toHostString(multiaddress))
            }
        }.stream()
    }

    @Test
    fun testDialArgsIp4WithZone() {
        assertErrorResult("/ip6zone/foo/ip4/127.0.0.1 has ip4 with zone") { InetMultiaddress.fromString("/ip6zone/foo/ip4/127.0.0.1") }
    }

    @Test
    fun testDialArgsIp6MultipleZones() {
        assertErrorResult("/ip6zone/foo/ip6zone/bar/ip6/::1 has multiple zones") { InetMultiaddress.fromString("/ip6zone/foo/ip6zone/bar/ip6/::1") }
    }

    private fun toHostString(address: InetMultiaddress): String {
        val sb = StringBuilder()
        sb.append("Inet: ")
        val hostName = address.hostName
        if (hostName != null) {
            val ipAddress = hostName.asAddress()
            if (ipAddress != null) {
                if (hostName.port != null && hostName.port != 0) {
                    if (ipAddress.isIPv6) {
                        sb.append("[${ipAddress.toCompressedString()}]:${hostName.port}")
                    } else {
                        sb.append("${ipAddress.toCompressedString()}:${hostName.port}")
                    }
                } else {
                    sb.append(ipAddress.toCompressedString())
                }
            } else {
                sb.append(hostName.toString())
            }
        }
        if (address.networkProtocol != NetworkProtocol.UNKNOWN) {
            sb.append(" (${address.networkProtocol})")
        }
        return sb.toString()
    }
}
