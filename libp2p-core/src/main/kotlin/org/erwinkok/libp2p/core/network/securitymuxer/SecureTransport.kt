// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.network.securitymuxer

import org.erwinkok.libp2p.core.PeerId
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.network.Connection
import org.erwinkok.result.Result

interface SecureTransport {
    val localIdentity: LocalIdentity
    suspend fun secureInbound(insecureConnection: Connection, peerId: PeerId?): Result<SecureConnection>
    suspend fun secureOutbound(insecureConnection: Connection, peerId: PeerId): Result<SecureConnection>
}
