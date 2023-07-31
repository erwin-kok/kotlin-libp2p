// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.resourcemanager

import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.Ok
import org.erwinkok.result.Result

class NullScope :
    ResourceScope,
    ResourceScopeSpan,
    ServiceScope,
    ProtocolScope,
    PeerScope,
    ConnectionManagementScope,
    ConnectionScope,
    StreamManagementScope,
    StreamScope {
    override val peerScope: PeerScope
        get() = NullScope
    override val name: String
        get() = ""
    override val protocol: ProtocolId
        get() = ProtocolId.Null
    override val peer: PeerId
        get() = PeerId.Null

    override fun reserveMemory(size: Int, prio: UByte): Result<Unit> = Ok(Unit)
    override fun releaseMemory(size: Int) = Unit
    override fun statistic(): ScopeStatistic = ScopeStatistic()
    override fun beginSpan(): Result<ResourceScopeSpan> = Ok(NullScope)
    override fun done() = Unit
    override fun setPeer(peerId: PeerId): Result<Unit> = Ok(Unit)
    override fun protocolScope(): ProtocolScope = NullScope
    override fun setProtocol(proto: ProtocolId): Result<Unit> = Ok(Unit)
    override fun serviceScope(): ServiceScope = NullScope

    override fun setService(srv: String): Result<Unit> = Ok(Unit)

    companion object {
        val NullScope = NullScope()
    }
}
