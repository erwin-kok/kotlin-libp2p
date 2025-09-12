// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.testing

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.close
import kotlinx.coroutines.Job
import org.erwinkok.libp2p.core.network.Connection

class TestConnection {
    private val input = ByteChannel(true)
    private val output = ByteChannel(true)

    val local = Inner(input, output)
    val remote = Inner(output, input)

    inner class Inner(
        private val _input: ByteReadChannel,
        private val _output: ByteWriteChannel,
    ) : Connection {
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
