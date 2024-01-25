// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.yamux

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.core.internal.ChunkBuffer
import io.ktor.utils.io.pool.ObjectPool
import kotlinx.coroutines.Job
import org.erwinkok.libp2p.core.network.streammuxer.MuxedStream

enum class StreamState {
    StreamInit,
    StreamSYNSent,
    StreamSYNReceived,
    StreamEstablished,
    StreamFinished,
}

enum class HalfStreamState {
    HalfOpen,
    HalfClosed,
    HalfReset,
}

class YamuxMuxedStream(
    private val session: Session,

): MuxedStream {
//    sendWindow uint32
//
//    memorySpan MemoryManager
//
//    id      uint32
//    session *Session
//
//    recvWindow uint32
//    epochStart time.Time
//
//    state                 streamState
//    writeState, readState halfStreamState
//    stateLock             sync.Mutex
//
//    recvBuf segmentedBuffer
//
//    recvNotifyCh chan struct{}
//    sendNotifyCh chan struct{}
//
//    readDeadline, writeDeadline pipeDeadline

    override val name: String
        get() = TODO("Not yet implemented")
    override val id: String
        get() = TODO("Not yet implemented")

    override fun reset() {
        TODO("Not yet implemented")
    }

    override val pool: ObjectPool<ChunkBuffer>
        get() = TODO("Not yet implemented")
    override val input: ByteReadChannel
        get() = TODO("Not yet implemented")
    override val output: ByteWriteChannel
        get() = TODO("Not yet implemented")

    override fun close() {
        TODO("Not yet implemented")
    }

    override val jobContext: Job
        get() = TODO("Not yet implemented")
}
