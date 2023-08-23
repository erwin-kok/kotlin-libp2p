// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.event

import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.Connectedness

data class EvtPeerConnectednessChanged(
    val peerId: PeerId,
    val connectedness: Connectedness
) : EventType()
