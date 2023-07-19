// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.record

import mu.KotlinLogging
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.getOrElse
import org.erwinkok.result.map
import org.erwinkok.result.onFailure
import org.erwinkok.result.onSuccess

private val logger = KotlinLogging.logger {}

class AddressInfo private constructor(
    val peerId: PeerId,
    val addresses: List<InetMultiaddress>,
) {
    fun p2pAddresses(): Result<List<InetMultiaddress>> {
        if (addresses.isEmpty()) {
            return InetMultiaddress.fromString("/p2p/$peerId")
                .map { listOf(it) }
        }
        return Ok(addresses.map { it.withPeerId(peerId) })
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is AddressInfo) {
            return super.equals(other)
        }
        if (peerId != other.peerId) {
            return false
        }
        return if (addresses.size != other.addresses.size) {
            false
        } else {
            addresses.containsAll(other.addresses) && other.addresses.containsAll(addresses)
        }
    }

    override fun hashCode(): Int {
        return peerId.hashCode() xor addresses.hashCode()
    }

    override fun toString(): String {
        return "{$peerId: ${addresses.joinToString(", ")}}"
    }

    companion object {
        fun fromPeerId(peerId: PeerId): AddressInfo {
            return AddressInfo(peerId, listOf())
        }

        fun fromPeerIdAndAddresses(peerId: PeerId, addresses: List<InetMultiaddress>): AddressInfo {
            return AddressInfo(peerId, addresses)
        }

        fun fromP2pAddress(multiaddress: InetMultiaddress): Result<AddressInfo> {
            val peerId = multiaddress.peerId
                .getOrElse { return Err("$multiaddress does not contain a multihash") }
            val transport = multiaddress.withoutPeerId()
            return if (transport.isNotEmpty) {
                Ok(AddressInfo(peerId, listOf(transport)))
            } else {
                Ok(AddressInfo(peerId, listOf()))
            }
        }

        fun fromP2pAddresses(vararg multiaddress: InetMultiaddress): Result<List<AddressInfo>> {
            return fromP2pAddresses(listOf(*multiaddress))
        }

        fun fromP2pAddresses(multiaddresses: List<InetMultiaddress>): Result<List<AddressInfo>> {
            val map = mutableMapOf<PeerId, MutableList<InetMultiaddress>>()
            for (multiaddress in multiaddresses) {
                multiaddress.peerId
                    .onSuccess { peerId ->
                        val list = map.computeIfAbsent(peerId) { mutableListOf() }
                        val transport = multiaddress.withoutPeerId()
                        if (transport.isNotEmpty) {
                            list.add(transport)
                        }
                    }
                    .onFailure {
                        logger.warn { "Could not add $multiaddress to AddressInfo: no PeerId present" }
                    }
            }
            return Ok(map.entries.map { (k, v) -> AddressInfo(k, v) })
        }

        fun transport(a: InetMultiaddress): InetMultiaddress? {
            val withoutPeerId = a.withoutPeerId()
            return if (withoutPeerId.isNotEmpty) withoutPeerId else null
        }
    }
}
