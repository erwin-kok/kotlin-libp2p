// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.security.noise

import kotlinx.coroutines.CoroutineScope
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.Connection
import org.erwinkok.libp2p.core.network.Direction
import org.erwinkok.libp2p.core.network.securitymuxer.SecureConnection
import org.erwinkok.libp2p.core.network.securitymuxer.SecureTransport
import org.erwinkok.libp2p.core.network.securitymuxer.SecureTransportFactory
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.map

class NoiseTransport(
    private val scope: CoroutineScope,
    override val localIdentity: LocalIdentity,
) : SecureTransport {
    override suspend fun secureInbound(insecureConnection: Connection, peerId: PeerId?): Result<SecureConnection> {
        return newSecureSession(insecureConnection, peerId, Direction.DirInbound)
    }

    override suspend fun secureOutbound(insecureConnection: Connection, peerId: PeerId): Result<SecureConnection> {
        return newSecureSession(insecureConnection, peerId, Direction.DirOutbound)
    }

    private suspend fun newSecureSession(insecureConnection: Connection, peerId: PeerId?, direction: Direction): Result<SecureConnection> {
        return NoiseHandshaker(insecureConnection, localIdentity, peerId, direction).runHandshake()
            .map { (remoteIdentity, receiverCipherState, senderCipherState) ->
                NoiseSecureConnection(
                    scope,
                    insecureConnection,
                    receiverCipherState,
                    senderCipherState,
                    localIdentity,
                    remoteIdentity,
                )
            }
    }

    companion object NoiseSecureTransportFactory : SecureTransportFactory {
        override val protocolId: ProtocolId
            get() = ProtocolId.from("/noise")

        override fun create(scope: CoroutineScope, localIdentity: LocalIdentity): Result<SecureTransport> {
            return Ok(NoiseTransport(scope, localIdentity))
        }
    }
}
