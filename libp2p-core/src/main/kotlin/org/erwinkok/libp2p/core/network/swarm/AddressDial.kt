// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network.swarm

import org.erwinkok.libp2p.core.network.InetMultiaddress
import java.time.Instant

internal data class AddressDial(
    val address: InetMultiaddress,
    val retries: Int,
    val createdAt: Instant,
    override val scheduleTime: Instant,
) : TimedQueueElement {
    var connection: SwarmConnection? = null
}
