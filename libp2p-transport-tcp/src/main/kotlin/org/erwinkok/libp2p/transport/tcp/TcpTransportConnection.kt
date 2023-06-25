// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.transport.tcp

import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.close
import io.ktor.utils.io.core.internal.ChunkBuffer
import io.ktor.utils.io.pool.ObjectPool
import kotlinx.coroutines.Job
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.MultiaddressConnection

class TcpTransportConnection(
    private val socket: Socket,
    override val localAddress: InetMultiaddress,
    override val remoteAddress: InetMultiaddress,
    override val pool: ObjectPool<ChunkBuffer>,
) : MultiaddressConnection {
    override val jobContext = Job()
    override val input = socket.openReadChannel()
    override val output = socket.openWriteChannel()
    override fun close() {
        input.cancel()
        output.close()
        socket.close()
    }
}
