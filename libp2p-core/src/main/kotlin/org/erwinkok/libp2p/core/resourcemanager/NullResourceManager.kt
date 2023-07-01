// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.resourcemanager

import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.Direction
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.Ok
import org.erwinkok.result.Result

val NullResourceManager = object : ResourceManager {
    override fun viewSystem(f: (ResourceScope) -> Result<Unit>): Result<Unit> {
        return f(NullScope.NullScope)
    }

    override fun viewTransient(f: (ResourceScope) -> Result<Unit>): Result<Unit> {
        return f(NullScope.NullScope)
    }

    override fun viewService(s: String, f: (ServiceScope) -> Result<Unit>): Result<Unit> {
        return f(NullScope.NullScope)
    }

    override fun viewProtocol(protocolId: ProtocolId, f: (ProtocolScope) -> Result<Unit>): Result<Unit> {
        return f(NullScope.NullScope)
    }

    override fun viewPeer(peerId: PeerId, f: (PeerScope) -> Result<Unit>): Result<Unit> {
        return f(NullScope.NullScope)
    }

    override fun openConnection(dir: Direction, usefd: Boolean, endpoint: InetMultiaddress): Result<ConnectionManagementScope> {
        return Ok(NullScope.NullScope)
    }

    override fun openStream(peerId: PeerId, dir: Direction): Result<StreamManagementScope> {
        return Ok(NullScope.NullScope)
    }

    override fun close() = Unit
}
