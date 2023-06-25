// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.network.streammuxer

import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.result.Result

interface StreamMuxerConnection : AwaitableClosable {
    suspend fun openStream(name: String? = null): Result<MuxedStream>
    suspend fun acceptStream(): Result<MuxedStream>
}
