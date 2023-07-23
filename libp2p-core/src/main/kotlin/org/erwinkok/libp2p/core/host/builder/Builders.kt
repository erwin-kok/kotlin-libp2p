// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.host.builder

abstract class BuilderPart(val hostBuilder: HostBuilder) {
    val errors = hostBuilder.errors
    val config = hostBuilder.config
}

class MuxersBuilder(hostBuilder: HostBuilder) : BuilderPart(hostBuilder)

class SecurityTransportBuilder(hostBuilder: HostBuilder) : BuilderPart(hostBuilder)

class TransportsBuilder(hostBuilder: HostBuilder) : BuilderPart(hostBuilder)

class PeerstoreBuilder(hostBuilder: HostBuilder) : BuilderPart(hostBuilder) {
    var cacheSize by config.peerstoreConfig::cacheSize
    var maxProtocols by config.peerstoreConfig::maxProtocols
    var gcInitialDelay by config.peerstoreConfig::gcInitialDelay
    var gcInterval by config.peerstoreConfig::gcPurgeInterval

    @HostDsl
    fun keyStore(init: KeyStoreBuilder.() -> Unit) {
        val keyStoreConfig = KeyStoreConfig()
        config.peerstoreConfig.keyStoreConfig = keyStoreConfig
        KeyStoreBuilder(hostBuilder, keyStoreConfig).apply(init)
    }
}

class KeyStoreBuilder(hostBuilder: HostBuilder, private val keyStoreConfig: KeyStoreConfig) : BuilderPart(hostBuilder) {
    var password by keyStoreConfig::password

    @HostDsl
    fun dek(init: DekConfig.() -> Unit) {
        keyStoreConfig.dekConfig.apply(init)
    }
}
