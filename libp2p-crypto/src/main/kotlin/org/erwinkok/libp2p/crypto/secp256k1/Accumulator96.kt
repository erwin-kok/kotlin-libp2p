// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.secp256k1

import org.erwinkok.libp2p.crypto.math.Subtle.constantTimeLess

// accumulator96 provides a 96-bit accumulator for use in the intermediate
// calculations requiring more than 64-bits.
class Accumulator96(private var n0: UInt, private var n1: UInt, private var n2: UInt) {
    // Add adds the passed unsigned 64-bit value to the accumulator.
    fun add(v: ULong) {
        val low = (v and uint32Mask).toUInt()
        var hi = (v shr 32).toUInt()
        n0 += low
        hi += constantTimeLess(n0, low) // Carry if overflow in n[0].
        n1 += hi
        n2 += constantTimeLess(n1, hi) // Carry if overflow in n[1].
    }

    fun rsh32(): ULong {
        val r = n0
        n0 = n1
        n1 = n2
        n2 = 0u
        return r.toULong()
    }

    override fun hashCode(): Int {
        var result = 1u
        result = 31u * result + n0
        result = 31u * result + n1
        result = 31u * result + n2
        return result.toInt()
    }

    // Equals returns whether or not the two field values are the same.  Both
    // field values being compared must be normalized for this function to return
    // the correct result.
    override fun equals(other: Any?): Boolean {
        // Xor only sets bits when they are different, so the two field values
        // can only be the same if no bits are set after xoring each word.
        // This is a constant time implementation.
        if (other === this) {
            return true
        }
        if (other !is Accumulator96) {
            return super.equals(other)
        }
        val bits = n0 xor other.n0 or (n1 xor other.n1) or (n2 xor other.n2)
        return bits == 0u
    }

    override fun toString(): String {
        return "$n0$n1$n2"
    }

    companion object {
        // uint32Mask is simply a mask with all bits set for a uint32 and is used to
        // improve the readability of the code.
        private const val uint32Mask = 0xffffffffuL

        val Zero: Accumulator96
            get() = Accumulator96(0u, 0u, 0u)
    }
}
