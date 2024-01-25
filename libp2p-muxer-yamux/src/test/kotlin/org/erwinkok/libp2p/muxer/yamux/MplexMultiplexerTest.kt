// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.yamux

import io.ktor.utils.io.close
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.core.toByteArray
import io.ktor.utils.io.core.writeFully
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.erwinkok.libp2p.core.network.Connection
import org.erwinkok.libp2p.core.network.StreamResetException
import org.erwinkok.libp2p.core.network.streammuxer.MuxedStream
import org.erwinkok.libp2p.core.util.buildPacket
import org.erwinkok.libp2p.muxer.yamux.frame.CloseFrame
import org.erwinkok.libp2p.muxer.yamux.frame.MessageFrame
import org.erwinkok.libp2p.muxer.yamux.frame.NewStreamFrame
import org.erwinkok.libp2p.muxer.yamux.frame.readMplexFrame
import org.erwinkok.libp2p.muxer.yamux.frame.writeMplexFrame
import org.erwinkok.libp2p.testing.TestConnection
import org.erwinkok.libp2p.testing.TestWithLeakCheck
import org.erwinkok.libp2p.testing.VerifyingChunkBufferPool
import org.erwinkok.result.coAssertErrorResult
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.experimental.xor
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

internal class MplexMultiplexerTest : TestWithLeakCheck {
    override val pool = VerifyingChunkBufferPool()

    private val maxStreamId = 0x1000000000000000L

    @Test
    fun remoteRequestsNewStream() = runTest {
        val connectionPair = TestConnection(pool)
        val mplexMultiplexer = YamuxStreamMuxerConnection(this, connectionPair.local, true)
        repeat(1000) {
            val id = randomId()
            connectionPair.remote.output.writeMplexFrame(NewStreamFrame(id, "aName$id"))
            connectionPair.remote.output.flush()
            val muxedStream = mplexMultiplexer.acceptStream().expectNoErrors()
            assertEquals("aName$id", muxedStream.name)
            assertStreamHasId(false, id, muxedStream)
            muxedStream.close()
            assertCloseFrameReceived(connectionPair.remote)
        }
        mplexMultiplexer.close()
        mplexMultiplexer.awaitClosed()
    }

    @Test
    fun localRequestNewStream() = runTest {
        val connectionPair = TestConnection(pool)
        val mplexMultiplexer = YamuxStreamMuxerConnection(this, connectionPair.local, true)
        repeat(1000) {
            val muxedStream = mplexMultiplexer.openStream("newStreamName$it").expectNoErrors()
            assertEquals("newStreamName$it", muxedStream.name)
            assertEquals(MplexStreamId(true, it.toLong()).toString(), muxedStream.id)
            val actual = connectionPair.remote.input.readMplexFrame().expectNoErrors()
            assertInstanceOf(NewStreamFrame::class.java, actual)
            assertTrue(actual.initiator)
            assertEquals(it.toLong(), actual.id)
            muxedStream.close()
            assertCloseFrameReceived(connectionPair.remote)
        }
        mplexMultiplexer.close()
        mplexMultiplexer.awaitClosed()
    }

    @Test
    fun remoteOpensAndRemoteSends() = runTest {
        val connectionPair = TestConnection(pool)
        val mplexMultiplexer = YamuxStreamMuxerConnection(this, connectionPair.local, true)
        repeat(1000) {
            val id = randomId()
            connectionPair.remote.output.writeMplexFrame(NewStreamFrame(id, "aName$id"))
            connectionPair.remote.output.flush()
            val muxedStream = mplexMultiplexer.acceptStream().expectNoErrors()
            assertEquals("aName$id", muxedStream.name)
            assertStreamHasId(false, id, muxedStream)
            val random1 = Random.nextBytes(1000)
            connectionPair.remote.output.writeMplexFrame(MessageFrame(MplexStreamId(true, id), buildPacket(pool) { writeFully(random1) }))
            connectionPair.remote.output.flush()
            assertFalse(muxedStream.input.isClosedForRead)
            val random2 = ByteArray(random1.size)
            muxedStream.input.readFully(random2)
            assertArrayEquals(random1, random2)
            muxedStream.close()
            assertCloseFrameReceived(connectionPair.remote)
        }
        mplexMultiplexer.close()
        mplexMultiplexer.awaitClosed()
    }

    @Test
    fun remoteOpensAndLocalSends() = runTest {
        val connectionPair = TestConnection(pool)
        val mplexMultiplexer = YamuxStreamMuxerConnection(this, connectionPair.local, true)
        repeat(1000) {
            val id = randomId()
            connectionPair.remote.output.writeMplexFrame(NewStreamFrame(id, "aName$id"))
            connectionPair.remote.output.flush()
            val muxedStream = mplexMultiplexer.acceptStream().expectNoErrors()
            assertEquals("aName$id", muxedStream.name)
            assertStreamHasId(false, id, muxedStream)
            val random1 = Random.nextBytes(1000)
            assertFalse(muxedStream.output.isClosedForWrite)
            muxedStream.output.writeFully(random1)
            muxedStream.output.flush()
            assertMessageFrameReceived(random1, connectionPair.remote)
            muxedStream.close()
            assertCloseFrameReceived(connectionPair.remote)
        }
        mplexMultiplexer.close()
        mplexMultiplexer.awaitClosed()
    }

    @Test
    fun localOpenAndLocalSends() = runTest {
        val connectionPair = TestConnection(pool)
        val mplexMultiplexer = YamuxStreamMuxerConnection(this, connectionPair.local, true)
        repeat(1000) {
            val muxedStream = mplexMultiplexer.openStream("newStreamName$it").expectNoErrors()
            assertEquals("newStreamName$it", muxedStream.name)
            assertEquals(MplexStreamId(true, it.toLong()).toString(), muxedStream.id)
            assertNewStreamFrameReceived(it, "newStreamName$it", connectionPair.remote)
            val random1 = Random.nextBytes(1000)
            assertFalse(muxedStream.output.isClosedForWrite)
            muxedStream.output.writeFully(random1)
            muxedStream.output.flush()
            assertMessageFrameReceived(random1, connectionPair.remote)
            muxedStream.close()
            assertCloseFrameReceived(connectionPair.remote)
        }
        mplexMultiplexer.close()
        mplexMultiplexer.awaitClosed()
    }

    @Test
    fun localOpensAndRemoteSends() = runTest {
        val connectionPair = TestConnection(pool)
        val mplexMultiplexer = YamuxStreamMuxerConnection(this, connectionPair.local, true)
        repeat(1000) {
            val muxedStream = mplexMultiplexer.openStream("newStreamName$it").expectNoErrors()
            assertEquals("newStreamName$it", muxedStream.name)
            assertEquals(MplexStreamId(true, it.toLong()).toString(), muxedStream.id)
            assertNewStreamFrameReceived(it, "newStreamName$it", connectionPair.remote)
            val random1 = Random.nextBytes(1000)
            connectionPair.remote.output.writeMplexFrame(MessageFrame(MplexStreamId(false, it.toLong()), buildPacket(pool) { writeFully(random1) }))
            connectionPair.remote.output.flush()
            assertFalse(muxedStream.input.isClosedForRead)
            val random2 = ByteArray(random1.size)
            muxedStream.input.readFully(random2)
            assertArrayEquals(random1, random2)
            muxedStream.close()
            assertCloseFrameReceived(connectionPair.remote)
        }
        mplexMultiplexer.close()
        mplexMultiplexer.awaitClosed()
    }

    @Test
    fun remoteRequestsNewStreamAndCloses() = runTest {
        val connectionPair = TestConnection(pool)
        val mplexMultiplexer = YamuxStreamMuxerConnection(this, connectionPair.local, true)
        val id = randomId()
        connectionPair.remote.output.writeMplexFrame(NewStreamFrame(id, "aName$id"))
        connectionPair.remote.output.flush()
        val muxedStream = mplexMultiplexer.acceptStream().expectNoErrors()
        assertEquals("aName$id", muxedStream.name)
        assertStreamHasId(false, id, muxedStream)
        assertFalse(muxedStream.input.isClosedForRead)
        assertFalse(muxedStream.output.isClosedForWrite)
        connectionPair.remote.output.writeMplexFrame(CloseFrame(MplexStreamId(true, id)))
        connectionPair.remote.output.flush()
        val exception = assertThrows<ClosedReceiveChannelException> {
            muxedStream.input.readPacket(10)
        }
        assertEquals("Unexpected EOF: expected 10 more bytes", exception.message)
        assertTrue(muxedStream.input.isClosedForRead)
        assertFalse(muxedStream.output.isClosedForWrite)
        muxedStream.close()
        assertCloseFrameReceived(connectionPair.remote)
        mplexMultiplexer.close()
        mplexMultiplexer.awaitClosed()
    }

    @Test
    fun remoteRequestsNewStreamAndLocalCloses() = runTest {
        val connectionPair = TestConnection(pool)
        val mplexMultiplexer = YamuxStreamMuxerConnection(this, connectionPair.local, true)
        repeat(1000) {
            val id = randomId()
            connectionPair.remote.output.writeMplexFrame(NewStreamFrame(id, "aName$id"))
            connectionPair.remote.output.flush()
            val muxedStream = mplexMultiplexer.acceptStream().expectNoErrors()
            assertEquals("aName$id", muxedStream.name)
            assertStreamHasId(false, id, muxedStream)
            muxedStream.output.close()
            yield()
            assertCloseFrameReceived(connectionPair.remote)
            assertFalse(muxedStream.input.isClosedForRead)
            assertTrue(muxedStream.output.isClosedForWrite)
            val exception = assertThrows<CancellationException> {
                muxedStream.output.writeFully(Random.nextBytes(1000))
            }
            assertEquals("The channel was closed", exception.message)
            muxedStream.close()
        }
        mplexMultiplexer.close()
        mplexMultiplexer.awaitClosed()
    }

    @Test
    fun localRequestNewStreamAndCloses() = runTest {
        val connectionPair = TestConnection(pool)
        val mplexMultiplexer = YamuxStreamMuxerConnection(this, connectionPair.local, true)
        repeat(1000) {
            val muxedStream = mplexMultiplexer.openStream("newStreamName$it").expectNoErrors()
            assertEquals("newStreamName$it", muxedStream.name)
            assertEquals(MplexStreamId(true, it.toLong()).toString(), muxedStream.id)
            assertNewStreamFrameReceived(it, "newStreamName$it", connectionPair.remote)
            muxedStream.output.close()
            yield()
            assertCloseFrameReceived(connectionPair.remote)
            assertFalse(muxedStream.input.isClosedForRead)
            assertTrue(muxedStream.output.isClosedForWrite)
            val exception = assertThrows<CancellationException> {
                muxedStream.output.writeFully(Random.nextBytes(1000))
            }
            assertEquals("The channel was closed", exception.message)
            muxedStream.close()
        }
        mplexMultiplexer.close()
        mplexMultiplexer.awaitClosed()
    }

    @Test
    fun localRequestNewStreamAndRemoteCloses() = runTest {
        val connectionPair = TestConnection(pool)
        val mplexMultiplexer = YamuxStreamMuxerConnection(this, connectionPair.local, true)
        repeat(1000) {
            val muxedStream = mplexMultiplexer.openStream("newStreamName$it").expectNoErrors()
            assertEquals("newStreamName$it", muxedStream.name)
            assertEquals(MplexStreamId(true, it.toLong()).toString(), muxedStream.id)
            assertNewStreamFrameReceived(it, "newStreamName$it", connectionPair.remote)
            assertFalse(muxedStream.input.isClosedForRead)
            assertFalse(muxedStream.output.isClosedForWrite)
            connectionPair.remote.output.writeMplexFrame(CloseFrame(MplexStreamId(false, it.toLong())))
            connectionPair.remote.output.flush()
            val exception = assertThrows<ClosedReceiveChannelException> {
                muxedStream.input.readPacket(10)
            }
            assertEquals("Unexpected EOF: expected 10 more bytes", exception.message)
            assertTrue(muxedStream.input.isClosedForRead)
            assertFalse(muxedStream.output.isClosedForWrite)
            muxedStream.close()
            assertCloseFrameReceived(connectionPair.remote)
        }
        mplexMultiplexer.close()
        mplexMultiplexer.awaitClosed()
    }

    @Test
    fun basicStreams() = runTest {
        val connectionPair = TestConnection(pool)
        val muxa = YamuxStreamMuxerConnection(this, connectionPair.local, true)
        val muxb = YamuxStreamMuxerConnection(this, connectionPair.remote, false)
        repeat(100) {
            val random1 = Random.nextBytes(40960)
            val job = launch {
                val sb = muxb.acceptStream().expectNoErrors()
                sb.output.writeFully(random1)
                sb.output.flush()
                sb.close()
                sb.awaitClosed()
            }
            val sa = muxa.openStream().expectNoErrors()
            val random2 = ByteArray(random1.size)
            sa.input.readFully(random2)
            assertArrayEquals(random1, random2)
            job.join()
            sa.close()
        }
        muxa.close()
        muxb.close()
    }

    @Test
    fun echo() = runTest {
        val pipe = TestConnection(pool)
        val muxa = YamuxStreamMuxerConnection(this, pipe.local, true)
        val muxb = YamuxStreamMuxerConnection(this, pipe.remote, false)
        repeat(100) {
            val message = Random.nextBytes(40960)
            val job = launch {
                val sb = muxb.acceptStream().expectNoErrors()
                val buf = ByteArray(message.size)
                sb.input.readFully(buf)
                sb.output.writeFully(buf)
                sb.output.flush()
                sb.close()
            }
            val sa = muxa.openStream().expectNoErrors()
            sa.output.writeFully(message)
            sa.output.flush()
            val buf = ByteArray(message.size)
            sa.input.readFully(buf)
            assertArrayEquals(message, buf)
            job.join()
            sa.close()
        }
        muxa.close()
        muxb.close()
    }

    @Test
    fun stress() = runTest(timeout = 1.minutes) {
        val pipe = TestConnection(pool)
        val muxa = YamuxStreamMuxerConnection(this, pipe.local, true)
        val muxb = YamuxStreamMuxerConnection(this, pipe.remote, false)
        val messageSize = 40960
        repeat(1000) {
            val jobs = mutableListOf<Job>()
            repeat(10) {
                jobs.add(
                    launch {
                        delay(Random.nextLong(1000))
                        val sb = muxb.acceptStream().expectNoErrors()
                        val buf = ByteArray(messageSize)
                        sb.input.readFully(buf)
                        for (i in buf.indices) {
                            buf[i] = buf[i] xor 123
                        }
                        sb.output.writeFully(buf)
                        sb.output.flush()
                        sb.close()
                    },
                )
            }
            repeat(10) {
                jobs.add(
                    launch {
                        val message = Random.nextBytes(messageSize)
                        val sa = muxa.openStream().expectNoErrors()
                        sa.output.writeFully(message)
                        sa.output.flush()
                        val buf = ByteArray(messageSize)
                        sa.input.readFully(buf)
                        for (i in buf.indices) {
                            buf[i] = buf[i] xor 123
                        }
                        assertArrayEquals(message, buf)
                        sa.close()
                    },
                )
            }
            jobs.joinAll()
        }
        muxa.close()
        muxb.close()
    }

    @Test
    fun writeAfterClose() = runTest {
        val pipe = TestConnection(pool)
        val muxa = YamuxStreamMuxerConnection(this, pipe.local, true)
        val muxb = YamuxStreamMuxerConnection(this, pipe.remote, false)
        val message = "Hello world".toByteArray()
        launch {
            val sb = muxb.acceptStream().expectNoErrors()
            sb.output.writeFully(message)
            sb.output.flush()
            sb.close()
            sb.output.writeFully(message)
            sb.output.flush()
        }
        val sa = muxa.openStream().expectNoErrors()
        assertFalse(sa.input.isClosedForRead)
        val buf = ByteArray(message.size)
        sa.input.readFully(buf)
        assertArrayEquals(message, buf)
        assertTrue(sa.input.isClosedForRead)
        val exception1 = assertThrows<ClosedReceiveChannelException> {
            sa.input.readFully(buf)
        }
        assertEquals("Unexpected EOF: expected 11 more bytes", exception1.message)
        sa.close()
        muxa.close()
        muxb.close()
    }

    @Test
    fun slowReader() = runTest {
        val pipe = TestConnection(pool)
        val muxa = YamuxStreamMuxerConnection(this, pipe.local, true)
        val muxb = YamuxStreamMuxerConnection(this, pipe.remote, false)
        val message = "Hello world".toByteArray()
        val sa = muxa.openStream().expectNoErrors()
        val exception = assertThrows<StreamResetException> {
            for (i in 0..10000) {
                sa.output.writeFully(message)
                sa.output.flush()
                yield()
            }
        }
        assertEquals("Stream was reset", exception.message)
        muxa.close()
        muxb.close()
    }

    @Test
    fun acceptingStreamWhileClosing() = runTest {
        val pipe = TestConnection(pool)
        val mux = YamuxStreamMuxerConnection(this, pipe.local, true)
        val job = launch {
            coAssertErrorResult("session shut down") { mux.acceptStream() }
        }
        mux.close()
        job.join()
    }

    @Test
    fun acceptingStreamAfterClose() = runTest {
        val pipe = TestConnection(pool)
        val mux = YamuxStreamMuxerConnection(this, pipe.local, true)
        mux.close()
        coAssertErrorResult("session shut down") { mux.acceptStream() }
    }

    private fun randomId(): Long {
        return Random.nextLong(maxStreamId)
    }

    private fun assertStreamHasId(initiator: Boolean, id: Long, muxedStream: MuxedStream) {
        assertEquals(MplexStreamId(initiator, id).toString(), muxedStream.id)
    }

    private suspend fun assertMessageFrameReceived(expected: ByteArray, connection: Connection) {
        val frame = connection.input.readMplexFrame().expectNoErrors()
        if (frame is MessageFrame) {
            assertArrayEquals(expected, frame.packet.readBytes())
        } else {
            assertFalse(true, "MessageFrame expected")
        }
    }

    private suspend fun assertNewStreamFrameReceived(id: Int, name: String, connection: Connection) {
        val frame = connection.input.readMplexFrame().expectNoErrors()
        if (frame is NewStreamFrame) {
            assertTrue(frame.initiator)
            assertEquals(id.toLong(), frame.id)
            assertEquals(name, frame.name)
        } else {
            assertFalse(true, "NewStreamFrame expected")
        }
    }

    private suspend fun assertCloseFrameReceived(connection: Connection) {
        val frame = connection.input.readMplexFrame().expectNoErrors()
        assertTrue(frame is CloseFrame)
    }
}
