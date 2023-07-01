// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ed25519.tables

import org.erwinkok.libp2p.crypto.ed25519.AffineCached
import org.erwinkok.libp2p.crypto.ed25519.Point

// A precomputed lookup table for fixed-base, variable-time scalar muls.
class NafLookupTable8(private val points: Array<AffineCached>) {
    // Given odd x with 0 < x < 2^7, return x*Q (in variable time).
    fun select(x: Byte): AffineCached {
        return AffineCached(points[x / 2])
    }

    override fun hashCode(): Int {
        return points.contentHashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is NafLookupTable8) {
            return super.equals(other)
        }
        return points.contentEquals(other.points)
    }

    companion object {
        // This is not optimised for speed; fixed-base tables should be precomputed.
        fun fromP3(p: Point): NafLookupTable8 {
            val pp = p + p
            val points = mutableListOf(AffineCached(p))
            for (i in 0..62) {
                // Compute (i+1)*Q as Q + i*Q and convert to AffineCached
                // Take the previous element 'list[index]' and add p to it. Convert to AffineCached and iterate.
                points.add(AffineCached(Point(pp + points[i])))
            }
            return NafLookupTable8(points.toTypedArray())
        }
    }
}
