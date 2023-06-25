// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.network.transport

import io.ktor.network.sockets.SocketAddress
import io.ktor.utils.io.core.Closeable
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.result.Result

interface Listener : Closeable {
    val socketAddress: SocketAddress
    val transportAddress: InetMultiaddress
    suspend fun accept(): Result<TransportConnection>
}
