// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.network.swarm

import kotlinx.coroutines.Job
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.network.ConnectionStatistics
import org.erwinkok.libp2p.core.network.Stream
import org.erwinkok.libp2p.core.network.streammuxer.MuxedStream
import org.erwinkok.libp2p.core.resourcemanager.StreamManagementScope
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.Result
import org.erwinkok.result.onSuccess
import java.util.concurrent.atomic.AtomicReference

class SwarmStream(
    private val stream: MuxedStream,
    override val connection: SwarmConnection,
    private val identifier: Long,
    override val streamScope: StreamManagementScope,
    override val statistic: ConnectionStatistics,
    private val onClose: (SwarmStream) -> Unit,
) : AwaitableClosable, Stream, MuxedStream by stream {
    private val _context = Job()
    private val protocol = AtomicReference(ProtocolId.Null)

    override val jobContext: Job get() = _context

    override val id: String
        get() = "${connection.id}-$identifier"

    override val name: String
        get() = id

    override fun protocol(): ProtocolId {
        return protocol.get()
    }

    override fun setProtocol(protocolId: ProtocolId): Result<Unit> {
        return streamScope.setProtocol(protocolId)
            .onSuccess { protocol.set(protocolId) }
    }

    override fun toString(): String {
        return "<SwarmStream[${connection.id}] ${connection.localAddress} (${connection.localIdentity}) <-> ${connection.remoteAddress} (${connection.remoteIdentity.peerId})>"
    }

    override fun reset() {
        stream.reset()
        onClose(this)
        _context.complete()
    }

    override fun close() {
        stream.close()
        onClose(this)
        _context.complete()
    }
}
