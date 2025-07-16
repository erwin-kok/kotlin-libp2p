// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.secp256k1

import org.erwinkok.util.Tuple4
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream

internal class Accumulator96Test {
    @TestFactory
    fun add(): Stream<DynamicTest> {
        return listOf(
            Tuple4("0 + 0 = 0", Accumulator96.Zero, 0uL, Accumulator96.Zero),
            Tuple4("overflow in word zero", Accumulator96(0xffffffffu, 0u, 0u), 1uL, Accumulator96(0u, 1u, 0u)),
            Tuple4("overflow in word one", Accumulator96(0u, 0xffffffffu, 0u), 0x100000000u, Accumulator96(0u, 0u, 1u)),
            Tuple4("overflow in words one and two", Accumulator96(0xffffffffu, 0xffffffffu, 0u), 1uL, Accumulator96(0u, 0u, 1u)),
            // Start accumulator at 129127208455837319175 which is the result of
            // 4294967295 * 4294967295 accumulated seven times.
            Tuple4("max result from eight adds of max uint32 multiplications", Accumulator96(7u, 4294967282u, 6u), 18446744065119617025uL, Accumulator96(8u, 4294967280u, 7u)),
        ).map { (name: String, start: Accumulator96, input: ULong, expected: Accumulator96) ->
            DynamicTest.dynamicTest("Test: $name") {
                start.add(input)
                assertEquals(expected, start)
            }
        }.stream()
    }
}
