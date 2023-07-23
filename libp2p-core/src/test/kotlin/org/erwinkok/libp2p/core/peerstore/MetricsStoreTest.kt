// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
@file:OptIn(ExperimentalCoroutinesApi::class)

package org.erwinkok.libp2p.core.peerstore

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

internal class MetricsStoreTest {
    @Test
    fun latencyEWMA() = runTest {
        repeat(10000) {
            val metricsStore = MetricsStore.create().expectNoErrors()
            val id = LocalIdentity.random().expectNoErrors()
            repeat(20) {
                val v = Random.nextLong(1000)
                metricsStore.recordLatency(id.peerId, v)
            }
            val lat = metricsStore.latencyEWMA(id.peerId)
            assertNotNull(lat)
            assertTrue(lat < 1000)
        }
    }
}
