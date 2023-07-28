// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.security.plaintext

import kotlinx.coroutines.CoroutineScope
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.Connection
import org.erwinkok.libp2p.core.network.securitymuxer.SecureConnection
import org.erwinkok.libp2p.core.network.securitymuxer.SecureTransport
import org.erwinkok.libp2p.core.network.securitymuxer.SecureTransportFactory
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.getOrElse

class PlainTextSecureTransport private constructor(
    override val localIdentity: LocalIdentity,
) : SecureTransport {
    override suspend fun secureInbound(insecureConnection: Connection, peerId: PeerId?): Result<SecureConnection> {
        val remoteIdentity = PlainTextHandshaker(insecureConnection, localIdentity).runHandshake()
            .getOrElse { return Err(it) }
        if (peerId != null && peerId != remoteIdentity.peerId) {
            return Err("Remote peer sent unexpected PeerId. expected=$peerId received=${remoteIdentity.peerId}")
        }
        return Ok(
            PlainTextSecureConnection(
                insecureConnection,
                localIdentity,
                remoteIdentity,
            ),
        )
    }

    override suspend fun secureOutbound(insecureConnection: Connection, peerId: PeerId): Result<SecureConnection> {
        val remoteIdentity = PlainTextHandshaker(insecureConnection, localIdentity).runHandshake()
            .getOrElse { return Err(it) }
        if (peerId != remoteIdentity.peerId) {
            return Err("Remote peer sent unexpected PeerId. expected=$peerId received=${remoteIdentity.peerId}")
        }
        return Ok(
            PlainTextSecureConnection(
                insecureConnection,
                localIdentity,
                remoteIdentity,
            ),
        )
    }

    companion object PlainTextSecureTransportFactory : SecureTransportFactory {
        override val protocolId: ProtocolId
            get() = ProtocolId.of("/plaintext/2.0.0")

        override fun create(scope: CoroutineScope, localIdentity: LocalIdentity): Result<SecureTransport> {
            return Ok(PlainTextSecureTransport(localIdentity))
        }
    }
}
