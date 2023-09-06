// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.event

import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.multiformat.multistream.ProtocolId

data class EvtPeerProtocolsUpdated(
    val peerId: PeerId,
    val added: Set<ProtocolId>,
    val removed: Set<ProtocolId>,
) : EventType()
