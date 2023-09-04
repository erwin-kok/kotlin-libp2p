// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.testing.mocknet

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.core.internal.ChunkBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.network.ConnectionStatistics
import org.erwinkok.libp2p.core.network.NetworkConnection
import org.erwinkok.libp2p.core.network.Stream
import org.erwinkok.libp2p.core.resourcemanager.NullScope
import org.erwinkok.libp2p.core.resourcemanager.StreamScope
import org.erwinkok.libp2p.testing.mocknet.done.MockConnection
import org.erwinkok.libp2p.testing.mocknet.done.StreamPair
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class MockStream(
    scope: CoroutineScope,
    override val input: ByteReadChannel,
    override val output: ByteWriteChannel,
    override val statistic: ConnectionStatistics,
) : AwaitableClosable, Stream {
    private val _context = Job(scope.coroutineContext[Job])
    private val _id = streamCounter.getAndIncrement()

    //    write     *io.PipeWriter
//    read      *io.PipeReader
//    toDeliver chan *transportObject
//    reset  chan struct{}
//    close  chan struct{}
//    closed chan struct{}
//    writeErr error
    private val protocol = AtomicReference(ProtocolId.Null)
    override val pool = ChunkBuffer.Pool
    override lateinit var connection: NetworkConnection
    lateinit var reverseConnection: MockConnection                  // --> rstream        counterpart

    override val jobContext: Job
        get() = _context

    override val id: String
        get() = "$_id"

    override val name: String
        get() = id

    override val streamScope: StreamScope
        get() = NullScope.NullScope

    override fun protocol(): ProtocolId {
        return protocol.get()
    }

    override fun setProtocol(protocolId: ProtocolId): Result<Unit> {
        protocol.set(protocolId)
        return Ok(Unit)
    }

    override fun reset() {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
        _context.complete()
    }

    companion object {
        private val streamCounter = AtomicLong(0)

        fun newStreamPair(name: String?): Result<StreamPair> {
            TODO("Not yet implemented")
        }
    }
}