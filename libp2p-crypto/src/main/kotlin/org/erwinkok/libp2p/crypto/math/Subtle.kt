// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.math

import kotlin.experimental.or
import kotlin.experimental.xor

object Subtle {
    // ConstantTimeCompare returns 1 if the two slices, x and y, have equal contents
    // and 0 otherwise. The time taken is a function of the length of the slices and
    // is independent of the contents.
    fun constantTimeCompare(x: ByteArray, y: ByteArray): Boolean {
        if (x.size != y.size) {
            return false
        }
        var v: Byte = 0
        for (i in x.indices) {
            v = v or (x[i] xor y[i])
        }
        return constantTimeByteEq(v.toInt(), 0)
    }

    // ConstantTimeByteEq returns 1 if x == y and 0 otherwise.
    fun constantTimeByteEq(x: Int, y: Int): Boolean {
        return ((x xor y) and 0xff) - 1 ushr 31 != 0
    }

    fun constantTimeEq(a: UInt, b: UInt): UInt {
        return (((a xor b).toULong() - 1u) shr 63).toUInt()
    }

    fun constantTimeNotEq(a: UInt, b: UInt): UInt {
        return (((a xor b).toULong() - 1u) shr 63).inv().toUInt() and 1u
    }

    fun constantTimeLess(a: UInt, b: UInt): UInt {
        return ((a.toULong() - b.toULong()) shr 63).toUInt()
    }

    fun constantTimeLessOrEq(a: UInt, b: UInt): UInt {
        return ((a.toULong() - b.toULong() - 1u) shr 63).toUInt()
    }

    fun constantTimeGreater(a: UInt, b: UInt): UInt {
        return constantTimeLess(b, a)
    }

    fun constantTimeGreaterOrEq(a: UInt, b: UInt): UInt {
        return constantTimeLessOrEq(b, a)
    }

    fun constantTimeMin(a: UInt, b: UInt): UInt {
        return b xor ((a xor b) and (-constantTimeLess(a, b).toInt()).toUInt())
    }
}
