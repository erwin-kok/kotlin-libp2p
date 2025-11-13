// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.mplex

import io.ktor.utils.io.ClosedReadChannelException
import io.ktor.utils.io.ClosedWriteChannelException
import io.ktor.utils.io.availableForRead
import io.ktor.utils.io.cancel
import io.ktor.utils.io.core.build
import io.ktor.utils.io.core.writeFully
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readPacket
import io.ktor.utils.io.writeFully
import io.mockk.Runs
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlinx.io.Buffer
import kotlinx.io.EOFException
import kotlinx.io.readByteArray
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

internal class MplexMuxedStreamTest {
    private val mplexStreamId = MplexStreamId(true, 1234)
    private val mplexStreamName = "AName"
    private val mplexMultiplexer = mockk<MplexStreamMuxerConnection>()
    private val streamIdSlot = slot<MplexStreamId>()

    @BeforeEach
    fun setup() {
        every { mplexMultiplexer.removeStream(capture(streamIdSlot)) } just Runs
    }

    @Test
    fun testIdAndName() = runTest {
        val reader = FrameReader(this)
        val muxedStream = MplexMuxedStream(this, mplexMultiplexer, reader.frameChannel, mplexStreamId, mplexStreamName)
        assertEquals("stream000004d2/initiator", muxedStream.id)
        assertEquals(mplexStreamName, muxedStream.name)
        muxedStream.close()
        muxedStream.awaitClosed()
        reader.stop()
        reader.assertNoBytesReceived()
    }

    @Test
    fun testInitiallyNothingAvailableForRead() = runTest {
        val reader = FrameReader(this)
        val muxedStream = MplexMuxedStream(this, mplexMultiplexer, reader.frameChannel, mplexStreamId, mplexStreamName)
        assertEquals(0, muxedStream.input.availableForRead)
        muxedStream.close()
        muxedStream.awaitClosed()
        reader.stop()
        reader.assertNoBytesReceived()
    }

    @Test
    fun testReadPacket() = runTest {
        repeat(1000) {
            val reader = FrameReader(this)
            val muxedStream = MplexMuxedStream(this, mplexMultiplexer, reader.frameChannel, mplexStreamId, mplexStreamName)
            val random = Random.nextBytes(100000)
            assertTrue(muxedStream.remoteSendsNewMessage(random))
            val bytes = ByteArray(random.size)
            muxedStream.input.readFully(bytes)
            assertArrayEquals(random, bytes)
            muxedStream.close()
            muxedStream.awaitClosed()
            reader.stop()
            reader.assertNoBytesReceived()
        }
    }

    @Test
    fun testReadPacketSplit() = runTest {
        repeat(1000) {
            val reader = FrameReader(this)
            val muxedStream = MplexMuxedStream(this, mplexMultiplexer, reader.frameChannel, mplexStreamId, mplexStreamName)
            val random = Random.nextBytes(50000)
            assertTrue(muxedStream.remoteSendsNewMessage(random))
            for (j in 0 until 5) {
                val bytes = ByteArray(10000)
                muxedStream.input.readFully(bytes)
                assertArrayEquals(random.copyOfRange(j * bytes.size, (j + 1) * bytes.size), bytes)
            }
            muxedStream.close()
            muxedStream.awaitClosed()
            reader.stop()
            reader.assertNoBytesReceived()
        }
    }

    @Test
    fun testReadPacketCombined() = runTest {
        repeat(1000) {
            val reader = FrameReader(this)
            val muxedStream = MplexMuxedStream(this, mplexMultiplexer, reader.frameChannel, mplexStreamId, mplexStreamName)
            val random = Random.nextBytes(50000)
            for (j in 0 until 5) {
                val bytes = random.copyOfRange(j * 10000, (j + 1) * 10000)
                assertTrue(muxedStream.remoteSendsNewMessage(bytes))
            }
            val bytes = ByteArray(random.size)
            muxedStream.input.readFully(bytes)
            assertArrayEquals(random, bytes)
            muxedStream.close()
            muxedStream.awaitClosed()
            reader.stop()
            reader.assertNoBytesReceived()
        }
    }

    @Test
    fun testReadPacketWait() = runTest {
        val reader = FrameReader(this)
        val muxedStream = MplexMuxedStream(this, mplexMultiplexer, reader.frameChannel, mplexStreamId, mplexStreamName)
        val result = withTimeoutOrNull(500) {
            muxedStream.input.readPacket(10)
        }
        assertNull(result)
        muxedStream.close()
        muxedStream.awaitClosed()
        reader.stop()
        reader.assertNoBytesReceived()
    }

    @Test
    fun testReadPacketAfterCancel() = runTest {
        val reader = FrameReader(this)
        val muxedStream = MplexMuxedStream(this, mplexMultiplexer, reader.frameChannel, mplexStreamId, mplexStreamName)
        muxedStream.input.cancel()
        yield() // Give the input coroutine a chance to cancel
        val exception = assertThrows<ClosedReadChannelException> {
            muxedStream.input.readPacket(10)
        }
        assertEquals("Channel was cancelled", exception.message)
        // Remote can not send messages
        assertFalse(muxedStream.remoteSendsNewMessage(Random.nextBytes(100000)))
        muxedStream.close()
        muxedStream.awaitClosed()
        reader.stop()
        reader.assertNoBytesReceived()
    }

    @Test
    fun testReadPacketAfterClose() = runTest {
        val reader = FrameReader(this)
        val muxedStream = MplexMuxedStream(this, mplexMultiplexer, reader.frameChannel, mplexStreamId, mplexStreamName)
        assertFalse(muxedStream.input.isClosedForRead)
        assertFalse(muxedStream.output.isClosedForWrite)
        muxedStream.close()
        muxedStream.awaitClosed()
        assertTrue(muxedStream.input.isClosedForRead)
        assertTrue(muxedStream.output.isClosedForWrite)
        assertStreamRemoved()
        val exception1 = assertThrows<ClosedReadChannelException> {
            muxedStream.input.readPacket(123)
        }
        assertEquals("Channel was cancelled", exception1.message)
        reader.stop()
        reader.assertNoBytesReceived()
        reader.assertCloseFrameReceived(mplexStreamId)
    }

    @Test
    fun testReadPacketAfterRemoteCloses() = runTest {
        val reader = FrameReader(this)
        val muxedStream = MplexMuxedStream(this, mplexMultiplexer, reader.frameChannel, mplexStreamId, mplexStreamName)
        assertFalse(muxedStream.input.isClosedForRead)
        assertFalse(muxedStream.output.isClosedForWrite)
        muxedStream.remoteClosesWriting()
        yield()
        assertTrue(muxedStream.input.isClosedForRead)
        assertFalse(muxedStream.output.isClosedForWrite)
        reader.assertNoCloseFrameReceived()
        assertStreamNotRemoved()
        val exception1 = assertThrows<EOFException> {
            muxedStream.input.readPacket(123)
        }
        assertEquals("Not enough data available, required 123 bytes but only 0 available", exception1.message)
        muxedStream.close()
        muxedStream.awaitClosed()
        reader.stop()
        reader.assertNoBytesReceived()
        reader.assertCloseFrameReceived(mplexStreamId)
    }

    @Test
    fun testReadPacketAfterRemoteClosesDataInBuffer() = runTest {
        val reader = FrameReader(this)
        val muxedStream = MplexMuxedStream(this, mplexMultiplexer, reader.frameChannel, mplexStreamId, mplexStreamName)
        assertFalse(muxedStream.input.isClosedForRead)
        assertFalse(muxedStream.output.isClosedForWrite)
        val random = Random.nextBytes(50000)
        assertTrue(muxedStream.remoteSendsNewMessage(random))
        muxedStream.remoteClosesWriting()
        yield()
        assertFalse(muxedStream.input.isClosedForRead)
        assertFalse(muxedStream.output.isClosedForWrite)
        reader.assertNoCloseFrameReceived()
        assertStreamNotRemoved()
        val bytes = ByteArray(random.size)
        muxedStream.input.readFully(bytes)
        assertArrayEquals(random, bytes)
        assertTrue(muxedStream.input.isClosedForRead)
        assertFalse(muxedStream.output.isClosedForWrite)
        val exception = assertThrows<EOFException> {
            muxedStream.input.readPacket(321)
        }
        assertEquals("Not enough data available, required 321 bytes but only 0 available", exception.message)
        muxedStream.close()
        muxedStream.awaitClosed()
        reader.stop()
        reader.assertNoBytesReceived()
        reader.assertCloseFrameReceived(mplexStreamId)
    }

    @Test
    @Disabled
    fun testNotReading() = runTest {
        val reader = FrameReader(this)
        val muxedStream = MplexMuxedStream(this, mplexMultiplexer, reader.frameChannel, mplexStreamId, mplexStreamName)
        // It seems that the maximum of the input ByteReadChannel is 4088 bytes. So we have to provide enough data
        // to fill the input channel (~5 * 1000 bytes) and we also have to fill up the inputChannel with 16 packets.
        // So we have to provide 5 + 16 = 21 packets.
        repeat(21) {
            muxedStream.remoteSendsNewMessage(Random.nextBytes(1000))
            yield() // Give the input coroutine a chance to process the packets
        }
        assertTrue(muxedStream.input.availableForRead > 0)
        val timeout = withTimeoutOrNull(2.seconds) {
            muxedStream.remoteSendsNewMessage(Random.nextBytes(1000))
        }
        assertNull(timeout)
        muxedStream.close() // Causes all packets in the input channel to be closed
        muxedStream.awaitClosed()
        reader.stop()
        reader.assertNoBytesReceived()
    }

    @Test
    fun testReadPacketAfterReset() = runTest {
        val reader = FrameReader(this)
        val muxedStream = MplexMuxedStream(this, mplexMultiplexer, reader.frameChannel, mplexStreamId, mplexStreamName)
        val random = Random.nextBytes(50000)
        muxedStream.remoteSendsNewMessage(random)
        assertFalse(muxedStream.input.isClosedForRead)
        assertFalse(muxedStream.output.isClosedForWrite)
        muxedStream.reset()
        muxedStream.awaitClosed()
        assertTrue(muxedStream.input.isClosedForRead)
        assertTrue(muxedStream.output.isClosedForWrite)
        reader.assertResetFrameReceived(mplexStreamId)
        assertStreamRemoved()
        val exception = assertThrows<CancellationException> {
            muxedStream.input.readPacket(random.size)
        }
        assertEquals("Stream was reset", exception.message)
        reader.stop()
        reader.assertNoBytesReceived()
    }

    //
    // Write
    //

    @Test
    fun testWritePacket() = runTest {
        repeat(1000) {
            val reader = FrameReader(this)
            val random = Random.nextBytes(10000)
            val muxedStream = MplexMuxedStream(this, mplexMultiplexer, reader.frameChannel, mplexStreamId, mplexStreamName)
            muxedStream.output.writeFully(random)
            muxedStream.output.flush()
            muxedStream.close()
            muxedStream.awaitClosed()
            reader.stop()
            reader.assertBytesReceived(random)
        }
    }

    @Test
    fun testWritePacketSplit() = runTest {
        repeat(1000) {
            val reader = FrameReader(this)
            val random = Random.nextBytes(10000)
            val muxedStream = MplexMuxedStream(this, mplexMultiplexer, reader.frameChannel, mplexStreamId, mplexStreamName)
            muxedStream.output.writeFully(random, 0, 5000)
            muxedStream.output.writeFully(random, 5000, 10000)
            muxedStream.output.flush()
            muxedStream.close()
            muxedStream.awaitClosed()
            reader.stop()
            reader.assertBytesReceived(random)
        }
    }

    @Test
    fun testWritePacketAfterChannelClose() = runTest {
        val reader = FrameReader(this)
        val muxedStream = MplexMuxedStream(this, mplexMultiplexer, reader.frameChannel, mplexStreamId, mplexStreamName)
        muxedStream.output.flushAndClose()
        yield() // Give the input coroutine a chance to cancel
        assertThrows<ClosedWriteChannelException> {
            muxedStream.output.writeFully(Random.nextBytes(100000))
            muxedStream.output.flush()
        }
        // Remote can send messages
        assertTrue(muxedStream.remoteSendsNewMessage(Random.nextBytes(1000)))
        muxedStream.close()
        muxedStream.awaitClosed()
        reader.stop()
        reader.assertNoBytesReceived()
        reader.assertCloseFrameReceived(mplexStreamId)
    }

    @Test
    fun testWritePacketAfterClose() = runTest {
        val reader = FrameReader(this)
        val muxedStream = MplexMuxedStream(this, mplexMultiplexer, reader.frameChannel, mplexStreamId, mplexStreamName)
        assertFalse(muxedStream.input.isClosedForRead)
        assertFalse(muxedStream.output.isClosedForWrite)
        muxedStream.close()
        muxedStream.awaitClosed()
        assertTrue(muxedStream.input.isClosedForRead)
        assertTrue(muxedStream.output.isClosedForWrite)
        assertStreamRemoved()
        assertThrows<ClosedWriteChannelException> {
            muxedStream.output.writeFully(Random.nextBytes(100000))
            muxedStream.output.flush()
        }
        reader.stop()
        reader.assertNoBytesReceived()
        reader.assertCloseFrameReceived(mplexStreamId)
    }

    @Test
    fun testWritePacketAfterReset() = runTest {
        val reader = FrameReader(this)
        val muxedStream = MplexMuxedStream(this, mplexMultiplexer, reader.frameChannel, mplexStreamId, mplexStreamName)
        val random = Random.nextBytes(50000)
        muxedStream.remoteSendsNewMessage(random)
        assertFalse(muxedStream.input.isClosedForRead)
        assertFalse(muxedStream.output.isClosedForWrite)
        muxedStream.reset()
        muxedStream.awaitClosed()
        assertTrue(muxedStream.input.isClosedForRead)
        assertTrue(muxedStream.output.isClosedForWrite)
        reader.assertResetFrameReceived(mplexStreamId)
        assertStreamRemoved()
        val exception = assertThrows<CancellationException> {
            muxedStream.output.writeFully(Random.nextBytes(100000))
            muxedStream.output.flush()
        }
        assertEquals("Stream was reset", exception.message)
        reader.stop()
        reader.assertNoBytesReceived()
    }

    private fun assertStreamRemoved() {
        coVerify { mplexMultiplexer.removeStream(any()) }
        assertEquals(mplexStreamId, streamIdSlot.captured)
    }

    private fun assertStreamNotRemoved() {
        coVerify(exactly = 0) { mplexMultiplexer.removeStream(any()) }
    }

    private class FrameReader(scope: CoroutineScope) {
        val frameChannel = Channel<Frame>(16)
        private var closeFrame: Frame.CloseFrame? = null
        private var resetFrame: Frame.ResetFrame? = null
        private val builder = Buffer()
        private val job = scope.launch {
            frameChannel.consumeEach {
                when (it) {
                    is Frame.MessageFrame -> {
                        builder.writeFully(it.data)
                    }

                    is Frame.CloseFrame -> {
                        assertNull(closeFrame)
                        closeFrame = it
                    }

                    is Frame.ResetFrame -> {
                        assertNull(resetFrame)
                        resetFrame = it
                    }

                    else -> {
                        assertTrue(false, "Unexpected frame type in FrameReader: $it")
                    }
                }
            }
        }

        fun assertResetFrameReceived(streamId: MplexStreamId) {
            assertNotNull(resetFrame)
            assertEquals(streamId.initiator, resetFrame!!.initiator)
            assertEquals(streamId.id, resetFrame!!.id)
        }

        fun assertCloseFrameReceived(streamId: MplexStreamId) {
            assertNotNull(closeFrame)
            assertEquals(streamId.initiator, closeFrame!!.initiator)
            assertEquals(streamId.id, closeFrame!!.id)
        }

        fun assertNoCloseFrameReceived() {
            assertNull(closeFrame)
        }

        fun assertBytesReceived(expected: ByteArray) {
            assertArrayEquals(expected, builder.build().readByteArray())
        }

        fun assertNoBytesReceived() {
            assertTrue(builder.build().exhausted())
        }

        suspend fun stop() {
            frameChannel.close()
            job.join()
        }
    }
}
