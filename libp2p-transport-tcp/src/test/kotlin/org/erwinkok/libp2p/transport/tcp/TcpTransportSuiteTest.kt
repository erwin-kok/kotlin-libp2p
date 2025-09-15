// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
@file:OptIn(ExperimentalCoroutinesApi::class)

package org.erwinkok.libp2p.transport.tcp

import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.erwinkok.libp2p.muxer.mplex.MplexStreamMuxerTransport
import org.erwinkok.libp2p.security.plaintext.PlainTextSecureTransport
import org.erwinkok.libp2p.testing.ConnectionBuilder
import org.erwinkok.libp2p.testing.testsuites.transport.TransportTestSuite
import org.erwinkok.libp2p.transport.tcp.TcpTransport.TcpTransportFactory
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream

internal class TcpTransportSuiteTest {
    @TestFactory
    @Disabled
    fun testSuite(): Stream<DynamicTest> {
        val ca = ConnectionBuilder(TcpTransportFactory, PlainTextSecureTransport, MplexStreamMuxerTransport)
        val cb = ConnectionBuilder(TcpTransportFactory, PlainTextSecureTransport, MplexStreamMuxerTransport)
        return TransportTestSuite("Tcp").testTransport(ca, cb, "/ip4/127.0.0.1/tcp/0", ca.identity)
    }
}
