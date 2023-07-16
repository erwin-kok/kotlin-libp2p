// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.security.plaintext

import org.erwinkok.libp2p.core.host.builder.HostDsl
import org.erwinkok.libp2p.core.host.builder.SecurityTransportBuilder

@HostDsl
fun SecurityTransportBuilder.plainText() {
    if (config.securityTransportFactories.isNotEmpty()) {
        errors.recordError { "cannot disable security transport with secure transports configured" }
    } else {
        config.securityTransportFactories.add(PlainTextSecureTransport)
        config.insecure = true
    }
}

@HostDsl
fun SecurityTransportBuilder.noSecurity() {
    plainText()
}
