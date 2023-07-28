// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.network

import org.erwinkok.libp2p.core.network.streammuxer.MuxedStream
import org.erwinkok.libp2p.core.resourcemanager.StreamScope
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.Result

interface Stream : MuxedStream {
    override val id: String
    val connection: NetworkConnection
    val streamScope: StreamScope
    val statistic: ConnectionStatistics
    fun protocol(): ProtocolId
    fun setProtocol(protocolId: ProtocolId): Result<Unit>
}
