// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ed25519.tables

import org.erwinkok.libp2p.crypto.ed25519.AffineCached
import org.erwinkok.libp2p.crypto.ed25519.Point
import org.erwinkok.libp2p.crypto.math.Subtle

// A precomputed lookup table for fixed-base, constant-time scalar muls.
class AffineLookupTable(private val points: Array<AffineCached>) {
    // Set dest to x*Q, where -8 <= x <= 8, in constant time.
    fun select(x: Byte): AffineCached {
        // Compute xabs = |x|
        val xmask = (x.toInt() ushr 7)
        val xabs = ((x + xmask) xor xmask)
        var dest = AffineCached.Zero
        for (j in 1..8) {
            // Set dest = j*Q if |x| = j
            val cond = Subtle.constantTimeByteEq(xabs, j)
            dest = AffineCached.select(points[j - 1], dest, cond)
        }
        // Now dest = |x|*Q, conditionally negate to get x*Q
        return dest.negateConditionally((xmask and 1) != 0)
    }

    override fun hashCode(): Int {
        return points.contentHashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is AffineLookupTable) {
            return super.equals(other)
        }
        return points.contentEquals(other.points)
    }

    companion object {
        // This is not optimised for speed; fixed-base tables should be precomputed.
        fun fromP3(p: Point): AffineLookupTable {
            // Goal: v.points[i] = (i+1)*Q, i.e., Q, 2Q, ..., 8Q
            // This allows lookup of -8Q, ..., -Q, 0, Q, ..., 8Q
            val points = mutableListOf(AffineCached(p))
            for (i in 0..6) {
                // Compute (i+1)*Q as Q + i*Q and convert to AffineCached
                // Take the previous element 'list[index]' and add p to it. Convert to AffineCached and iterate.
                points.add(AffineCached(Point(p + points[i])))
            }
            return AffineLookupTable(points.toTypedArray())
        }
    }
}
