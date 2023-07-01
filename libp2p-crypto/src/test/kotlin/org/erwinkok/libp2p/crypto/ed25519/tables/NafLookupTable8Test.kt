// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ed25519.tables

import org.erwinkok.libp2p.crypto.ed25519.Point
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class NafLookupTable8Test {
    @Test
    fun table() {
        val table = NafLookupTable8.fromP3(Point.GeneratorPoint)
        val tmp1 = table.select(49.toByte())
        val tmp2 = table.select(11.toByte())
        val tmp3 = table.select(35.toByte())
        val tmp4 = table.select(25.toByte())

        // Expect T1 + T2 = T3 + T4
        val lhs = Point(Point(Point.IdentityPoint + tmp1) + tmp2)
        val rhs = Point(Point(Point.IdentityPoint + tmp3) + tmp4)
        assertEquals(lhs, rhs, "Consistency check on nafLookupTable8 failed")
    }
}
