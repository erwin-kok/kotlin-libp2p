// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.protocol.identify

import kotlinx.atomicfu.locks.ReentrantLock
import mu.KotlinLogging
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.record.Envelope
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.errorMessage
import org.erwinkok.result.getOrElse
import kotlin.concurrent.withLock

private val logger = KotlinLogging.logger {}

class IdentifySnapshot(
    private var disableSignedPeerRecord: Boolean = false
) {
    private val lock = ReentrantLock()
    val protocols = mutableSetOf<ProtocolId>()
    val addresses = mutableListOf<InetMultiaddress>()
    var record: Envelope? = null
    var sequence: Long = 0L
        private set

    fun update(newAddresses: List<InetMultiaddress> = listOf(), newProtocols: Set<ProtocolId> = setOf(), newRecord: Envelope? = null): Boolean {
        lock.withLock {
            if (compareAddresses(newAddresses, addresses) || compareProtocols(newProtocols, protocols) || compareRecord(newRecord, record)) {
                addresses.clear()
                addresses.addAll(newAddresses)
                protocols.clear()
                protocols.addAll(newProtocols)
                record = newRecord
                sequence++
                logger.info { "Updating identify snapshot: $sequence: protocols=[${protocols.joinToString(", ")}], addresses=[${addresses.joinToString(", ")}]" }
                return true
            }
        }
        return false
    }

    fun getSignedRecord(): ByteArray? {
        val r = record
        if (disableSignedPeerRecord || r == null) {
            return null
        }
        return r.marshal()
            .getOrElse {
                logger.error { "failed to marshal signed record: ${errorMessage(it)}" }
                return null
            }
    }

    private fun compareAddresses(a1: List<InetMultiaddress>, a2: MutableList<InetMultiaddress>): Boolean {
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

    private fun compareProtocols(s1: Set<ProtocolId>, s2: MutableSet<ProtocolId>): Boolean {
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
