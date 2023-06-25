// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.resourcemanager

import org.erwinkok.libp2p.core.PeerId
import org.erwinkok.result.Result

interface ConnectionManagementScope : ResourceScopeSpan, ConnectionScope {
    fun peerScope(): PeerScope
    fun setPeer(peerId: PeerId): Result<Unit>
}
