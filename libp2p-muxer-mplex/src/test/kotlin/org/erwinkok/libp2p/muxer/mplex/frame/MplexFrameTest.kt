// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.mplex.frame

import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.core.writeFully
import kotlinx.coroutines.test.runTest
import org.erwinkok.libp2p.core.util.buildPacket
import org.erwinkok.libp2p.muxer.mplex.MplexStreamId
import org.erwinkok.libp2p.testing.TestWithLeakCheck
import org.erwinkok.libp2p.testing.VerifyingChunkBufferPool
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

internal class MplexFrameTest : TestWithLeakCheck {
    override val pool = VerifyingChunkBufferPool()

    private val maxStreamId = 0x1000000000000000L

    @Test
    fun testMessageNoData() = runTest {
        repeat(100) {
            val streamId = randomMplexStreamId()
            val expected = MessageFrame(streamId, ByteReadPacket.Companion.Empty)
            val actual = expected.loopFrame<MessageFrame>()
            assertEquals(expected.initiator, actual.initiator)
            assertEquals(expected.id, actual.id)
            assertEquals(expected.packet, actual.packet)
        }
    }

    @Test
    fun testMessage() = runTest {
        repeat(100) {
            val data = Random.nextBytes(1024)
            val streamId = randomMplexStreamId()
            val expected = MessageFrame(streamId, buildPacket(pool) { writeFully(data) })
            val actual = expected.loopFrame<MessageFrame>()
            assertEquals(expected.initiator, actual.initiator)
            assertEquals(expected.id, actual.id)
            assertArrayEquals(data, actual.packet.readBytes())
        }
    }

    @Test
    fun testNewStream() = runTest {
        repeat(100) {
            val id = Random.nextLong(maxStreamId)
            val name = randomText(Random.nextInt(64))
            val expected = NewStreamFrame(id, name)
            val actual = expected.loopFrame<NewStreamFrame>()
            assertEquals(expected.initiator, actual.initiator)
            assertEquals(expected.id, actual.id)
            assertEquals(name, actual.name)
        }
    }

    @Test
    fun testCloseFrame() = runTest {
        repeat(100) {
            val streamId = randomMplexStreamId()
            val expected = CloseFrame(streamId)
            val actual = expected.loopFrame<CloseFrame>()
            assertEquals(expected.initiator, actual.initiator)
            assertEquals(expected.id, actual.id)
        }
    }

    @Test
    fun testResetFrame() = runTest {
        repeat(100) {
            val streamId = randomMplexStreamId()
            val expected = ResetFrame(streamId)
            val actual = expected.loopFrame<ResetFrame>()
            assertEquals(expected.initiator, actual.initiator)
            assertEquals(expected.id, actual.id)
        }
    }

    private fun randomText(length: Int): String {
        val charset = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        return (1..length)
            .map { charset.random() }
            .joinToString("")
    }

    private fun randomMplexStreamId(): MplexStreamId {
        val id = Random.nextLong(maxStreamId)
        val initiator = Random.nextBoolean()
        return MplexStreamId(initiator, id)
    }
}
