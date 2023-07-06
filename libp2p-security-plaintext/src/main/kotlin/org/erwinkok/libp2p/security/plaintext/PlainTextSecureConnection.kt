// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.security.plaintext

import io.ktor.utils.io.core.internal.ChunkBuffer
import io.ktor.utils.io.pool.ObjectPool
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.host.RemoteIdentity
import org.erwinkok.libp2p.core.network.Connection
import org.erwinkok.libp2p.core.network.securitymuxer.SecureConnection

class PlainTextSecureConnection(
    private val insecureConnection: Connection,
    override val localIdentity: LocalIdentity,
    override val remoteIdentity: RemoteIdentity,
    override val pool: ObjectPool<ChunkBuffer> = insecureConnection.pool,
) : SecureConnection, Connection by insecureConnection
