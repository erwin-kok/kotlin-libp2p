// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.util

object Bytes {
    fun compare(a: ByteArray?, b: ByteArray?): Int {
        if (a === b) {
            return 0
        }
        if (a == null) {
            return +1
        }
        if (b == null) {
            return -1
        }
        for (i in 0 until Integer.min(a.size, b.size)) {
            val v1 = a[i]
            val v2 = b[i]
            if (v1 < v2) {
                return -1
            }
            if (v1 > v2) {
                return +1
            }
        }
        if (a.size < b.size) {
            return -1
        }
        if (a.size > b.size) {
            return +1
        }
        return 0
    }
}
