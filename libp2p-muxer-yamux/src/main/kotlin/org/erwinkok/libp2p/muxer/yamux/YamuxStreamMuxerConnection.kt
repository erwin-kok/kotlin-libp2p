// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
@file:OptIn(DelicateCoroutinesApi::class)

package org.erwinkok.libp2p.muxer.yamux

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import org.erwinkok.libp2p.core.network.streammuxer.MuxedStream
import org.erwinkok.libp2p.core.network.streammuxer.StreamMuxerConnection
import org.erwinkok.result.Result

class YamuxStreamMuxerConnection internal constructor(
    private val session: Session,
) : StreamMuxerConnection {
    override val isClosed: Boolean get() = session.isClosed

    override val jobContext: Job get() = session.jobContext

    override suspend fun openStream(name: String?): Result<MuxedStream> {
        return session.openStream(name)
    }

    override suspend fun acceptStream(): Result<MuxedStream> {
        return session.acceptStream()
    }

    override fun close() {
        session.close()
    }
}
