// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.resourcemanager

import org.erwinkok.libp2p.core.PeerId
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.Result

interface ResourceScopeViewer {
    fun viewSystem(f: (ResourceScope) -> Result<Unit>): Result<Unit>
    fun viewTransient(f: (ResourceScope) -> Result<Unit>): Result<Unit>
    fun viewService(s: String, f: (ServiceScope) -> Result<Unit>): Result<Unit>
    fun viewProtocol(protocolId: ProtocolId, f: (ProtocolScope) -> Result<Unit>): Result<Unit>
    fun viewPeer(peerId: PeerId, f: (PeerScope) -> Result<Unit>): Result<Unit>
}
