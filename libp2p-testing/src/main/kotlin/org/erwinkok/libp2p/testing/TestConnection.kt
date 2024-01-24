// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.testing

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.close
import io.ktor.utils.io.core.internal.ChunkBuffer
import io.ktor.utils.io.pool.ObjectPool
import kotlinx.coroutines.Job
import org.erwinkok.libp2p.core.network.Connection

class TestConnection(chunkBufferPool: VerifyingChunkBufferPool) {
    private val input = ByteChannel(true)
    private val output = ByteChannel(true)

    val local = Inner(chunkBufferPool, input, output)
    val remote = Inner(chunkBufferPool, output, input)

    inner class Inner(
        private val chunkBufferPool: VerifyingChunkBufferPool,
        private val _input: ByteReadChannel,
        private val _output: ByteWriteChannel,
    ) : Connection {
        override val pool: ObjectPool<ChunkBuffer>
            get() = chunkBufferPool
        override val jobContext: Job
            get() = Job()
        override val input: ByteReadChannel
            get() = _input
        override val output: ByteWriteChannel
            get() = _output

        override fun close() {
            _input.cancel()
            _output.close()
        }
    }
}
