// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.yamux

import kotlinx.coroutines.CoroutineScope
import org.erwinkok.libp2p.core.network.Connection
import org.erwinkok.libp2p.core.network.streammuxer.StreamMuxerConnection
import org.erwinkok.libp2p.core.network.streammuxer.StreamMuxerTransport
import org.erwinkok.libp2p.core.network.streammuxer.StreamMuxerTransportFactory
import org.erwinkok.libp2p.core.resourcemanager.PeerScope
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.getOrElse
import org.erwinkok.result.onFailure
import kotlin.time.Duration

class YamuxStreamMuxerTransport private constructor(
    private val coroutineScope: CoroutineScope,
) : StreamMuxerTransport {
    private val config = YamuxConfig(
        maxIncomingStreams = Int.MAX_VALUE,
        initialStreamWindowSize = YamuxConst.initialStreamWindow,
    )

    override suspend fun newConnection(connection: Connection, initiator: Boolean, scope: PeerScope?): Result<StreamMuxerConnection> {
        val span = if (scope != null) {
            { scope.beginSpan() }
        } else {
            null
        }
        val session = if (initiator) {
            verifyConfig(config).onFailure {
                return Err(it)
            }
            Ok(Session(coroutineScope, config, connection, false, span))
                .getOrElse { return Err(it) }
        } else {
            verifyConfig(config).onFailure {
                return Err(it)
            }
            Ok(Session(coroutineScope, config, connection, true, span))
                .getOrElse { return Err(it) }
        }
        return Ok(YamuxStreamMuxerConnection(session))
    }

    private fun verifyConfig(config: YamuxConfig): Result<Unit> {
        if (config.acceptBacklog <= 0) {
            return Err("backlog must be positive")
        }
        if (config.enableKeepAlive && config.keepAliveInterval == Duration.ZERO) {
            return Err("keep-alive interval must be positive")
        }
        if (config.measureRTTInterval == Duration.ZERO) {
            return Err("measure-rtt interval must be positive")
        }

        if (config.initialStreamWindowSize < YamuxConst.initialStreamWindow) {
            return Err("InitialStreamWindowSize must be larger or equal 256 kB")
        }
        if (config.maxStreamWindowSize < config.initialStreamWindowSize) {
            return Err("MaxStreamWindowSize must be larger than the InitialStreamWindowSize")
        }
        if (config.maxMessageSize < 1024) {
            return Err("MaxMessageSize must be greater than a kilobyte")
        }
        if (config.writeCoalesceDelay < Duration.ZERO) {
            return Err("WriteCoalesceDelay must be >= 0")
        }
        if (config.pingBacklog < 1) {
            return Err("PingBacklog must be > 0")
        }
        return Ok(Unit)
    }

    companion object YamuxStreamMuxerTransportFactory : StreamMuxerTransportFactory {
        override val protocolId: ProtocolId
            get() = ProtocolId.of("/yamux/1.0.0")

        override fun create(scope: CoroutineScope): Result<StreamMuxerTransport> {
            return Ok(YamuxStreamMuxerTransport(scope))
        }
    }
}
