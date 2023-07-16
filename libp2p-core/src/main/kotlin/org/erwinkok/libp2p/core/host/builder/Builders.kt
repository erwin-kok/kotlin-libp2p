// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.host.builder

abstract class BuilderPart(val hostBuilder: HostBuilder) {
    val errors = hostBuilder.errors
    val config = hostBuilder.config
}

class MuxersBuilder(hostBuilder: HostBuilder) : BuilderPart(hostBuilder)

class SecurityTransportBuilder(hostBuilder: HostBuilder) : BuilderPart(hostBuilder)

class TransportsBuilder(hostBuilder: HostBuilder) : BuilderPart(hostBuilder)
