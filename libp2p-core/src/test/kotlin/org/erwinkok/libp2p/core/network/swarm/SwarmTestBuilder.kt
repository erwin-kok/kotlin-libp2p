// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network.swarm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.erwinkok.libp2p.core.base.EpochTimeProvider
import org.erwinkok.libp2p.core.datastore.MapDatastore
import org.erwinkok.libp2p.core.event.EventBus
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.connectiongater.ConnectionGater
import org.erwinkok.libp2p.core.network.securitymuxer.SecurityMuxer
import org.erwinkok.libp2p.core.network.streammuxer.StreamMuxer
import org.erwinkok.libp2p.core.network.upgrader.Upgrader
import org.erwinkok.libp2p.core.peerstore.Peerstore
import org.erwinkok.libp2p.core.resourcemanager.NullResourceManager
import org.erwinkok.libp2p.crypto.CryptoUtil
import org.erwinkok.libp2p.crypto.KeyType
import org.erwinkok.libp2p.crypto.PrivateKey
import org.erwinkok.libp2p.muxer.mplex.MplexStreamMuxerTransport
import org.erwinkok.libp2p.security.plaintext.PlainTextSecureTransport
import org.erwinkok.libp2p.transport.tcp.TcpTransport
import org.erwinkok.result.expectNoErrors

class SwarmTestConfig(
    var DisableReuseport: Boolean = false,
    var DialOnly: Boolean = false,
    var DisableTcp: Boolean = false,
    var DisableQuic: Boolean = false,
    var ConnectionGater: ConnectionGater? = null,
    var PrivateKey: PrivateKey? = null,
    var EventBus: EventBus? = null,
    var TimeProvider: EpochTimeProvider = EpochTimeProvider.test,
)

object SwarmTestBuilder {
    suspend fun create(scope: CoroutineScope, config: SwarmTestConfig = SwarmTestConfig()): Swarm {
        val pk = config.PrivateKey
        val privateKey = if (pk != null) {
            pk
        } else {
            val pair = CryptoUtil.generateKeyPair(KeyType.ED25519).expectNoErrors()
            pair.privateKey
        }
        val localIdentity = LocalIdentity.fromPrivateKey(privateKey).expectNoErrors()
        val peerstore = Peerstore.create(scope, MapDatastore(scope), null, config.TimeProvider).expectNoErrors()
        val eventBus = config.EventBus ?: EventBus()

        val swarmConfig = SwarmConfig()
        swarmConfig.connectionGater = config.ConnectionGater

        peerstore.addLocalIdentity(localIdentity).expectNoErrors()
        val swarm = Swarm(
            scope,
            localIdentity.peerId,
            peerstore,
            eventBus,
            swarmConfig,
        )
        val upgrader = createUpgrader(scope, localIdentity, config)
        if (!config.DisableTcp) {
            val transport = TcpTransport.create(upgrader, NullResourceManager, Dispatchers.IO).expectNoErrors()
            swarm.addTransport(transport)
            if (!config.DialOnly) {
                val listenAddress = InetMultiaddress.fromString("/ip4/127.0.0.1/tcp/0").expectNoErrors()
                swarm.addListener(listenAddress)
            }
        }
        return swarm
    }

    private fun createUpgrader(scope: CoroutineScope, localIdentity: LocalIdentity, config: SwarmTestConfig): Upgrader {
        val securityMuxer = SecurityMuxer()
        val plainText = PlainTextSecureTransport.create(scope, localIdentity).expectNoErrors()
        securityMuxer.addTransport(PlainTextSecureTransport.protocolId, plainText)
        val streamMuxer = StreamMuxer()
        val mplexMuxer = MplexStreamMuxerTransport.create(scope).expectNoErrors()
        streamMuxer.addStreamMuxerTransport(MplexStreamMuxerTransport.protocolId, mplexMuxer)
        return Upgrader(securityMuxer, streamMuxer, config.ConnectionGater, NullResourceManager)
    }
}
