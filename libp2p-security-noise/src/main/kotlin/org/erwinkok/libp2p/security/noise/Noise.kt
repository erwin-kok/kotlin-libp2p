// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.security.noise

import org.erwinkok.libp2p.core.host.builder.HostDsl
import org.erwinkok.libp2p.core.host.builder.SecurityTransportBuilder

@HostDsl
fun SecurityTransportBuilder.noise() {
    if (config.insecure) {
        errors.recordError { "cannot configure security transports with an insecure configuration" }
    } else {
        config.securityTransportFactories.add(NoiseTransport)
    }
}
