// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.testing.mocknet.done

import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

class RateLimiter(b: Double) {
    private val lock = ReentrantLock()
    private var bandwidth = b / ToNanoSeconds     // bytes per nanosecond
    private var allowance = 0.0
    private var maxAllowance = b
    private var lastUpdate = Instant.now()
    private var count = 0
    private var duration = Duration.ZERO

    fun updateBandwidth(b: Double) {
        lock.withLock {
            this.bandwidth = b / ToNanoSeconds
            this.allowance = 0.0
            this.maxAllowance = b
            this.lastUpdate = Instant.now()
        }
    }

    fun limit(dataSize: Int): Duration {
        lock.withLock {
            var duration = Duration.ZERO
            if (this.bandwidth == 0.0) {
                return duration
            }
            val current = Instant.now()
            val elapsedTime = java.time.Duration.between(current, this.lastUpdate)
            this.lastUpdate = current
            var allowance = this.allowance + elapsedTime.toNanos() * bandwidth
            //  allowance can't exceed bandwidth
            if (allowance > maxAllowance) {
                allowance = maxAllowance
            }
            allowance -= dataSize.toDouble()
            if (allowance < 0) {
                duration = (-allowance / this.bandwidth).nanoseconds
                //  rate limiting was applied, record stats
                count++
                this.duration += duration
            }
            this.allowance = allowance
            return duration
        }
    }

    companion object {
        private const val ToNanoSeconds = 1000000
    }
}
