package org.erwinkok.libp2p.muxer.mplex

import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.test.runTest
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.random.Random

internal class FrameTest {
    private val maxStreamId = 0x1000000000000000L

    @Test
    fun testMessageNoData() = runTest {
        repeat(100) {
            val streamId = randomMplexStreamId()
            val expected = Frame.MessageFrame(streamId, Frame.Empty)
            val actual = expected.loopFrame<Frame.MessageFrame>()
            Assertions.assertEquals(expected.initiator, actual.initiator)
            Assertions.assertEquals(expected.id, actual.id)
            Assertions.assertArrayEquals(expected.data, actual.data)
        }
    }

    @Test
    fun testMessage() = runTest {
        repeat(100) {
            val data = Random.nextBytes(1024)
            val streamId = randomMplexStreamId()
            val expected = Frame.MessageFrame(streamId, data)
            val actual = expected.loopFrame<Frame.MessageFrame>()
            Assertions.assertEquals(expected.initiator, actual.initiator)
            Assertions.assertEquals(expected.id, actual.id)
            Assertions.assertArrayEquals(data, actual.data)
        }
    }

    @Test
    fun testNewStream() = runTest {
        repeat(100) {
            val id = Random.nextLong(maxStreamId)
            val name = randomText(Random.nextInt(64))
            val expected = Frame.NewStreamFrame(id, name)
            val actual = expected.loopFrame<Frame.NewStreamFrame>()
            Assertions.assertEquals(expected.initiator, actual.initiator)
            Assertions.assertEquals(expected.id, actual.id)
            Assertions.assertEquals(name, actual.name)
        }
    }

    @Test
    fun testCloseFrame() = runTest {
        repeat(100) {
            val streamId = randomMplexStreamId()
            val expected = Frame.CloseFrame(streamId)
            val actual = expected.loopFrame<Frame.CloseFrame>()
            Assertions.assertEquals(expected.initiator, actual.initiator)
            Assertions.assertEquals(expected.id, actual.id)
        }
    }

    @Test
    fun testResetFrame() = runTest {
        repeat(100) {
            val streamId = randomMplexStreamId()
            val expected = Frame.ResetFrame(streamId)
            val actual = expected.loopFrame<Frame.ResetFrame>()
            Assertions.assertEquals(expected.initiator, actual.initiator)
            Assertions.assertEquals(expected.id, actual.id)
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

    internal suspend inline fun <reified T : Frame> Frame.loopFrame(): T {
        val byteChannel = ByteChannel(false)
        FrameWriter.writeMplexFrame(byteChannel, this)
        byteChannel.flush()
        val frame = FrameReaderOld.readMplexFrame(byteChannel).expectNoErrors()
        Assertions.assertInstanceOf(T::class.java, frame)
        return frame as T
    }
}
