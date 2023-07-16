// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.host.builder

import org.erwinkok.result.CombinedError

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
annotation class HostDsl

@HostDsl
class HostBuilder {
    val errors = CombinedError()
    val config = HostConfig()
}

@HostDsl
fun host(init: HostBuilder.() -> Unit): HostBuilder {
    return HostBuilder().apply(init)
}
