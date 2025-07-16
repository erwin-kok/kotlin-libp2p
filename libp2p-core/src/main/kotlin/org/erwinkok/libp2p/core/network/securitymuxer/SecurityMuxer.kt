// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.network.securitymuxer

import io.github.oshai.kotlinlogging.KotlinLogging
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.Connection
import org.erwinkok.libp2p.core.network.Direction
import org.erwinkok.multiformat.multistream.MultistreamMuxer
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.flatMap
import org.erwinkok.result.map

private val logger = KotlinLogging.logger {}

data class SecureMuxerInfo(
    val secureConnection: SecureConnection,
    val direction: Direction,
)

class SecurityMuxer {
    private val multistreamMuxer = MultistreamMuxer<Connection>()
    private val secureTransports = mutableMapOf<ProtocolId, SecureTransport>()
    fun addTransport(protocol: ProtocolId, transport: SecureTransport) {
        secureTransports[protocol] = transport
        multistreamMuxer.addHandler(protocol)
    }

    suspend fun secureInbound(connection: Connection, peerId: PeerId?): Result<SecureMuxerInfo> {
        return multistreamMuxer.negotiate(connection)
            .flatMap { getSecureTransport(it.protocol) }
            .flatMap { secureTransport -> secureTransport.secureInbound(connection, peerId) }
            .map { secureConnection -> SecureMuxerInfo(secureConnection, Direction.DirInbound) }
    }

    suspend fun secureOutbound(connection: Connection, peerId: PeerId): Result<SecureMuxerInfo> {
        return MultistreamMuxer.selectWithSimopenOrFail(secureTransports.keys, connection)
            .map { simOpenInfo ->
                return getSecureTransport(simOpenInfo.protocol)
                    .map { secureTransport ->
                        return secureTransport.secureOutbound(connection, peerId)
                            .map { secureConnection ->
                                if (secureConnection.remoteIdentity.peerId != peerId) {
                                    secureConnection.close()
                                    logger.warn { "Handshake failed to properly authenticate peer. Authenticated ${secureConnection.remoteIdentity.peerId}, expected $peerId." }
                                    return Err("")
                                }
                                val direction = if (simOpenInfo.server) Direction.DirInbound else Direction.DirOutbound
                                return Ok(SecureMuxerInfo(secureConnection, direction))
                            }
                    }
            }
    }

    private fun getSecureTransport(protocol: ProtocolId): Result<SecureTransport> {
        val handler = secureTransports[protocol] ?: return Err("No SecureTransport registered for $protocol")
        return Ok(handler)
    }
}
