// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class Publisher<T : EventType>(replay: Int) {
    private val mutableSharedFlow = MutableSharedFlow<T>(replay = replay, extraBufferCapacity = Int.MAX_VALUE)

    fun tryPublish(event: T): Boolean {
        return mutableSharedFlow.tryEmit(event)
    }

    suspend fun publish(event: T) {
        mutableSharedFlow.emit(event)
    }

    fun asSharedFlow(): SharedFlow<T> {
        return mutableSharedFlow.asSharedFlow()
    }
}
