// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ed25519.tables

import org.erwinkok.libp2p.crypto.ed25519.Point
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class AffineLookupTableTest {
    @Test
    fun table() {
        val table = AffineLookupTable.fromP3(Point.GeneratorPoint)
        val tmp1 = table.select(3.toByte())
        val tmp2 = table.select((-7).toByte())
        val tmp3 = table.select(4.toByte())

        // Expect T1 + T2 + T3 = identity
        var accP1xP1 = Point.IdentityPoint + tmp1
        accP1xP1 = Point(accP1xP1) + tmp2
        accP1xP1 = Point(accP1xP1) + tmp3
        assertEquals(Point.IdentityPoint, Point(accP1xP1), "Consistency check on AffineLookupTable.SelectInto failed! $tmp1 $tmp2 $tmp3")
    }
}
