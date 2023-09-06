// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.protocol.identify

import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.protocol.identify.ObservedAddressManager.Companion.ActivationThreshold
import java.time.Instant

class ObservedAddress(val address: InetMultiaddress) {
    val seenBy = mutableMapOf<String, Observation>()
    var lastSeen: Instant = Instant.now()
    var numInbound = 0

    val isActivated: Boolean
        get() = seenBy.size >= ActivationThreshold

    val groupKey: String
        get() {
            val hostName = address.hostName
            if (hostName != null) {
                val address = hostName.address
                return address?.toString() ?: hostName.toString()
            }
            return address.toString()
        }
}
