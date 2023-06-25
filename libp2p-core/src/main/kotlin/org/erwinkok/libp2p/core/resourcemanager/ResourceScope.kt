// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.resourcemanager

import org.erwinkok.result.Result

interface ResourceScope {
    fun reserveMemory(size: Int, prio: UByte): Result<Unit>
    fun releaseMemory(size: Int)
    fun statistic(): ScopeStatistic
    fun beginSpan(): Result<ResourceScopeSpan>

    companion object {
        const val ReservationPriorityLow: UByte = 101u
        const val ReservationPriorityMedium: UByte = 152u
        const val ReservationPriorityHigh: UByte = 203u
        const val ReservationPriorityAlways: UByte = 255u
    }
}
