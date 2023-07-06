// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.network.streammuxer

import kotlinx.coroutines.CoroutineScope
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.Result

interface StreamMuxerTransportFactory {
    val protocolId: ProtocolId
    fun create(scope: CoroutineScope): Result<StreamMuxerTransport>
}
