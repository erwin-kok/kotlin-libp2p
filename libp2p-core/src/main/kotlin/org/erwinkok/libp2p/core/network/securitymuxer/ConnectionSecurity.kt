// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.network.securitymuxer

import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.host.RemoteIdentity

interface ConnectionSecurity {
    val localIdentity: LocalIdentity
    val remoteIdentity: RemoteIdentity
}
