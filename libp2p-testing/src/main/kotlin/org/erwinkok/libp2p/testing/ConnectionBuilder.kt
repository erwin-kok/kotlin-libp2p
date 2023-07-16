// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.testing

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.network.securitymuxer.SecureTransportFactory
import org.erwinkok.libp2p.core.network.securitymuxer.SecurityMuxer
import org.erwinkok.libp2p.core.network.streammuxer.StreamMuxer
import org.erwinkok.libp2p.core.network.streammuxer.StreamMuxerTransportFactory
import org.erwinkok.libp2p.core.network.transport.Transport
import org.erwinkok.libp2p.core.network.transport.TransportFactory
import org.erwinkok.libp2p.core.network.upgrader.Upgrader
import org.erwinkok.libp2p.core.resourcemanager.NullResourceManager
import org.erwinkok.libp2p.crypto.KeyType
import org.erwinkok.result.expectNoErrors

data class ConnectionInfo(
    val identity: LocalIdentity,
    val transport: Transport,
)

class ConnectionBuilder(
    private val transportBuilder: TransportFactory,
    private val securityTransportBuilder: SecureTransportFactory,
    private val streamMuxerBuilder: StreamMuxerTransportFactory,
) {
    val identity = createIdentity()

    fun build(scope: CoroutineScope, dispatcher: CoroutineDispatcher): ConnectionInfo {
        val multiplexerMuxer = createMultiplexerMuxer(scope)
        val securityMuxer = createSecurityMuxer(scope, identity)
        val upgrader = Upgrader(securityMuxer, multiplexerMuxer, null, NullResourceManager)
        val transport = transportBuilder.create(upgrader, NullResourceManager, dispatcher).expectNoErrors()
        return ConnectionInfo(identity, transport)
    }

    private fun createIdentity(): LocalIdentity {
        return LocalIdentity.random(KeyType.ED25519, 256).expectNoErrors()
    }

    private fun createMultiplexerMuxer(coroutineScope: CoroutineScope): StreamMuxer {
        val streamMuxer = StreamMuxer()
        val multiplexer = streamMuxerBuilder.create(coroutineScope).expectNoErrors()
        streamMuxer.addStreamMuxerTransport(streamMuxerBuilder.protocolId, multiplexer)
        return streamMuxer
    }

    private fun createSecurityMuxer(scope: CoroutineScope, identity: LocalIdentity): SecurityMuxer {
        val secureMuxer = SecurityMuxer()
        val transport = securityTransportBuilder.create(scope, identity).expectNoErrors()
        secureMuxer.addTransport(securityTransportBuilder.protocolId, transport)
        return secureMuxer
    }
}
