// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.math

import org.erwinkok.util.Tuple2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.util.stream.Stream
import kotlin.random.Random

internal class BigIntTest {
    @TestFactory
    fun fromDecimal(): Stream<DynamicTest> {
        return listOf(
            Tuple2("0", BigInteger.valueOf(0)),
            Tuple2("1", BigInteger.valueOf(1)),
            Tuple2("-1", BigInteger.valueOf(-1)),
            Tuple2("100", BigInteger.valueOf(100)),
            Tuple2("-100", BigInteger.valueOf(-100)),
        ).map { (a, b) ->
            DynamicTest.dynamicTest("Test: $a ->  $b") {
                assertEquals(BigInt.fromDecimal(a), b)
            }
        }.stream()
    }

    @Test
    fun fromDecimalRandom() {
        for (i in 0..1024) {
            val x = Random.nextLong()
            assertEquals(BigInteger.valueOf(x), BigInt.fromDecimal(x.toString()))
        }
    }

    @TestFactory
    fun fromHex(): Stream<DynamicTest> {
        return listOf(
            Tuple2("0", BigInteger.valueOf(0)),
            Tuple2("1", BigInteger.valueOf(1)),
            Tuple2("-1", BigInteger.valueOf(-1)),
            Tuple2("100", BigInteger.valueOf(256)),
            Tuple2("-100", BigInteger.valueOf(-256)),
        ).map { (a, b) ->
            DynamicTest.dynamicTest("Test: $a ->  $b") {
                assertEquals(BigInt.fromHex(a), b)
            }
        }.stream()
    }

    @Test
    fun fromHexRandom() {
        for (i in 0..1024) {
            val x = Random.nextLong()
            assertEquals(BigInteger.valueOf(x), BigInt.fromDecimal(x.toString()))
        }
    }
}
