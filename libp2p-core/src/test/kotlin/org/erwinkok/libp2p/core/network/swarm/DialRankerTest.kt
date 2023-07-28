// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network.swarm

import org.erwinkok.libp2p.core.network.AddressDelay
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.swarm.DialRanker.PublicQUICDelay
import org.erwinkok.libp2p.core.network.swarm.DialRanker.PublicTCPDelay
import org.erwinkok.libp2p.core.network.swarm.DialRanker.defaultDialRanker
import org.erwinkok.libp2p.core.network.swarm.DialRanker.noDelayDialRanker
import org.erwinkok.result.expectNoErrors
import org.erwinkok.util.Tuple3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream
import kotlin.time.Duration.Companion.ZERO

internal class DialRankerTest {
    @TestFactory
    fun testNoDelayDialRanker(): Stream<DynamicTest> {
        val q1 = InetMultiaddress.fromString("/ip4/1.2.3.4/udp/1/quic").expectNoErrors()
        val q1v1 = InetMultiaddress.fromString("/ip4/1.2.3.4/udp/1/quic-v1").expectNoErrors()
        val wt1 = InetMultiaddress.fromString("/ip4/1.2.3.4/udp/1/quic-v1/webtransport/").expectNoErrors()
        val q2 = InetMultiaddress.fromString("/ip4/1.2.3.4/udp/2/quic").expectNoErrors()
        val q2v1 = InetMultiaddress.fromString("/ip4/1.2.3.4/udp/2/quic-v1").expectNoErrors()
        val q3 = InetMultiaddress.fromString("/ip4/1.2.3.4/udp/3/quic").expectNoErrors()
        val q3v1 = InetMultiaddress.fromString("/ip4/1.2.3.4/udp/3/quic-v1").expectNoErrors()
        val q4 = InetMultiaddress.fromString("/ip4/1.2.3.4/udp/4/quic").expectNoErrors()
        val t1 = InetMultiaddress.fromString("/ip4/1.2.3.5/tcp/1/").expectNoErrors()
        return listOf(
            Tuple3(
                "quic+webtransport filtered when quicv1",
                listOf(q1, q2, q3, q4, q1v1, q2v1, q3v1, wt1, t1),
                listOf(
                    AddressDelay(q1, ZERO),
                    AddressDelay(q2, ZERO),
                    AddressDelay(q3, ZERO),
                    AddressDelay(q4, ZERO),
                    AddressDelay(q1v1, ZERO),
                    AddressDelay(q2v1, ZERO),
                    AddressDelay(q3v1, ZERO),
                    AddressDelay(wt1, ZERO),
                    AddressDelay(t1, ZERO),
                ),
            ),
        ).map { (name: String, addresses: List<InetMultiaddress>, output: List<AddressDelay>) ->
            DynamicTest.dynamicTest("Test: $name") {
                val result = noDelayDialRanker(addresses)
                assertAddressDelayEqual(output, result)
            }
        }.stream()
    }

    @TestFactory
    fun testDelayRankerQUICDelay(): Stream<DynamicTest> {
        val q1 = InetMultiaddress.fromString("/ip4/1.2.3.4/udp/1/quic").expectNoErrors()
        val q1v1 = InetMultiaddress.fromString("/ip4/1.2.3.4/udp/1/quic-v1").expectNoErrors()
        val wt1 = InetMultiaddress.fromString("/ip4/1.2.3.4/udp/1/quic-v1/webtransport/").expectNoErrors()
        val q2 = InetMultiaddress.fromString("/ip4/1.2.3.4/udp/2/quic").expectNoErrors()
        val q2v1 = InetMultiaddress.fromString("/ip4/1.2.3.4/udp/2/quic-v1").expectNoErrors()
        val q3 = InetMultiaddress.fromString("/ip4/1.2.3.4/udp/3/quic").expectNoErrors()
        val q3v1 = InetMultiaddress.fromString("/ip4/1.2.3.4/udp/3/quic-v1").expectNoErrors()
        val q4 = InetMultiaddress.fromString("/ip4/1.2.3.4/udp/4/quic").expectNoErrors()

        val q1v16 = InetMultiaddress.fromString("/ip6/1::2/udp/1/quic-v1").expectNoErrors()
        val q2v16 = InetMultiaddress.fromString("/ip6/1::2/udp/2/quic-v1").expectNoErrors()
        val q3v16 = InetMultiaddress.fromString("/ip6/1::2/udp/3/quic-v1").expectNoErrors()
        return listOf(
            Tuple3(
                "quic-ipv4",
                listOf(q1, q2, q3, q4),
                listOf(
                    AddressDelay(q1, ZERO),
                    AddressDelay(q2, PublicQUICDelay),
                    AddressDelay(q3, PublicQUICDelay),
                    AddressDelay(q4, PublicQUICDelay),
                ),
            ),
            Tuple3(
                "quic-ipv6",
                listOf(q1v16, q2v16, q3v16),
                listOf(
                    AddressDelay(q1v16, ZERO),
                    AddressDelay(q2v16, PublicQUICDelay),
                    AddressDelay(q3v16, PublicQUICDelay),
                ),
            ),
            Tuple3(
                "quic-ip4-ip6",
                listOf(q1, q1v16, q2v1, q3, q4),
                listOf(
                    AddressDelay(q1v16, ZERO),
                    AddressDelay(q2v1, PublicQUICDelay),
                    AddressDelay(q1, PublicQUICDelay.times(2)),
                    AddressDelay(q3, PublicQUICDelay.times(2)),
                    AddressDelay(q4, PublicQUICDelay.times(2)),
                ),
            ),
            Tuple3(
                "quic-quic-v1-webtransport",
                listOf(q1v16, q1, q2, q3, q4, q1v1, q2v1, q3v1, wt1),
                listOf(
                    AddressDelay(q1v16, ZERO),
                    AddressDelay(q1v1, PublicQUICDelay),
                    AddressDelay(q2v1, PublicQUICDelay.times(2)),
                    AddressDelay(q3v1, PublicQUICDelay.times(2)),
                    AddressDelay(q1, PublicQUICDelay.times(2)),
                    AddressDelay(q2, PublicQUICDelay.times(2)),
                    AddressDelay(q3, PublicQUICDelay.times(2)),
                    AddressDelay(q4, PublicQUICDelay.times(2)),
                    AddressDelay(wt1, PublicQUICDelay.times(2)),
                ),
            ),
            Tuple3(
                "wt-ranking",
                listOf(q1v16, q2v16, q3v16, q2, wt1),
                listOf(
                    AddressDelay(q1v16, ZERO),
                    AddressDelay(q2, PublicQUICDelay),
                    AddressDelay(wt1, PublicQUICDelay.times(2)),
                    AddressDelay(q2v16, PublicQUICDelay.times(2)),
                    AddressDelay(q3v16, PublicQUICDelay.times(2)),
                ),
            ),
        ).map { (name: String, addresses: List<InetMultiaddress>, output: List<AddressDelay>) ->
            DynamicTest.dynamicTest("Test: $name") {
                val result = defaultDialRanker(addresses)
                assertAddressDelayEqual(output, result)
            }
        }.stream()
    }

    @TestFactory
    fun testDelayRankerTCPDelay(): Stream<DynamicTest> {
        val q1 = InetMultiaddress.fromString("/ip4/1.2.3.4/udp/1/quic").expectNoErrors()
        val q1v1 = InetMultiaddress.fromString("/ip4/1.2.3.4/udp/1/quic-v1").expectNoErrors()
        val q2 = InetMultiaddress.fromString("/ip4/1.2.3.4/udp/2/quic").expectNoErrors()
        val q2v1 = InetMultiaddress.fromString("/ip4/1.2.3.4/udp/2/quic-v1").expectNoErrors()
        val q3 = InetMultiaddress.fromString("/ip4/1.2.3.4/udp/3/quic").expectNoErrors()

        val q1v16 = InetMultiaddress.fromString("/ip6/1::2/udp/1/quic-v1").expectNoErrors()
        val q2v16 = InetMultiaddress.fromString("/ip6/1::2/udp/2/quic-v1").expectNoErrors()
        val q3v16 = InetMultiaddress.fromString("/ip6/1::2/udp/3/quic-v1").expectNoErrors()

        val t1 = InetMultiaddress.fromString("/ip4/1.2.3.5/tcp/1/").expectNoErrors()
        val t1v6 = InetMultiaddress.fromString("/ip6/1::2/tcp/1").expectNoErrors()
        val t2 = InetMultiaddress.fromString("/ip4/1.2.3.4/tcp/2").expectNoErrors()
        return listOf(
            Tuple3(
                "quic-with-tcp-ip6-ip4",
                listOf(q1, q1v1, q1v16, q2v16, q3v16, q2v1, t1, t2),
                listOf(
                    AddressDelay(q1v16, ZERO),
                    AddressDelay(q1v1, PublicQUICDelay),
                    AddressDelay(q1, PublicQUICDelay.times(2)),
                    AddressDelay(q2v16, PublicQUICDelay.times(2)),
                    AddressDelay(q3v16, PublicQUICDelay.times(2)),
                    AddressDelay(q2v1, PublicQUICDelay.times(2)),
                    AddressDelay(t1, PublicQUICDelay.times(3)),
                    AddressDelay(t2, PublicQUICDelay.times(3)),
                ),
            ),
            Tuple3(
                "quic-ip4-with-tcp",
                listOf(q1, q2, q3, t1, t2, t1v6),
                listOf(
                    AddressDelay(q1, ZERO),
                    AddressDelay(q2, PublicQUICDelay),
                    AddressDelay(q3, PublicQUICDelay),
                    AddressDelay(t1, PublicQUICDelay + PublicTCPDelay),
                    AddressDelay(t2, PublicQUICDelay + PublicTCPDelay),
                    AddressDelay(t1v6, PublicQUICDelay + PublicTCPDelay),
                ),
            ),
            Tuple3(
                "tcp-ip4-ip6",
                listOf(t1, t2, t1v6),
                listOf(
                    AddressDelay(t1v6, ZERO),
                    AddressDelay(t1, ZERO),
                    AddressDelay(t2, ZERO),
                ),
            ),
        ).map { (name: String, addresses: List<InetMultiaddress>, output: List<AddressDelay>) ->
            DynamicTest.dynamicTest("Test: $name") {
                val result = defaultDialRanker(addresses)
                assertAddressDelayEqual(output, result)
            }
        }.stream()
    }

    private fun assertAddressDelayEqual(expected: List<AddressDelay>, actual: List<AddressDelay>) {
        assertEquals(expected.size, actual.size)
        for (addressDelay in actual) {
            assertTrue(expected.contains(addressDelay), "AddressDelay lists are not equal")
        }
    }
}
