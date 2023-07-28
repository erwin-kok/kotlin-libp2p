// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network

import kotlin.time.Duration

data class AddressDelay(
    val address: InetMultiaddress,
    val delay: Duration,
)
