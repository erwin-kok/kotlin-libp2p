// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network.upgrader

import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.host.RemoteIdentity
import org.erwinkok.libp2p.core.network.ConnectionStatistics
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.streammuxer.StreamMuxerConnection
import org.erwinkok.libp2p.core.network.transport.Transport
import org.erwinkok.libp2p.core.network.transport.TransportConnection
import org.erwinkok.libp2p.core.resourcemanager.ConnectionManagementScope

class UpgradedTransportConnection(
    private val streamMuxerConnection: StreamMuxerConnection,
    override val transport: Transport,
    override val connectionScope: ConnectionManagementScope,
    override val localAddress: InetMultiaddress,
    override val remoteAddress: InetMultiaddress,
    override val localIdentity: LocalIdentity,
    override val remoteIdentity: RemoteIdentity,
) : TransportConnection, StreamMuxerConnection by streamMuxerConnection {
    override val statistic = ConnectionStatistics()

    override fun close() {
        connectionScope.done()
        streamMuxerConnection.close()
    }

    override fun toString(): String {
        return "<UpgradedTransportConnection[$transport] $localAddress ($localIdentity) <-> $remoteAddress ($remoteIdentity)>"
    }
}
