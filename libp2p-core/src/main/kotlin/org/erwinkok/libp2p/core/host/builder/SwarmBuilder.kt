// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.host.builder

import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.result.Ok

class SwarmBuilder(hostBuilder: HostBuilder) : BuilderPart(hostBuilder) {
    var dialers by config.swarmConfig::dialers
    var dialTimeout by config.swarmConfig::dialTimeout
    var backoffBase by config.swarmConfig::backoffBase
    var backoffCoefficient by config.swarmConfig::backoffCoefficient
    var backoffMax by config.swarmConfig::backoffMax

    @HostDsl
    fun listenAddresses(init: ListenAddressBuilder.() -> Unit) {
        ListenAddressBuilder(hostBuilder).apply(init)
    }
}

class ListenAddressBuilder(hostBuilder: HostBuilder) : BuilderPart(hostBuilder) {
    @HostDsl
    fun multiAddress(multiaddress: InetMultiaddress) {
        config.swarmConfig.listenAddresses.add { Ok(multiaddress) }
    }

    @HostDsl
    fun multiAddress(multiaddress: String) {
        config.swarmConfig.listenAddresses.add { InetMultiaddress.fromString(multiaddress) }
    }
}
