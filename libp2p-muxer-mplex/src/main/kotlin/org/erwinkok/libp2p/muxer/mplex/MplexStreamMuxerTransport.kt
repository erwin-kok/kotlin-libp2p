// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.mplex

import kotlinx.coroutines.CoroutineScope
import org.erwinkok.libp2p.core.network.Connection
import org.erwinkok.libp2p.core.network.streammuxer.StreamMuxerConnection
import org.erwinkok.libp2p.core.network.streammuxer.StreamMuxerTransport
import org.erwinkok.libp2p.core.network.streammuxer.StreamMuxerTransportFactory
import org.erwinkok.libp2p.core.resourcemanager.PeerScope
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.Ok
import org.erwinkok.result.Result

class MplexStreamMuxerTransport private constructor(
    private val coroutineScope: CoroutineScope,
) : StreamMuxerTransport {
    override suspend fun newConnection(connection: Connection, initiator: Boolean, scope: PeerScope): Result<StreamMuxerConnection> {
        return Ok(MplexStreamMuxerConnection(coroutineScope, connection, initiator))
    }

    companion object MplexStreamMuxerTransportFactory : StreamMuxerTransportFactory {
        override val protocolId: ProtocolId
            get() = ProtocolId.of("/mplex/6.7.0")

        override fun create(scope: CoroutineScope): Result<StreamMuxerTransport> {
            return Ok(MplexStreamMuxerTransport(scope))
        }
    }
}
