// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.protocol.identify

import kotlinx.coroutines.Job

enum class PushSupport {
    IdentifyPushSupportUnknown,
    IdentifyPushSupported,
    IdentifyPushUnsupported,
}

class ConnectionEntry(
    val job: Job? = null,
    var pushSupport: PushSupport = PushSupport.IdentifyPushSupportUnknown,
    var sequence: Long = 0L,
)
