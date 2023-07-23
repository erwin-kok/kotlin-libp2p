// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.base

import java.time.Instant
import kotlin.time.Duration

interface EpochTimeProvider {
    fun time(): Instant

    companion object {
        val system = SystemEpochTimeProvider()
        val test = TestEpochTimeProvider()
    }
}

class SystemEpochTimeProvider : EpochTimeProvider {
    override fun time(): Instant {
        return Instant.now()
    }
}

class TestEpochTimeProvider : EpochTimeProvider {
    private var currentTime = 0L
    fun advanceTime(millis: Long) {
        currentTime += millis
    }

    fun advanceTime(duration: Duration) {
        currentTime += duration.inWholeMilliseconds
    }

    override fun time(): Instant {
        return Instant.ofEpochMilli(currentTime)
    }
}
