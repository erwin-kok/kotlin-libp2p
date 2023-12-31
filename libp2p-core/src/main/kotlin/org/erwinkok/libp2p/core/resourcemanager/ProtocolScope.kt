// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.resourcemanager

import org.erwinkok.multiformat.multistream.ProtocolId

interface ProtocolScope : ResourceScope {
    val protocol: ProtocolId
}
