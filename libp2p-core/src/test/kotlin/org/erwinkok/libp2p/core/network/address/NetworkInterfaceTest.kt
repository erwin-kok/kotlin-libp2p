// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network.address

import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.address.AddressUtilTest.Companion.assertInetMultiaddressEqual
import org.erwinkok.libp2p.core.network.address.NetworkInterface.resolveUnspecifiedAddress
import org.erwinkok.libp2p.core.network.address.NetworkInterface.resolveUnspecifiedAddresses
import org.erwinkok.result.assertErrorResult
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Test

internal class NetworkInterfaceTest {
    @Test
    fun resolveAddresses() {
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

        val actual = resolveUnspecifiedAddresses(unspec, iface).expectNoErrors()
        assertInetMultiaddressEqual(spec, actual)

        val ip4u = InetMultiaddress.fromString("/ip4/0.0.0.0").expectNoErrors()
        val ip4i = InetMultiaddress.fromString("/ip4/1.2.3.4").expectNoErrors()

        val ip6u = InetMultiaddress.fromString("/ip6/::").expectNoErrors()
        val ip6i = InetMultiaddress.fromString("/ip6/::1").expectNoErrors()

        assertErrorResult("Could not resolve specified addresses") { resolveUnspecifiedAddress(ip4u, listOf(ip6i)) }
        assertErrorResult("Could not resolve specified addresses") { resolveUnspecifiedAddress(ip6u, listOf(ip4i)) }

        assertErrorResult("Could not resolve specified addresses") { resolveUnspecifiedAddresses(listOf(ip4u), listOf(ip6i)) }
        assertErrorResult("Could not resolve specified addresses") { resolveUnspecifiedAddresses(listOf(ip6u), listOf(ip4i)) }
    }
}
