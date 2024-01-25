// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.network.streammuxer

import org.erwinkok.libp2p.core.network.Connection
import org.erwinkok.libp2p.core.resourcemanager.PeerScope
import org.erwinkok.result.Result

interface StreamMuxerTransport {
    suspend fun newConnection(connection: Connection, initiator: Boolean, scope: PeerScope?): Result<StreamMuxerConnection>
}
