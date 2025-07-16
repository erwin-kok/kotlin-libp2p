// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.resourcemanager

import org.erwinkok.result.Result

interface ResourceScope {
    fun reserveMemory(size: Int, prio: UByte): Result<Unit>
    fun releaseMemory(size: Int)
    fun statistic(): ScopeStatistic
    fun beginSpan(): Result<ResourceScopeSpan>

    companion object {
        const val reservationPriorityLow: UByte = 101u
        const val reservationPriorityMedium: UByte = 152u
        const val reservationPriorityHigh: UByte = 203u
        const val reservationPriorityAlways: UByte = 255u
    }
}
