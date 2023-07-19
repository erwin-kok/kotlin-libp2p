// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.util

import kotlin.random.Random

object StringUtils {
    private val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

    fun randomString(size: Int = 10): String {
        return (1..size)
            .map { Random.nextInt(0, charPool.size).let { charPool[it] } }
            .joinToString("")
    }
}
