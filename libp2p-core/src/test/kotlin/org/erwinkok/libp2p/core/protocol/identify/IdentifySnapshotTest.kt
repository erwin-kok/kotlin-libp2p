// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.protocol.identify

import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.record.Envelope
import org.erwinkok.libp2p.crypto.CryptoUtil
import org.erwinkok.libp2p.crypto.KeyType
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.expectNoErrors
import org.erwinkok.util.Tuple3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream

internal class IdentifySnapshotTest {
    @TestFactory
    fun equality(): Stream<DynamicTest> {
        val address1 = InetMultiaddress.fromString("/ip4/127.0.0.1/tcp/1234").expectNoErrors()
        val address2 = InetMultiaddress.fromString("/ip4/127.0.0.1/udp/1234/quic-v1").expectNoErrors()
        val (_, pk1) = CryptoUtil.generateKeyPair(KeyType.ED25519).expectNoErrors()
        val (_, pk2) = CryptoUtil.generateKeyPair(KeyType.ED25519).expectNoErrors()
        val record1 = Envelope(pk1, byteArrayOf(), byteArrayOf(), byteArrayOf())
        val record2 = Envelope(pk2, byteArrayOf(), byteArrayOf(), byteArrayOf())
        return listOf(
            Tuple3(IdentifySnapshot(record = record1), IdentifySnapshot(record = record1), true),
            Tuple3(IdentifySnapshot(record = record1), IdentifySnapshot(record = record2), false),
            Tuple3(IdentifySnapshot(addresses = listOf(address1)), IdentifySnapshot(addresses = listOf(address1)), true),
            Tuple3(IdentifySnapshot(addresses = listOf(address1)), IdentifySnapshot(addresses = listOf(address2)), false),
            Tuple3(IdentifySnapshot(addresses = listOf(address1, address2)), IdentifySnapshot(addresses = listOf(address2)), false),
            Tuple3(IdentifySnapshot(addresses = listOf(address1, address2)), IdentifySnapshot(addresses = listOf(address2, address1)), true),
            Tuple3(IdentifySnapshot(addresses = listOf(address1)), IdentifySnapshot(addresses = listOf(address1, address2)), false),
            Tuple3(IdentifySnapshot(protocols = setOf(ProtocolId.of("/foo"), ProtocolId.of("/bar"))), IdentifySnapshot(protocols = setOf(ProtocolId.of("/bar"), ProtocolId.of("/foo"))), true),
            Tuple3(IdentifySnapshot(protocols = setOf(ProtocolId.of("/foo"))), IdentifySnapshot(protocols = setOf(ProtocolId.of("/foo"))), true),
            Tuple3(IdentifySnapshot(protocols = setOf(ProtocolId.of("/foo"))), IdentifySnapshot(protocols = setOf(ProtocolId.of("/bar"))), false),
            Tuple3(IdentifySnapshot(protocols = setOf(ProtocolId.of("/foo"), ProtocolId.of("/bar"))), IdentifySnapshot(protocols = setOf(ProtocolId.of("/bar"))), false),
            Tuple3(IdentifySnapshot(protocols = setOf(ProtocolId.of("/foo"))), IdentifySnapshot(protocols = setOf(ProtocolId.of("/foo"), ProtocolId.of("/bar"))), false),
        ).map { (s1, s2, result) ->
            DynamicTest.dynamicTest("Test: $s1, $s2 -> $result") {
                if (result) {
                    assertEquals(s1, s2)
                } else {
                    assertNotEquals(s1, s2)
                }
            }
        }.stream()
    }
}
