// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.yamux

import io.ktor.utils.io.ByteChannel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.erwinkok.libp2p.core.network.Connection
import org.erwinkok.libp2p.core.resourcemanager.PeerScope
import org.erwinkok.libp2p.testing.TestWithLeakCheck
import org.erwinkok.libp2p.testing.VerifyingChunkBufferPool
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

internal class YamuxStreamMuxerTransportTest : TestWithLeakCheck {
    override val pool = VerifyingChunkBufferPool()

    @Test
    fun testProtocolId() {
        assertEquals("/yamux/1.0.0", YamuxStreamMuxerTransport.protocolId.id)
    }

//    @Test
//    fun testCreate() = runTest {
//        val transport = YamuxStreamMuxerTransport.create(this).expectNoErrors()
//        assertNotNull(transport)
//        val connection = mockk<Connection>()
//        val peerScope = mockk<PeerScope>()
//        every { connection.input } returns ByteChannel()
//        every { connection.output } returns ByteChannel()
//        val muxerConnection = transport.newConnection(connection, true, peerScope).expectNoErrors()
//        assertNotNull(muxerConnection)
//        muxerConnection.close()
//    }
}
