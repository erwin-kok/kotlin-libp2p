// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.security.noise

import com.southernstorm.noise.protocol.CipherState
import org.erwinkok.libp2p.core.host.RemoteIdentity

data class HandshakeInfo(
    val remoteIdentity: RemoteIdentity,
    var receiverCipherState: CipherState,
    var senderCipherState: CipherState,
)
