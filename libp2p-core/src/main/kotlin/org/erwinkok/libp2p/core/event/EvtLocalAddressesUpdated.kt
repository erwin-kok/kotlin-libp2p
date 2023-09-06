// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.event

import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.record.Envelope

enum class AddrAction {
    Unknown,
    Added,
    Maintained,
    Removed,
}

data class UpdatedAddress(
    val address: InetMultiaddress,
    val action: AddrAction,
) {
    override fun equals(other: Any?): Boolean {
        return if (other !is UpdatedAddress) {
            false
        } else {
            address == other.address &&
                action == other.action
        }
    }

    override fun hashCode(): Int {
        return address.hashCode() xor action.hashCode()
    }
}

data class EvtLocalAddressesUpdated(
    val diffs: Boolean,
) : EventType() {
    val current: MutableSet<UpdatedAddress> = mutableSetOf()
    val removed: MutableSet<UpdatedAddress> = mutableSetOf()
    var signedPeerRecord: Envelope? = null
}
