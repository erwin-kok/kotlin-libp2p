// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.host.builder

import org.erwinkok.libp2p.core.datastore.BatchingDatastore
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.connectiongater.ConnectionGater
import org.erwinkok.libp2p.core.network.securitymuxer.SecureTransportFactory
import org.erwinkok.libp2p.core.network.streammuxer.StreamMuxerTransportFactory
import org.erwinkok.libp2p.core.network.transport.TransportFactory
import org.erwinkok.libp2p.core.peerstore.AddressStore.Companion.DefaultCacheSize
import org.erwinkok.libp2p.core.peerstore.AddressStore.Companion.DefaultGcInitialDelay
import org.erwinkok.libp2p.core.peerstore.AddressStore.Companion.DefaultGcPurgeInterval
import org.erwinkok.libp2p.core.peerstore.ProtocolsStore.Companion.MaxProtocols
import org.erwinkok.libp2p.core.resourcemanager.ResourceManager
import org.erwinkok.result.Result
import kotlin.time.Duration.Companion.seconds

typealias InetMultiAddressConstructor = () -> Result<InetMultiaddress>

class DekConfig {
    var hash: String = "sha2-512"
    var salt: String = "W/SC6fnZfBIWdeAD3l+ClLpQtfICEtn+KYTUhfKq6d7l"
    var iterationCount: Int = 10000
    var keyLength: Int = 256
}

class KeyStoreConfig {
    var password: String? = null
    val dekConfig = DekConfig()
}

class PeerstoreConfig {
    var cacheSize = DefaultCacheSize
    var maxProtocols = MaxProtocols
    var gcPurgeInterval = DefaultGcPurgeInterval
    var gcInitialDelay = DefaultGcInitialDelay
    var keyStoreConfig: KeyStoreConfig? = null
}

class SwarmConfig {
    var dialers: Int = 4
    var maxRetries: Int = 1
    var dialTimeout = 15.seconds
    var backoffBase = 15.seconds
    var backoffCoefficient = 1.seconds
    val listenAddresses = mutableListOf<InetMultiAddressConstructor>()
}

class HostConfig {
    var insecure = false
    var enablePing = false
    var localIdentity: LocalIdentity? = null
    val peerstoreConfig = PeerstoreConfig()
    val swarmConfig = SwarmConfig()
    val streamMuxerTransportFactories = mutableListOf<StreamMuxerTransportFactory>()
    val securityTransportFactories = mutableListOf<SecureTransportFactory>()
    val transportFactories = mutableListOf<TransportFactory>()
    var connectionGater: ConnectionGater? = null
    var resourceManager: ResourceManager? = null
    var datastore: BatchingDatastore? = null
}
