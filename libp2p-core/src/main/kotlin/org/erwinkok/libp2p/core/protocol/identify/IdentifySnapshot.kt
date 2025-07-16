// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.protocol.identify

import org.erwinkok.libp2p.core.base.hashCodeOf
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.record.Envelope
import org.erwinkok.multiformat.multistream.ProtocolId

class IdentifySnapshot(
    val sequence: Long = 0L,
    val protocols: Set<ProtocolId> = setOf(),
    val addresses: List<InetMultiaddress> = listOf(),
    val record: Envelope? = null,
) {
    override fun toString(): String {
        return "snapshot: $sequence (protos: ${protocols.joinToString()}) (addrs: ${addresses.joinToString()}))"
    }

    override fun equals(other: Any?): Boolean {
        // Note that the sequence is not compared
        if (other === this) {
            return true
        }
        if (other !is IdentifySnapshot) {
            return super.equals(other)
        }
        if (compareAddresses(addresses, other.addresses)) {
            return false
        }
        if (compareProtocols(protocols, other.protocols)) {
            return false
        }
        if (compareRecord(record, other.record)) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        return hashCodeOf(addresses, protocols, record)
    }

    private fun compareAddresses(a1: List<InetMultiaddress>, a2: List<InetMultiaddress>): Boolean {
        if (a1.size != a2.size) {
            return true
        }
        for (multiaddress in a1) {
            if (!a2.contains(multiaddress)) {
                return true
            }
        }
        return false
    }

    private fun compareProtocols(s1: Set<ProtocolId>, s2: Set<ProtocolId>): Boolean {
        if (s1.size != s2.size) {
            return true
        }
        for (multiaddress in s1) {
            if (!s2.contains(multiaddress)) {
                return true
            }
        }
        return false
    }

    private fun compareRecord(r1: Envelope?, r2: Envelope?): Boolean {
        if (r1 == null && r2 == null) {
            return false
        }
        if (r1 == null || r2 == null) {
            return true
        }
        return r1 != r2
    }
}
