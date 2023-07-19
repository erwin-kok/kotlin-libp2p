// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.network.securitymuxer

import org.erwinkok.libp2p.testing.TestWithLeakCheck
import org.erwinkok.libp2p.testing.VerifyingChunkBufferPool

internal class SecureMuxerImplTest : TestWithLeakCheck {
    override val pool = VerifyingChunkBufferPool()

//    @Test
//    fun commonProto() = runTest {
//        val idA = LocalIdentity.random(KeyType.ED25519, 256).expectNoErrors()
//        val idB = LocalIdentity.random(KeyType.ED25519, 256).expectNoErrors()
//        val atInsecure = PlainTextSecureTransport.create(this, idA).expectNoErrors()
//        val btInsecure = PlainTextSecureTransport.create(this, idB).expectNoErrors()
//        val at = SecurityMuxer()
//        val bt = SecurityMuxer()
//        at.addTransport(ProtocolId.from("/plaintext/1.0.0"), atInsecure)
//        bt.addTransport(ProtocolId.from("/plaintext/1.1.0"), btInsecure)
//        bt.addTransport(ProtocolId.from("/plaintext/1.0.0"), btInsecure)
//        SecurityTestSuite.subtestRW(TransportAdapter(at), TransportAdapter(bt), idA.peerId, idB.peerId)
//    }
//
//    @Test
//    fun noCommonProto() = runTest {
//        val idA = LocalIdentity.random(KeyType.ED25519, 256).expectNoErrors()
//        val idB = LocalIdentity.random(KeyType.ED25519, 256).expectNoErrors()
//        val atInsecure = PlainTextSecureTransport.create(this, idA).expectNoErrors()
//        val btInsecure = PlainTextSecureTransport.create(this, idB).expectNoErrors()
//        val at = SecurityMuxer()
//        val bt = SecurityMuxer()
//        at.addTransport(ProtocolId.from("/plaintext/1.0.0"), atInsecure)
//        bt.addTransport(ProtocolId.from("/plaintext/1.1.0"), btInsecure)
//        val connection = TestConnection(pool)
//        val job1 = launch {
//            coAssertErrorResult("EndOfStream") { at.secureInbound(connection.local, null) }
//            connection.local.close()
//        }
//        val job2 = launch {
//            coAssertErrorResult("Peer does not support any of the given protocols") { bt.secureOutbound(connection.remote, idA.peerId) }
//            connection.remote.close()
//        }
//        job1.join()
//        job2.join()
//    }
//
//    internal inner class TransportAdapter(private val mux: SecurityMuxer) : SecureTransport {
//        override val localIdentity: LocalIdentity
//            get() = error("Not implemented")
//
//        override suspend fun secureInbound(insecureConnection: Connection, peerId: PeerId?): Result<SecureConnection> {
//            return mux.secureInbound(insecureConnection, peerId)
//                .map { it.secureConnection }
//        }
//
//        override suspend fun secureOutbound(insecureConnection: Connection, peerId: PeerId): Result<SecureConnection> {
//            return mux.secureOutbound(insecureConnection, peerId)
//                .map { it.secureConnection }
//        }
//    }
}
