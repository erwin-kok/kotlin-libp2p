// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.peerstore.addressbook

import org.erwinkok.libp2p.core.network.InetMultiaddress
import java.time.Instant
import kotlin.time.Duration

data class AddressEntry(
    val address: InetMultiaddress,
    val expiry: Instant,
    val ttl: Duration,
)
