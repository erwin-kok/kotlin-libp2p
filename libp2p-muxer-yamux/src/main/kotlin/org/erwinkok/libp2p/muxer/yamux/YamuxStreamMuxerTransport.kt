// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.yamux

import kotlinx.coroutines.CoroutineScope
import org.erwinkok.libp2p.core.network.Connection
import org.erwinkok.libp2p.core.network.streammuxer.StreamMuxerConnection
import org.erwinkok.libp2p.core.network.streammuxer.StreamMuxerTransport
import org.erwinkok.libp2p.core.network.streammuxer.StreamMuxerTransportFactory
import org.erwinkok.libp2p.core.resourcemanager.PeerScope
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.Ok
import org.erwinkok.result.Result

class YamuxStreamMuxerTransport private constructor(
    private val coroutineScope: CoroutineScope,
) : StreamMuxerTransport {
    override suspend fun newConnection(connection: Connection, initiator: Boolean, scope: PeerScope): Result<StreamMuxerConnection> {
        return Ok(YamuxStreamMuxerConnection(coroutineScope, connection, initiator))
    }

    companion object YamuxStreamMuxerTransportFactory : StreamMuxerTransportFactory {
        override val protocolId: ProtocolId
            get() = ProtocolId.of("/yamux/1.0.0")

        override fun create(scope: CoroutineScope): Result<StreamMuxerTransport> {
            return Ok(YamuxStreamMuxerTransport(scope))
        }
    }
}
