// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network.swarm

import kotlinx.coroutines.channels.Channel
import org.erwinkok.result.Result

internal class DialRequest {
    private val responseChannel = Channel<Result<SwarmConnection>>(Channel.RENDEZVOUS)

    suspend fun setResponse(response: Result<SwarmConnection>) {
        responseChannel.send(response)
    }

    suspend fun getResponse(): Result<SwarmConnection> {
        return responseChannel.receive()
    }
}
