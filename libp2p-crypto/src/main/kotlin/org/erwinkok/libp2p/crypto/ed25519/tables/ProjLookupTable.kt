// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ed25519.tables

import org.erwinkok.libp2p.crypto.ed25519.Point
import org.erwinkok.libp2p.crypto.ed25519.ProjCached
import org.erwinkok.libp2p.crypto.math.Subtle

// A dynamic lookup table for variable-base, constant-time scalar muls.
class ProjLookupTable(private val points: Array<ProjCached>) {
    // Set dest to x*Q, where -8 <= x <= 8, in constant time.
    fun select(x: Byte): ProjCached {
        // Compute xabs = |x|
        val xmask = (x.toInt() ushr 7)
        val xabs = ((x + xmask) xor xmask)
        var dest = ProjCached.Zero
        for (j in 1..8) {
            // Set dest = j*Q if |x| = j
            val cond = Subtle.constantTimeByteEq(xabs, j)
            dest = ProjCached.select(points[j - 1], dest, cond)
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
        if (other !is ProjLookupTable) {
            return super.equals(other)
        }
        return points.contentEquals(other.points)
    }

    companion object {
        // Builds a lookup table at runtime. Fast.
        fun fromP3(p: Point): ProjLookupTable {
            val points = mutableListOf(ProjCached(p))
            for (i in 0..6) {
                // Compute (i+1)*Q as Q + i*Q and convert to a ProjCached
                // This is needlessly complicated because the API has explicit
                // receivers instead of creating stack objects and relying on RVO
                // Take the previous element 'list[index]' and add p to it. Convert to AffineCached and iterate.
                points.add(ProjCached(Point(p + points[i])))
            }
            return ProjLookupTable(points.toTypedArray())
        }
    }
}
