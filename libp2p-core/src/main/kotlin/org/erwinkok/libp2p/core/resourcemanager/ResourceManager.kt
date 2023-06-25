// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.resourcemanager

import io.ktor.utils.io.core.Closeable
import org.erwinkok.libp2p.core.PeerId
import org.erwinkok.libp2p.core.network.Direction
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.result.Result

interface ResourceManager : ResourceScopeViewer, Closeable {
    fun openConnection(dir: Direction, usefd: Boolean, endpoint: InetMultiaddress): Result<ConnectionManagementScope>
    fun openStream(peerId: PeerId, dir: Direction): Result<StreamManagementScope>
}
