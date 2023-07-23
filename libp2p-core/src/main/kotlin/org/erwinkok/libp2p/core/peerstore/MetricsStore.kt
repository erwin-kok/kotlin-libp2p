// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.peerstore

import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import java.util.concurrent.ConcurrentHashMap

class MetricsStore private constructor() {
    private val latencyMap = ConcurrentHashMap<PeerId, Long>()

    fun recordLatency(peerId: PeerId, next: Long) {
        var s = LatencyEWMASmoothing
        if (s > 1.0 || s < 0.0) {
            s = 0.1 // ignore the knob. it's broken. look, it jiggles.
        }
        latencyMap.compute(peerId) { _, oldDuration ->
            if (oldDuration == null) {
                // when no data, just take it as the mean.
                next
            } else {
                (((1.0 - s) * oldDuration) + (s * next)).toLong()
            }
        }
    }

    fun latencyEWMA(peerId: PeerId): Long {
        return latencyMap[peerId] ?: 0L
    }

    fun removePeer(peerId: PeerId) {
        latencyMap.remove(peerId)
    }

    companion object {
        // LatencyEWMASmoothing governs the decay of the EWMA (the speed at which it changes). This must be a normalized (0-1) value.
        // 1 is 100% change, 0 is no change.
        private const val LatencyEWMASmoothing = 0.1

        fun create(): Result<MetricsStore> {
            return Ok(MetricsStore())
        }
    }
}
