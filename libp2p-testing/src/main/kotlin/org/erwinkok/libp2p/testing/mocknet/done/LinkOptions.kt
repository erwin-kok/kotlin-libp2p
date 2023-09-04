// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.testing.mocknet.done

import kotlin.time.Duration

data class LinkOptions(
    val latency: Duration,
    val bandwidth: Double
)
