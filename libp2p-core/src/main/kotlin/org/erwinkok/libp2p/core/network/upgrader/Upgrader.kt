// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network.upgrader

import mu.KotlinLogging
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.Connection
import org.erwinkok.libp2p.core.network.Direction
import org.erwinkok.libp2p.core.network.MultiaddressConnection
import org.erwinkok.libp2p.core.network.connectiongater.ConnectionGater
import org.erwinkok.libp2p.core.network.securitymuxer.SecureMuxerInfo
import org.erwinkok.libp2p.core.network.securitymuxer.SecurityMuxer
import org.erwinkok.libp2p.core.network.streammuxer.StreamMuxer
import org.erwinkok.libp2p.core.network.streammuxer.StreamMuxerConnection
import org.erwinkok.libp2p.core.network.transport.Transport
import org.erwinkok.libp2p.core.resourcemanager.ConnectionManagementScope
import org.erwinkok.libp2p.core.resourcemanager.PeerScope
import org.erwinkok.libp2p.core.resourcemanager.ResourceManager
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.erwinkok.result.getOrElse
import org.erwinkok.result.onFailure

private val logger = KotlinLogging.logger {}

class Upgrader(
    private val securityMuxer: SecurityMuxer,
    private val multiplexer: StreamMuxer,
    private val connectionGater: ConnectionGater?,
    private val resourceManager: ResourceManager,
) {
    suspend fun upgradeOutbound(transport: Transport, connection: MultiaddressConnection, dir: Direction, peerId: PeerId?, scope: ConnectionManagementScope): Result<UpgradedTransportConnection> {
        if (peerId == null) {
            connection.close()
            return Err("The provided peerId is null")
        }
        return upgrade(transport, connection, Direction.DirOutbound, peerId, scope)
            .onFailure { scope.done() }
    }

    suspend fun upgradeInbound(transport: Transport, connection: MultiaddressConnection): Result<UpgradedTransportConnection> {
        if (connectionGater != null && !connectionGater.interceptAccept(connection)) {
            logger.debug { "Gater blocked incoming connection on local address ${connection.localAddress} from ${connection.remoteAddress}" }
            connection.close()
            return Err("Gater blocked incoming connection")
        }
        val scope = resourceManager.openConnection(Direction.DirInbound, true, connection.remoteAddress)
            .getOrElse {
                logger.debug { "resource manager blocked accept of new connection: ${errorMessage(it)}" }
                connection.close()
                return Err("resource manager blocked accept of new connection")
            }
        return upgrade(transport, connection, Direction.DirInbound, null, scope)
            .onFailure { scope.done() }
    }

    private suspend fun upgrade(transport: Transport, connection: MultiaddressConnection, direction: Direction, peerId: PeerId?, scope: ConnectionManagementScope): Result<UpgradedTransportConnection> {
        val secureMuxerInfo = if (direction == Direction.DirInbound) {
            secureInbound(connection, peerId)
                .getOrElse {
                    connection.close()
                    return Err("failed to negotiate security protocol: ${errorMessage(it)}")
                }
        } else {
            if (peerId == null) {
                connection.close()
                return Err("Must provide PeerId for Outbound connection")
            }
            secureOutbound(connection, peerId)
                .getOrElse {
                    connection.close()
                    return Err("failed to negotiate security protocol: ${errorMessage(it)}")
                }
        }
        if (connectionGater != null && !connectionGater.interceptSecured(direction, secureMuxerInfo.secureConnection.remoteIdentity.peerId, connection)) {
            secureMuxerInfo.secureConnection.close()
            connection.close()
            return Err("Gater rejected connection with peer ${secureMuxerInfo.secureConnection.remoteIdentity.peerId} and address ${connection.remoteAddress} with direction $direction")
        }
        if (scope.peerScope == null) {
            scope.setPeer(secureMuxerInfo.secureConnection.remoteIdentity.peerId)
                .getOrElse {
                    val message = "resource manager blocked connection for peer. peer=${secureMuxerInfo.secureConnection.remoteIdentity.peerId}, address=${connection.remoteAddress}, direction $direction, error=${errorMessage(it)}"
                    logger.debug { message }
                    secureMuxerInfo.secureConnection.close()
                    connection.close()
                    return Err(message)
                }
        }
        val initiator = secureMuxerInfo.direction == Direction.DirOutbound
        val muxedConnection = setupMuxer(secureMuxerInfo.secureConnection, initiator, scope.peerScope)
            .getOrElse {
                secureMuxerInfo.secureConnection.close()
                connection.close()
                return Err("failed to negotiate stream multiplexer: ${errorMessage(it)}")
            }
        return Ok(
            UpgradedTransportConnection(
                muxedConnection,
                transport,
                scope,
                connection.localAddress,
                connection.remoteAddress,
                secureMuxerInfo.secureConnection.localIdentity,
                secureMuxerInfo.secureConnection.remoteIdentity,
            ),
        )
    }

    private suspend fun secureOutbound(connection: Connection, peerId: PeerId): Result<SecureMuxerInfo> {
        return securityMuxer.secureOutbound(connection, peerId)
    }

    private suspend fun secureInbound(connection: Connection, peerId: PeerId?): Result<SecureMuxerInfo> {
        return securityMuxer.secureInbound(connection, peerId)
    }

    private suspend fun setupMuxer(connection: Connection, initiator: Boolean, scope: PeerScope): Result<StreamMuxerConnection> {
        return multiplexer.newConnection(connection, initiator, scope)
    }
}
