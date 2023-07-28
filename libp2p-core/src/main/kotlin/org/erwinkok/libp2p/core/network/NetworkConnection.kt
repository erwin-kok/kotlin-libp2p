// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network

import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.network.securitymuxer.ConnectionSecurity
import org.erwinkok.result.Result

interface NetworkConnection : AwaitableClosable, ConnectionSecurity, ConnectionMultiaddress, ConnectionStatistic, ConnectionScoper {
    val id: String
    val streams: List<Stream>
    suspend fun newStream(name: String? = null): Result<Stream>
}
