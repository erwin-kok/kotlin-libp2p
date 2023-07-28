// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network

import java.time.Instant

open class Statistics {
    var direction: Direction? = null
    var opened: Instant? = null
    var transient: Boolean = false
    var extra: Map<Any, Any>? = null
}
