// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.network.address

import kotlinx.coroutines.test.runTest
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.address.IpUtil.isIp6LinkLocal
import org.erwinkok.result.assertErrorResult
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class AddressUtilTest {
    @Test
    fun testFilterAddresses() = runTest {
        val bad = listOf(
            InetMultiaddress.fromString("/ip6/fe80::1/tcp/1234").expectNoErrors(), // link local
            InetMultiaddress.fromString("/ip6/fe80::100/tcp/1234").expectNoErrors(), // link local
        )
        val good = listOf(
            InetMultiaddress.fromString("/ip4/127.0.0.1/tcp/1234").expectNoErrors(),
            InetMultiaddress.fromString("/ip6/::1/tcp/1234").expectNoErrors(),
            InetMultiaddress.fromString("/ip4/1.2.3.4/udp/1234/utp").expectNoErrors(),
        )
        val goodAndBad = mutableListOf<InetMultiaddress>()
        goodAndBad.addAll(good)
        goodAndBad.addAll(bad)
        assertInetMultiaddressEqual(AddressUtil.filterAddresses(bad) { AddressUtil.addressOverNonLocalIp(it) }, listOf())
        assertInetMultiaddressEqual(AddressUtil.filterAddresses(good) { AddressUtil.addressOverNonLocalIp(it) }, good)
        assertInetMultiaddressEqual(AddressUtil.filterAddresses(goodAndBad) { AddressUtil.addressOverNonLocalIp(it) }, good)
    }

    @Test
    fun testInterfaceAddresses() {
        val addrs = AddressUtil.interfaceAddresses().expectNoErrors()
        assertFalse(addrs.isEmpty())
        assertTrue(addrs.none { isIp6LinkLocal(it) })
    }

    @Test
    fun testResolvingAddresses() {
        val unspec = listOf(
            InetMultiaddress.fromString("/ip4/0.0.0.0/tcp/1234").expectNoErrors(),
            InetMultiaddress.fromString("/ip4/1.2.3.4/tcp/1234").expectNoErrors(),
            InetMultiaddress.fromString("/ip6/::/tcp/1234").expectNoErrors(),
            InetMultiaddress.fromString("/ip6/::100/tcp/1234").expectNoErrors(),
        )
        val iface = listOf(
            InetMultiaddress.fromString("/ip4/127.0.0.1").expectNoErrors(),
            InetMultiaddress.fromString("/ip4/10.20.30.40").expectNoErrors(),
            InetMultiaddress.fromString("/ip6/::1").expectNoErrors(),
            InetMultiaddress.fromString("/ip6/::f").expectNoErrors(),
        )
        val spec = listOf(
            InetMultiaddress.fromString("/ip4/127.0.0.1/tcp/1234").expectNoErrors(),
            InetMultiaddress.fromString("/ip4/10.20.30.40/tcp/1234").expectNoErrors(),
            InetMultiaddress.fromString("/ip4/1.2.3.4/tcp/1234").expectNoErrors(),
            InetMultiaddress.fromString("/ip6/::1/tcp/1234").expectNoErrors(),
            InetMultiaddress.fromString("/ip6/::f/tcp/1234").expectNoErrors(),
            InetMultiaddress.fromString("/ip6/::100/tcp/1234").expectNoErrors(),
        )
        val actual = AddressUtil.resolveUnspecifiedAddresses(unspec, iface).expectNoErrors()
        assertTrue(spec.containsAll(actual))
        assertTrue(actual.containsAll(spec))
        val ip4u = listOf(InetMultiaddress.fromString("/ip4/0.0.0.0").expectNoErrors())
        val ip4i = listOf(InetMultiaddress.fromString("/ip4/1.2.3.4").expectNoErrors())
        val ip6u = listOf(InetMultiaddress.fromString("/ip6/::").expectNoErrors())
        val ip6i = listOf(InetMultiaddress.fromString("/ip6/::1").expectNoErrors())
        assertErrorResult("failed to resolve: /ip4/0.0.0.0") { AddressUtil.resolveUnspecifiedAddress(ip4u[0], ip6i) }
        assertErrorResult("failed to resolve: /ip6/::") { AddressUtil.resolveUnspecifiedAddress(ip6u[0], ip4i) }
        assertErrorResult("failed to specify addresses: [/ip6/::]") { AddressUtil.resolveUnspecifiedAddresses(ip6u, ip4i) }
        assertErrorResult("failed to specify addresses: [/ip4/0.0.0.0]") { AddressUtil.resolveUnspecifiedAddresses(ip4u, ip6i) }
    }

    @Test
    fun testAddressInList() {
        val multiaddresses = listOf(
            InetMultiaddress.fromString("/ip4/0.0.0.0/tcp/1234").expectNoErrors(),
            InetMultiaddress.fromString("/ip6/::/tcp/1234").expectNoErrors(),
            InetMultiaddress.fromString("/ip6/fe80::/tcp/1234").expectNoErrors(),
        )
        assertFalse(AddressUtil.addressInList(InetMultiaddress.fromString("/ip6/fe80::1/tcp/1234").expectNoErrors(), multiaddresses))
        assertTrue(AddressUtil.addressInList(InetMultiaddress.fromString("/ip4/0.0.0.0/tcp/1234").expectNoErrors(), multiaddresses))
    }

    companion object {
        fun assertInetMultiaddressEqual(expected: List<InetMultiaddress>, actual: List<InetMultiaddress>) {
            assertEquals(expected.size, actual.size, "Expected and actual list sizes differ")
            for (multiaddress in actual) {
                assertTrue(expected.contains(multiaddress), "Multiaddress lists are not equal")
            }
        }
    }
}
