// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.yamux

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.SelectClause1
import org.erwinkok.result.Err
import org.erwinkok.result.Result

class Ping(val id: Long) {
    private val done = Channel<Unit>(Channel.RENDEZVOUS)
    private var result: Result<Long>? = null
    private val pingResponse = Channel<Unit>(1)

    val onWait: SelectClause1<Unit>
        get() = pingResponse.onReceive

    fun trigger() {
        // If the response was already triggered, don't bother.
        pingResponse.trySend(Unit)
    }

    suspend fun finish(value: Result<Long>) {
        result = value
        done.send(Unit)
    }

    suspend fun wait(): Result<Long> {
        done.receive()
        return result ?: Err("wait called before finish")
    }
}