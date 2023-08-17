// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.host.builder

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import mu.KotlinLogging
import org.erwinkok.libp2p.core.datastore.BatchingDatastore
import org.erwinkok.libp2p.core.datastore.Key.Companion.key
import org.erwinkok.libp2p.core.datastore.MapDatastore
import org.erwinkok.libp2p.core.event.EventBus
import org.erwinkok.libp2p.core.host.BasicHost
import org.erwinkok.libp2p.core.host.Host
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.Network
import org.erwinkok.libp2p.core.network.Stream
import org.erwinkok.libp2p.core.network.connectiongater.ConnectionGater
import org.erwinkok.libp2p.core.network.securitymuxer.SecureTransportFactory
import org.erwinkok.libp2p.core.network.securitymuxer.SecurityMuxer
import org.erwinkok.libp2p.core.network.streammuxer.StreamMuxer
import org.erwinkok.libp2p.core.network.streammuxer.StreamMuxerTransportFactory
import org.erwinkok.libp2p.core.network.swarm.Swarm
import org.erwinkok.libp2p.core.network.transport.TransportFactory
import org.erwinkok.libp2p.core.network.upgrader.Upgrader
import org.erwinkok.libp2p.core.peerstore.Peerstore
import org.erwinkok.libp2p.core.resourcemanager.NullResourceManager
import org.erwinkok.libp2p.core.resourcemanager.ResourceManager
import org.erwinkok.libp2p.crypto.KeyType
import org.erwinkok.multiformat.multistream.MultistreamMuxer
import org.erwinkok.result.CombinedError
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.flatMap
import org.erwinkok.result.getOr
import org.erwinkok.result.getOrElse
import org.erwinkok.result.map
import org.erwinkok.result.onFailure
import org.erwinkok.result.onSuccess
import org.reflections.Reflections

private val logger = KotlinLogging.logger {}

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
annotation class HostDsl

@HostDsl
class HostBuilder {
    private val reflections = Reflections("org.erwinkok")
    val errors = CombinedError()
    val config = HostConfig()

    var enablePing by config::enablePing

    @HostDsl
    fun identity(localIdentity: LocalIdentity) {
        if (config.localIdentity != null) {
            errors.recordError { "cannot specify multiple identities" }
        } else {
            config.localIdentity = localIdentity
        }
    }

    @HostDsl
    fun muxers(init: MuxersBuilder.() -> Unit) {
        MuxersBuilder(this).apply(init)
    }

    @HostDsl
    fun securityTransport(init: SecurityTransportBuilder.() -> Unit) {
        SecurityTransportBuilder(this).apply(init)
    }

    @HostDsl
    fun transports(init: TransportsBuilder.() -> Unit) {
        TransportsBuilder(this).apply(init)
    }

    @HostDsl
    fun peerstore(init: PeerstoreBuilder.() -> Unit) {
        PeerstoreBuilder(this).apply(init)
    }

    @HostDsl
    fun swarm(init: SwarmBuilder.() -> Unit) {
        SwarmBuilder(this).apply(init)
    }

    @HostDsl
    fun datastore(datastore: BatchingDatastore) {
        if (config.datastore != null) {
            errors.recordError { "cannot specify multiple datastores" }
        } else {
            config.datastore = datastore
        }
    }

    @HostDsl
    fun connectionGater(connectionGater: ConnectionGater) {
        if (config.connectionGater != null) {
            errors.recordError { "cannot specify multiple connection gaters, or cannot configure both Filters and connection gater" }
        } else {
            config.connectionGater = connectionGater
        }
    }

    @HostDsl
    fun resourceManager(resourceManager: ResourceManager) {
        if (config.resourceManager != null) {
            errors.recordError { "cannot specify multiple resource managers" }
        } else {
            config.resourceManager = resourceManager
        }
    }

    @HostDsl
    fun noSecurityTransport() {
        if (config.securityTransportFactories.isNotEmpty()) {
            errors.recordError { "cannot disable security transport with secure transports configured" }
        } else {
            config.insecure = true
        }
    }

    suspend fun build(coroutineScope: CoroutineScope): Result<Host> {
        if (errors.hasErrors) {
            return Err(errors.error())
        }

        val datastore = createDatastore(coroutineScope)
            .getOrElse { return Err(it) }
        val peerstore = Peerstore.create(coroutineScope, datastore, config.peerstoreConfig)
            .getOrElse { return Err(it) }
        val localIdentity = createIdentity(peerstore)
            .getOrElse { return Err(it) }

        peerstore.addLocalIdentity(localIdentity)
            .onFailure { errors.recordError(it) }

        val eventbus = EventBus()
        val multistreamMuxer = MultistreamMuxer<Stream>()

        val swarmBuilder = Swarm.Builder(
            eventbus,
            localIdentity.peerId,
            peerstore,
            multistreamMuxer,
        )

        val connectionGater = config.connectionGater
        if (connectionGater != null) {
            swarmBuilder.withConnectionGater(connectionGater)
        }

        val resourceManager = config.resourceManager
        if (resourceManager != null) {
            swarmBuilder.withResourceManager(resourceManager)
        }

        swarmBuilder.withSwarmConfig(config.swarmConfig)

        val swarm = swarmBuilder.build(coroutineScope)
            .getOrElse { return Err(it) }

        val host = BasicHost(
            coroutineScope,
            localIdentity,
            config,
            swarm,
            peerstore,
            multistreamMuxer,
            eventbus,
        )

        val upgrader = createUpgrader(coroutineScope, localIdentity, NullResourceManager)
        addTransports(swarm, NullResourceManager, upgrader, Dispatchers.IO)
        addListenAddresses(swarm)

        return if (errors.hasErrors) {
            Err(errors.error())
        } else {
            Ok(host)
        }
    }

    private fun createDatastore(coroutineScope: CoroutineScope): Result<BatchingDatastore> {
        return Ok(config.datastore ?: MapDatastore(coroutineScope))
    }

    private suspend fun createIdentity(peerstore: Peerstore): Result<LocalIdentity> {
        var localIdentity = config.localIdentity
        if (localIdentity == null) {
            val datastore = config.datastore
            if (datastore != null) {
                localIdentity = datastore.get(key("self-peerid"))
                    .flatMap { PeerId.fromBytes(it) }
                    .map { peerstore.localIdentity(it) }
                    .getOr(null)
            }
        }
        if (localIdentity != null) {
            return Ok(localIdentity)
        }
        logger.warn { "No Identity specified and could not be retrieved from Datastore. Creating random identity." }
        return LocalIdentity.random(KeyType.ED25519, 2048)
    }

    private fun createSecurityMuxer(coroutineScope: CoroutineScope, localIdentity: LocalIdentity): SecurityMuxer {
        val secureMuxer = SecurityMuxer()
        if (config.insecure) {
            logger.warn { "Insecure transport selected!" }
        } else {
            if (config.securityTransportFactories.isNotEmpty()) {
                for (securityTransportFactory in config.securityTransportFactories) {
                    securityTransportFactory.create(coroutineScope, localIdentity)
                        .onFailure { errors.recordError(it) }
                        .onSuccess { secureMuxer.addTransport(securityTransportFactory.protocolId, it) }
                }
            } else {
                val subTypes = getFactory<SecureTransportFactory>()
                for (objectInstance in subTypes) {
                    objectInstance.create(coroutineScope, localIdentity)
                        .onFailure { errors.recordError(it) }
                        .onSuccess { secureMuxer.addTransport(objectInstance.protocolId, it) }
                }
            }
        }
        return secureMuxer
    }

    private fun createStreamMuxer(coroutineScope: CoroutineScope): StreamMuxer {
        val streamMuxer = StreamMuxer()
        if (config.streamMuxerTransportFactories.isNotEmpty()) {
            for (streamMuxerTransportFactory in config.streamMuxerTransportFactories) {
                streamMuxerTransportFactory.create(coroutineScope)
                    .onSuccess { streamMuxer.addStreamMuxerTransport(streamMuxerTransportFactory.protocolId, it) }
                    .onFailure { errors.recordError(it) }
            }
        } else {
            val subTypes = getFactory<StreamMuxerTransportFactory>()
            for (objectInstance in subTypes) {
                objectInstance.create(coroutineScope)
                    .onFailure { errors.recordError(it) }
                    .onSuccess { streamMuxer.addStreamMuxerTransport(objectInstance.protocolId, it) }
            }
        }
        return streamMuxer
    }

    private fun createUpgrader(coroutineScope: CoroutineScope, localIdentity: LocalIdentity, resourceManager: ResourceManager): Upgrader {
        val securityMuxer = createSecurityMuxer(coroutineScope, localIdentity)
        val multiplexer = createStreamMuxer(coroutineScope)
        return Upgrader(securityMuxer, multiplexer, config.connectionGater, resourceManager)
    }

    private fun addTransports(network: Network, resourceManager: ResourceManager, upgrader: Upgrader, dispatcher: CoroutineDispatcher) {
        if (config.transportFactories.isNotEmpty()) {
            for (transportFactory in config.transportFactories) {
                transportFactory.create(upgrader, resourceManager, dispatcher)
                    .flatMap { transport -> network.addTransport(transport) }
                    .onFailure { errors.recordError(it) }
            }
        } else {
            val subTypes = getFactory<TransportFactory>()
            for (subType in subTypes) {
                subType.create(upgrader, resourceManager, dispatcher)
                    .onFailure { errors.recordError(it) }
                    .onSuccess { network.addTransport(it) }
            }
        }
    }

    private fun addListenAddresses(network: Network) {
        if (config.swarmConfig.listenAddresses.isEmpty() && config.transportFactories.isEmpty()) {
            config.swarmConfig.listenAddresses.add { InetMultiaddress.fromString("/ip4/0.0.0.0/tcp/0") }
            config.swarmConfig.listenAddresses.add { InetMultiaddress.fromString("/ip6/::/tcp/0") }
        }
        config.swarmConfig.listenAddresses.forEach {
            it()
                .flatMap { listenAddress -> network.addListener(listenAddress) }
                .onFailure { errors.recordError(it) }
        }
    }

    private inline fun <reified T : Any> getFactory(): List<T> {
        val subTypes = reflections.getSubTypesOf(T::class.java).map { it.kotlin }
        logger.info { "Found ${T::class.simpleName}: " }
        subTypes.forEach {
            logger.info { " * ${it.qualifiedName}" }
        }
        return subTypes.mapNotNull { it.objectInstance }
    }
}

@HostDsl
fun host(init: HostBuilder.() -> Unit): HostBuilder {
    return HostBuilder().apply(init)
}
