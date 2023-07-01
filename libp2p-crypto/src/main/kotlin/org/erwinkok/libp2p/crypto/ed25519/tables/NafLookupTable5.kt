// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ed25519.tables

import org.erwinkok.libp2p.crypto.ed25519.Point
import org.erwinkok.libp2p.crypto.ed25519.ProjCached

// A dynamic lookup table for variable-base, variable-time scalar muls.
class NafLookupTable5(private val points: Array<ProjCached>) {
    // Given odd x with 0 < x < 2^4, return x*Q (in variable time).
    fun select(x: Byte): ProjCached {
        return ProjCached(points[x / 2])
    }

    override fun hashCode(): Int {
        return points.contentHashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is NafLookupTable5) {
            return super.equals(other)
        }
        return points.contentEquals(other.points)
    }

    companion object {
        // Builds a lookup table at runtime. Fast.
        fun fromP3(p: Point): NafLookupTable5 {
            // Goal: v.points[i] = (2*i+1)*Q, i.e., Q, 3Q, 5Q, ..., 15Q
            // This allows lookup of -15Q, ..., -3Q, -Q, 0, Q, 3Q, ..., 15Q
            val pp = p + p
            val points = mutableListOf(ProjCached(p))
            for (i in 0..6) {
                // Compute (i+1)*Q as Q + i*Q and convert to AffineCached
                // Take the previous element 'list[index]' and add p to it. Convert to AffineCached and iterate.
                points.add(ProjCached(Point(pp + points[i])))
            }
            return NafLookupTable5(points.toTypedArray())
        }
    }
}
