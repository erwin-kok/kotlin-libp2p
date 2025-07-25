// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.event

import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.result.Error

data class EvtPeerIdentificationFailed(
    val peerId: PeerId,
    val reason: Error,
) : EventType()
