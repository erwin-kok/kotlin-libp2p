// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.host

import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.event.EventBus
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.Network
import org.erwinkok.libp2p.core.network.Stream
import org.erwinkok.libp2p.core.network.StreamHandler
import org.erwinkok.libp2p.core.peerstore.Peerstore
import org.erwinkok.libp2p.core.record.AddressInfo
import org.erwinkok.multiformat.multistream.MultistreamMuxer
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.Result

interface Host : AwaitableClosable {
    val id: PeerId
    val peerstore: Peerstore
    val network: Network
    val multistreamMuxer: MultistreamMuxer<Stream>
    val eventBus: EventBus

    fun addresses(): List<InetMultiaddress>
    suspend fun connect(addressInfo: AddressInfo): Result<Unit>
    fun setStreamHandler(protocolId: ProtocolId, handler: StreamHandler)
    fun setStreamHandlerMatch(protocolId: ProtocolId, matcher: (ProtocolId) -> Boolean, handler: StreamHandler)
    fun removeStreamHandler(protocolId: ProtocolId)
    suspend fun newStream(peerId: PeerId, protocols: Set<ProtocolId>): Result<Stream>
    suspend fun newStream(peerId: PeerId, protocol: ProtocolId): Result<Stream> {
        return newStream(peerId, setOf(protocol))
    }
}
