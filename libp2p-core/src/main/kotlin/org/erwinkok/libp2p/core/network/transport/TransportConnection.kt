// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network.transport

import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.network.ConnectionMultiaddress
import org.erwinkok.libp2p.core.network.ConnectionStatistic
import org.erwinkok.libp2p.core.network.securitymuxer.ConnectionSecurity
import org.erwinkok.libp2p.core.network.streammuxer.StreamMuxerConnection
import org.erwinkok.libp2p.core.resourcemanager.ConnectionManagementScope

interface TransportConnection : ConnectionSecurity, ConnectionMultiaddress, ConnectionStatistic, StreamMuxerConnection, AwaitableClosable {
    val connectionScope: ConnectionManagementScope
    val transport: Transport
}
