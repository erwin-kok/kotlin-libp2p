// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.security.plaintext

import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.host.RemoteIdentity
import org.erwinkok.libp2p.core.network.Connection
import org.erwinkok.libp2p.core.network.securitymuxer.SecureConnection

class PlainTextSecureConnection(
    private val insecureConnection: Connection,
    override val localIdentity: LocalIdentity,
    override val remoteIdentity: RemoteIdentity,
) : SecureConnection, Connection by insecureConnection
