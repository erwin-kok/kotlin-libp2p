// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.yamux

import org.erwinkok.result.Ok
import org.erwinkok.result.Result

interface MemoryManager {
    fun reserveMemory(size: Int, prio: UByte): Result<Unit>
    fun releaseMemory(size: Int)
    fun done()

    companion object {
        val NullMemoryManager = object : MemoryManager {
            override fun reserveMemory(size: Int, prio: UByte): Result<Unit> {
                return Ok(Unit)
            }

            override fun releaseMemory(size: Int) {
            }

            override fun done() {
            }
        }
    }
}
