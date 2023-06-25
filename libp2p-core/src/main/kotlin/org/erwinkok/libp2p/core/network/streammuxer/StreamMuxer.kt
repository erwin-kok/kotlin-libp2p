// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.network.streammuxer

import kotlinx.coroutines.withTimeoutOrNull
import org.erwinkok.libp2p.core.network.Connection
import org.erwinkok.libp2p.core.resourcemanager.PeerScope
import org.erwinkok.multiformat.multistream.MultistreamMuxer
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.Err
import org.erwinkok.result.Error
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.flatMap
import kotlin.time.Duration.Companion.seconds

class StreamMuxer {
    private val defaultNegotiateTimeout = 60.seconds
    private val errNegotiateTimeout = Error("timeout negotiating")

    private val multistreamMuxer = MultistreamMuxer<Connection>()
    private val muxers = mutableMapOf<ProtocolId, StreamMuxerTransport>()
    private val negotiateTimeout = defaultNegotiateTimeout

    fun addStreamMuxerTransport(protocol: ProtocolId, muxer: StreamMuxerTransport) {
        muxers[protocol] = muxer
        multistreamMuxer.addHandler(protocol)
    }

    suspend fun newConnection(connection: Connection, initiator: Boolean, scope: PeerScope): Result<StreamMuxerConnection> {
        return if (initiator) {
            handleOutbound(connection, scope)
        } else {
            handleInbound(connection, scope)
        }
    }

    private suspend fun handleOutbound(connection: Connection, scope: PeerScope): Result<StreamMuxerConnection> {
        val muxerConnection =
            withTimeoutOrNull(negotiateTimeout) {
                MultistreamMuxer.selectOneOf(muxers.keys, connection)
                    .flatMap { getMuxer(it) }
                    .flatMap { it.newConnection(connection, true, scope) }
            }
        return muxerConnection ?: Err(errNegotiateTimeout)
    }

    private suspend fun handleInbound(connection: Connection, scope: PeerScope): Result<StreamMuxerConnection> {
        val muxerConnection =
            withTimeoutOrNull(negotiateTimeout) {
                multistreamMuxer.negotiate(connection)
                    .flatMap { getMuxer(it.protocol) }
                    .flatMap { it.newConnection(connection, false, scope) }
            }
        return muxerConnection ?: Err(errNegotiateTimeout)
    }

    private fun getMuxer(protocol: ProtocolId): Result<StreamMuxerTransport> {
        val muxer = muxers[protocol] ?: return Err("No Muxer registered for $protocol")
        return Ok(muxer)
    }
}
