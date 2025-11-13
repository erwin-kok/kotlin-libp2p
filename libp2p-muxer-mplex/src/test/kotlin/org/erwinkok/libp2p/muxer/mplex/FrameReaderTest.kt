package org.erwinkok.libp2p.muxer.mplex

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.test.runTest
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import kotlin.random.Random
import kotlin.system.measureTimeMillis

internal class FrameReaderTest {
    private val maxStreamId = 0x1000000000000000L

    @Test
    fun testMessageNoData() = runTest {
        repeat(1000) {
            val streamId = randomMplexStreamId()
            val expected = Frame.MessageFrame(streamId, Frame.Empty)
            val byteChannel = ByteChannel(false)
            FrameWriter.writeMplexFrame(byteChannel, expected)
            byteChannel.flush()
            val bb = ByteBuffer.allocate(1024)
            byteChannel.readAvailable(bb)
            bb.flip()
            val frameReader = FrameReader()
            val actual = frameReader.frame(bb)
            assertEquals(expected, actual)
        }
    }

    @Test
    fun x() = runTest {
        repeat(50) {
            val a = measureTimeMillis {
                repeat(5000) {
                    val streamId = randomMplexStreamId()
                    val data = Random.nextBytes(1024)
                    val expected = Frame.MessageFrame(streamId, data)
                    val byteChannel = ByteChannel(false)
                    FrameWriter.writeMplexFrame(byteChannel, expected)
                    byteChannel.flush()
                    val frame = FrameReaderOld.readMplexFrame(byteChannel).expectNoErrors()
                    assertEquals(expected, frame)
                }
            }
            val b = measureTimeMillis {
                val bb = ByteBuffer.allocate(2048)
                repeat(5000) {
                    val streamId = randomMplexStreamId()
                    val data = Random.nextBytes(1024)
                    val expected = Frame.MessageFrame(streamId, data)
                    val byteChannel = ByteChannel(false)
                    FrameWriter.writeMplexFrame(byteChannel, expected)
                    byteChannel.flush()
                    bb.clear()
                    byteChannel.readAvailable(bb)
                    bb.flip()
                    val frameReader = FrameReader()
                    frameReader.frame(bb)
                }
            }
            val x = a.toFloat() / b.toFloat()
            println(">> $a $b --> ${x * 100.0}")
        }
    }

    private fun randomMplexStreamId(): MplexStreamId {
        val id = Random.nextLong(maxStreamId)
        val initiator = Random.nextBoolean()
        return MplexStreamId(initiator, id)
    }
}
