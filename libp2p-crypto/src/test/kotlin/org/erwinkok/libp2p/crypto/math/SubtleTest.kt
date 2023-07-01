// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.math

import org.erwinkok.util.Tuple3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.security.SecureRandom
import java.util.stream.Stream

internal class SubtleTest {
    @TestFactory
    fun constantTimeCompare(): Stream<DynamicTest> {
        return listOf(
            Tuple3(byteArrayOf(), byteArrayOf(), true),
            Tuple3(byteArrayOf(0x11), byteArrayOf(0x11), true),
            Tuple3(byteArrayOf(0x12), byteArrayOf(0x11), false),
            Tuple3(byteArrayOf(0x11), byteArrayOf(0x11, 0x12), false),
            Tuple3(byteArrayOf(0x11, 0x12), byteArrayOf(0x11), false)
        ).map { (a, b, out) ->
            DynamicTest.dynamicTest("Test: $a, $b -> $out") {
                assertEquals(out, Subtle.constantTimeCompare(a, b))
            }
        }.stream()
    }

    @TestFactory
    fun constantTimeByteEq(): Stream<DynamicTest> {
        return listOf(
            Tuple3(0, 0, true),
            Tuple3(0, 1, false),
            Tuple3(1, 0, false),
            Tuple3(0xff, 0xff, true),
            Tuple3(0xff, 0xfe, false)
        ).map { (a, b, out) ->
            DynamicTest.dynamicTest("Test: $a, $b -> $out") {
                assertEquals(out, Subtle.constantTimeByteEq(a, b))
            }
        }.stream()
    }

    @Test
    fun constantTimeByteEqRandom() {
        val r = SecureRandom()
        for (i in 0..1024) {
            val x = r.nextInt().toByte()
            val y = r.nextInt().toByte()
            assertEquals(x == y, Subtle.constantTimeByteEq(x.toInt(), y.toInt()), "$x == $y")
        }
    }
}
