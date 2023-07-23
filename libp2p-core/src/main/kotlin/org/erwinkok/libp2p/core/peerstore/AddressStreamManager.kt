// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.peerstore

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.InetMultiaddress

class AddressStreamManager {
    private val mutex = Mutex()
    private val addressFlowMap = mutableMapOf<PeerId, MutableSharedFlow<InetMultiaddress>>()

    suspend fun addressStream(peerId: PeerId): SharedFlow<InetMultiaddress> {
        mutex.withLock {
            val sharedFlow = addressFlowMap.computeIfAbsent(peerId) { MutableSharedFlow(100) }
            return sharedFlow.asSharedFlow()
        }
    }

    suspend fun emit(peerId: PeerId, address: InetMultiaddress) {
        mutex.withLock {
            val sharedFlow = addressFlowMap.computeIfAbsent(peerId) { MutableSharedFlow(100) }
            sharedFlow.emit(address)
        }
    }
}
