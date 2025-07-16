// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.protocol.identify

import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.NetworkConnection

data class NewObservation(
    val connection: NetworkConnection,
    val observed: InetMultiaddress,
)
