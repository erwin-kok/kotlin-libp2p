// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.resourcemanager

import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.Result

interface StreamManagementScope : StreamScope, ResourceScopeSpan {
    fun protocolScope(): ProtocolScope
    fun setProtocol(proto: ProtocolId): Result<Unit>
    fun serviceScope(): ServiceScope
    fun peerScope(): PeerScope
}
