// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.protocol.identify

import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.record.Envelope
import org.erwinkok.libp2p.crypto.CryptoUtil
import org.erwinkok.libp2p.crypto.KeyType
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class IdentifySnapshotTest {
    @Test
    fun equalityRecord() {
        val (_, pk1) = CryptoUtil.generateKeyPair(KeyType.ED25519).expectNoErrors()
        val (_, pk2) = CryptoUtil.generateKeyPair(KeyType.ED25519).expectNoErrors()
        val record1 = Envelope(pk1, byteArrayOf(), byteArrayOf(), byteArrayOf())
        val record2 = Envelope(pk2, byteArrayOf(), byteArrayOf(), byteArrayOf())
        val snapshot = IdentifySnapshot()
        assertTrue(snapshot.update(newRecord = record1))
        assertFalse(snapshot.update(newRecord = record1))
        assertTrue(snapshot.update(newRecord = record2))
    }

    @Test
    fun equalityAddresses() {
        val address1 = InetMultiaddress.fromString("/ip4/127.0.0.1/tcp/1234").expectNoErrors()
        val address2 = InetMultiaddress.fromString("/ip4/127.0.0.1/udp/1234/quic-v1").expectNoErrors()
        val snapshot = IdentifySnapshot()
        assertTrue(snapshot.update(newAddresses = listOf(address1)))
        assertFalse(snapshot.update(newAddresses = listOf(address1)))
        assertTrue(snapshot.update(newAddresses = listOf(address2)))
        assertTrue(snapshot.update(newAddresses = listOf(address1, address2)))
        assertFalse(snapshot.update(newAddresses = listOf(address1, address2)))
        assertFalse(snapshot.update(newAddresses = listOf(address2, address1)))
        assertTrue(snapshot.update(newAddresses = listOf(address2)))
        assertTrue(snapshot.update(newAddresses = listOf(address1)))
        assertTrue(snapshot.update(newAddresses = listOf(address1, address2)))
        assertFalse(snapshot.update(newAddresses = listOf(address2, address1)))
    }

    @Test
    fun equalityProtocols() {
        val protocol1 = ProtocolId.of("/foo")
        val protocol2 = ProtocolId.of("/bar")
        val snapshot = IdentifySnapshot()
        assertTrue(snapshot.update(newProtocols = setOf(protocol1)))
        assertFalse(snapshot.update(newProtocols = setOf(protocol1)))
        assertFalse(snapshot.update(newProtocols = setOf(protocol1, protocol1)))
        assertTrue(snapshot.update(newProtocols = setOf(protocol2)))
        assertTrue(snapshot.update(newProtocols = setOf(protocol1, protocol2)))
        assertFalse(snapshot.update(newProtocols = setOf(protocol1, protocol2)))
        assertTrue(snapshot.update(newProtocols = setOf(protocol2)))
        assertFalse(snapshot.update(newProtocols = setOf(protocol2, protocol2)))
        assertTrue(snapshot.update(newProtocols = setOf(protocol1)))
        assertTrue(snapshot.update(newProtocols = setOf(protocol1, protocol2)))
        assertFalse(snapshot.update(newProtocols = setOf(protocol2, protocol1)))
    }
}
