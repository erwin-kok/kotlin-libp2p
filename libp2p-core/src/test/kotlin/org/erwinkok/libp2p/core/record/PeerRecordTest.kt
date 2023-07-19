// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.record

import org.erwinkok.libp2p.core.host.PeerId.Companion.fromPrivateKey
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.crypto.CryptoUtil.generateKeyPair
import org.erwinkok.libp2p.crypto.KeyType
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PeerRecordTest {
    @Test
    fun peerRecordConstants() {
        assertEquals("libp2p-peer-record", PeerRecord.PeerRecordEnvelopeDomain)
        assertArrayEquals(byteArrayOf(0x03, 0x01), PeerRecord.PeerRecordEnvelopePayloadType)
    }

    @Test
    fun signedPeerRecordFromEnvelope() {
        val (privateKey) = generateKeyPair(KeyType.ED25519, 256).expectNoErrors()
        val addrs = generateTestAddresses(10)
        val id = fromPrivateKey(privateKey).expectNoErrors()
        val rec = PeerRecord.fromPeerIdAndAddresses(id, addrs).expectNoErrors()
        val envelope = Envelope.seal(rec, privateKey).expectNoErrors()
        val envBytes = envelope.marshal().expectNoErrors()
        val env2 = Envelope.consumeEnvelope(envBytes, PeerRecord.PeerRecordEnvelopeDomain).expectNoErrors()
        val rec2 = env2.record as PeerRecord
        assertEquals(rec, rec2, "expected peer record to be unaltered after round-trip serde")
        assertEquals(envelope, env2.envelope, "expected signed envelope to be unchanged after round-trip serde")
    }

    @Test
    fun signedPeerRecordFromTypedEnvelope() {
        val (privateKey) = generateKeyPair(KeyType.ED25519, 256).expectNoErrors()
        val addrs = generateTestAddresses(10)
        val id = fromPrivateKey(privateKey).expectNoErrors()
        val rec = PeerRecord.fromPeerIdAndAddresses(id, addrs).expectNoErrors()
        val envelope = Envelope.seal(rec, privateKey).expectNoErrors()
        val envBytes = envelope.marshal().expectNoErrors()
        val env2 = Envelope.consumeTypedEnvelope<PeerRecord>(envBytes).expectNoErrors()
        assertEquals(rec, env2.record, "expected peer record to be unaltered after round-trip serde")
        assertEquals(envelope, env2.envelope, "expected signed envelope to be unchanged after round-trip serde")
    }

    private fun generateTestAddresses(n: Int): List<InetMultiaddress> {
        val result = mutableListOf<InetMultiaddress>()
        for (i in 0 until n) {
            result.add(InetMultiaddress.fromString(String.format("/ip4/1.2.3.4/tcp/%d", i)).expectNoErrors())
        }
        return result
    }
}
