// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.testing

import io.ktor.utils.io.core.BytePacketBuilder
import io.ktor.utils.io.core.ByteReadPacket
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

interface TestWithLeakCheck {
    val pool: VerifyingChunkBufferPool

    @BeforeEach
    fun resetInUse() {
        pool.resetInUse()
    }

    @AfterEach
    fun checkNoBuffersInUse() {
        pool.assertNoInUse()
    }

    fun buildPacket(block: BytePacketBuilder.() -> Unit): ByteReadPacket {
        val builder = BytePacketBuilder(pool)
        try {
            block(builder)
            return builder.build()
        } catch (t: Throwable) {
            builder.release()
            throw t
        }
    }
}
