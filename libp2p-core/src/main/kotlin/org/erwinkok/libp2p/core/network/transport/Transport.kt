// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.network.transport

import io.ktor.utils.io.core.Closeable
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.multiformat.multiaddress.Protocol
import org.erwinkok.result.Result

interface Transport : Closeable {
    val proxy: Boolean
    val protocols: List<Protocol>
    fun canDial(remoteAddress: InetMultiaddress): Boolean
    suspend fun dial(peerId: PeerId, remoteAddress: InetMultiaddress): Result<TransportConnection>
    fun listen(bindAddress: InetMultiaddress): Result<Listener>
}
