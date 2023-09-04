// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.testing.mocknet.done

import org.erwinkok.libp2p.testing.mocknet.MockStream

data class StreamPair(
    val dialer: MockStream,
    val target: MockStream
)
