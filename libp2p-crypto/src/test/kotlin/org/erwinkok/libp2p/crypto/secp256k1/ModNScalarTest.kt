// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
@file:OptIn(ExperimentalUnsignedTypes::class)

package org.erwinkok.libp2p.crypto.secp256k1

import org.erwinkok.libp2p.crypto.math.BigInt
import org.erwinkok.util.Hex
import org.erwinkok.util.Tuple
import org.erwinkok.util.Tuple2
import org.erwinkok.util.Tuple3
import org.erwinkok.util.Tuple4
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.security.SecureRandom
import java.util.stream.Stream
import kotlin.experimental.xor

internal class ModNScalarTest {
    @Test
    fun zero() {
        val s = ModNScalar.Zero
        assertTrue(s.isZero)
    }

    @TestFactory
    fun setInt(): Stream<DynamicTest> {
        return listOf(
            Tuple3("five", 5u, uintArrayOf(5u, 0u, 0u, 0u, 0u, 0u, 0u, 0u)),
            Tuple3("group order word zero", 0xd0364141u, uintArrayOf(0xd0364141u, 0u, 0u, 0u, 0u, 0u, 0u, 0u)),
            Tuple3("group order word zero + 1", 0xd0364141u + 1u, uintArrayOf(0xd0364141u + 1u, 0u, 0u, 0u, 0u, 0u, 0u, 0u)),
            Tuple3("2^32 - 1", 4294967295u, uintArrayOf(4294967295u, 0u, 0u, 0u, 0u, 0u, 0u, 0u)),
        ).map { (name: String, input: UInt, expected: UIntArray) ->
            DynamicTest.dynamicTest("Test: $name") {
                val s = ModNScalar.setInt(input)
                assertModNScalar(expected, s)
            }
        }.stream()
    }

    @TestFactory
    fun setBytes(): Stream<DynamicTest> {
        return listOf(
            Tuple3("zero", "00", uintArrayOf(0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u)),
            Tuple3("group order (aka 0)", "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", uintArrayOf(0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u)),
            Tuple3("group order - 1", "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140", uintArrayOf(0xd0364140u, 0xbfd25e8cu, 0xaf48a03bu, 0xbaaedce6u, 0xfffffffeu, 0xffffffffu, 0xffffffffu, 0xffffffffu)),
            Tuple3("group order + 1 (aka 1, overflow in word zero)", "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364142", uintArrayOf(1u, 0u, 0u, 0u, 0u, 0u, 0u, 0u)),
            Tuple3("group order word zero", "d0364141", uintArrayOf(0xd0364141u, 0u, 0u, 0u, 0u, 0u, 0u, 0u)),
            Tuple3("group order word zero and one", "bfd25e8cd0364141", uintArrayOf(0xd0364141u, 0xbfd25e8cu, 0u, 0u, 0u, 0u, 0u, 0u)),
            Tuple3("group order words zero, one, and two", "af48a03bbfd25e8cd0364141", uintArrayOf(0xd0364141u, 0xbfd25e8cu, 0xaf48a03bu, 0u, 0u, 0u, 0u, 0u)),
            Tuple3("overflow in word one", "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8dd0364141", uintArrayOf(0u, 1u, 0u, 0u, 0u, 0u, 0u, 0u)),
            Tuple3("overflow in word two", "fffffffffffffffffffffffffffffffebaaedce6af48a03cbfd25e8cd0364141", uintArrayOf(0u, 0u, 1u, 0u, 0u, 0u, 0u, 0u)),
            Tuple3("overflow in word three", "fffffffffffffffffffffffffffffffebaaedce7af48a03bbfd25e8cd0364141", uintArrayOf(0u, 0u, 0u, 1u, 0u, 0u, 0u, 0u)),
            Tuple3("overflow in word four", "ffffffffffffffffffffffffffffffffbaaedce6af48a03bbfd25e8cd0364141", uintArrayOf(0u, 0u, 0u, 0u, 1u, 0u, 0u, 0u)),
            Tuple3("(group order - 1) * 2 NOT mod N, truncated >32 bytes", "01fffffffffffffffffffffffffffffffd755db9cd5e9140777fa4bd19a06c8284", uintArrayOf(0x19a06c82u, 0x777fa4bdu, 0xcd5e9140u, 0xfd755db9u, 0xffffffffu, 0xffffffffu, 0xffffffffu, 0x01ffffffu)),
            Tuple3("alternating bits", "a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5", uintArrayOf(0xa5a5a5a5u, 0xa5a5a5a5u, 0xa5a5a5a5u, 0xa5a5a5a5u, 0xa5a5a5a5u, 0xa5a5a5a5u, 0xa5a5a5a5u, 0xa5a5a5a5u)),
            Tuple3("alternating bits 2", "5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a", uintArrayOf(0x5a5a5a5au, 0x5a5a5a5au, 0x5a5a5a5au, 0x5a5a5a5au, 0x5a5a5a5au, 0x5a5a5a5au, 0x5a5a5a5au, 0x5a5a5a5au)),
        ).map { (name: String, input: String, expected: UIntArray) ->
            DynamicTest.dynamicTest("Test: $name") {
                val inBytes = Hex.decodeOrThrow(input)
                val s = ModNScalar.setByteSlice(inBytes)
                assertModNScalar(expected, s)
            }
        }.stream()
    }

    @TestFactory
    fun bytes(): Stream<DynamicTest> {
        return listOf(
            Tuple3("zero", "00", "0000000000000000000000000000000000000000000000000000000000000000"),
            Tuple3("group order (aka 0)", "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", "0000000000000000000000000000000000000000000000000000000000000000"),
            Tuple3("group order - 1", "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140", "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140"),
            Tuple3("group order + 1 (aka 1, overflow in word zero)", "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364142", "0000000000000000000000000000000000000000000000000000000000000001"),
            Tuple3("group order word zero", "d0364141", "00000000000000000000000000000000000000000000000000000000d0364141"),
            Tuple3("group order word zero and one", "bfd25e8cd0364141", "000000000000000000000000000000000000000000000000bfd25e8cd0364141"),
            Tuple3("group order words zero, one, and two", "af48a03bbfd25e8cd0364141", "0000000000000000000000000000000000000000af48a03bbfd25e8cd0364141"),
            Tuple3("overflow in word one", "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8dd0364141", "0000000000000000000000000000000000000000000000000000000100000000"),
            Tuple3("overflow in word two", "fffffffffffffffffffffffffffffffebaaedce6af48a03cbfd25e8cd0364141", "0000000000000000000000000000000000000000000000010000000000000000"),
            Tuple3("overflow in word three", "fffffffffffffffffffffffffffffffebaaedce7af48a03bbfd25e8cd0364141", "0000000000000000000000000000000000000001000000000000000000000000"),
            Tuple3("overflow in word four", "ffffffffffffffffffffffffffffffffbaaedce6af48a03bbfd25e8cd0364141", "0000000000000000000000000000000100000000000000000000000000000000"),
            Tuple3("alternating bits", "a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5", "a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5"),
            Tuple3("alternating bits 2", "5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a", "5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a"),
        ).map { (name: String, input: String, expected: String) ->
            DynamicTest.dynamicTest("Test: $name") {
                val s = setHex(input)
                val expectedBytes = Hex.decodeOrThrow(expected)
                assertArrayEquals(expectedBytes, s.bytes())
            }
        }.stream()
    }

    @TestFactory
    fun isOdd(): Stream<DynamicTest> {
        return listOf(
            Tuple3("zero", "0", false),
            Tuple3("one", "1", true),
            Tuple3("two", "2", false),
            Tuple3("2^32 - 1", "ffffffff", true),
            Tuple3("2^64 - 2", "fffffffffffffffe", false),
            Tuple3("group order (aka 0)", "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", false),
            Tuple3("group order + 1 (aka 1)", "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364142", true),
        ).map { (name: String, input: String, expected: Boolean) ->
            DynamicTest.dynamicTest("Test: $name") {
                val s = setHex(input)
                assertEquals(expected, s.isOdd)
            }
        }.stream()
    }

    @TestFactory
    fun equals(): Stream<DynamicTest> {
        return listOf(
            Tuple4("0 == 0?", "0", "0", true),
            Tuple4("0 == 1?", "0", "1", false),
            Tuple4("1 == 0?", "1", "0", false),
            Tuple4("2^32 - 1 == 2^32 - 1?", "ffffffff", "ffffffff", true),
            Tuple4("2^64 - 1 == 2^64 - 2?", "ffffffffffffffff", "fffffffffffffffe", false),
            Tuple4("0 == group order?", "0", "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", true),
            Tuple4("1 == group order + 1?", "1", "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364142", true),
        ).map { (name: String, in1: String, in2: String, expected: Boolean) ->
            DynamicTest.dynamicTest("Test: $name") {
                val s1 = setHex(in1)
                val s2 = setHex(in2)
                assertEquals(expected, s1 == s2)
            }
        }.stream()
    }

    @Test
    fun equalsRandom() {
        val r = SecureRandom()
        for (i in 0..1024) {
            val (_, s) = randIntAndModNScalar()
            assertTrue(s.equals(s))
            val s2 = ModNScalar.setBytes(s.bytes())
            assertTrue(s == s2)
            val b32 = s.bytes()
            val index = r.nextInt(b32.size)
            val flip = (1 shl r.nextInt(7)).toByte()
            b32[index] = b32[index] xor flip
            val s3 = ModNScalar.setBytes(b32)
            assertTrue(s != s3)
        }
    }

    @TestFactory
    fun add(): Stream<DynamicTest> {
        return listOf(
            Tuple4("zero + one", "0", "1", "1"),
            Tuple4("one + zero", "1", "0", "1"),
            Tuple4("group order (aka 0) + 1 (gets reduced, no overflow)", "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", "1", "1"),
            Tuple4("group order - 1 + 1 (aka 0, overflow to prime)", "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140", "1", "0"),
            Tuple4("group order - 1 + 2 (aka 1, overflow in word zero)", "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140", "2", "1"),
            Tuple4("overflow in word one", "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8bd0364141", "100000001", "1"),
            Tuple4("overflow in word two", "fffffffffffffffffffffffffffffffebaaedce6af48a03abfd25e8cd0364141", "10000000000000001", "1"),
            Tuple4("overflow in word three", "fffffffffffffffffffffffffffffffebaaedce5af48a03bbfd25e8cd0364141", "1000000000000000000000001", "1"),
            Tuple4("overflow in word four", "fffffffffffffffffffffffffffffffdbaaedce6af48a03bbfd25e8cd0364141", "100000000000000000000000000000001", "1"),
            Tuple4("overflow in word five", "fffffffffffffffffffffffefffffffebaaedce6af48a03bbfd25e8cd0364141", "10000000000000000000000000000000000000001", "1"),
            Tuple4("overflow in word six", "fffffffffffffffefffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", "1000000000000000000000000000000000000000000000001", "1"),
            Tuple4("overflow in word seven", "fffffffefffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", "100000000000000000000000000000000000000000000000000000001", "1"),
            Tuple4("alternating bits", "a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5", "5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a", "14551231950b75fc4402da1732fc9bebe"),
            Tuple4("alternating bits 2", "5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a", "a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5", "14551231950b75fc4402da1732fc9bebe"),
        ).map { (name: String, in1: String, in2: String, expected: String) ->
            DynamicTest.dynamicTest("Test: $name") {
                val s1 = setHex(in1)
                val s2 = setHex(in2)
                val exp = setHex(expected)
                assertEquals(exp, s1 + s2)
            }
        }.stream()
    }

    @Test
    fun addRandom() {
        for (i in 0..1024) {
            val (bigIntVal1, modNVal1) = randIntAndModNScalar()
            val (bigIntVal2, modNVal2) = randIntAndModNScalar()
            val bigIntResult = bigIntVal1.add(bigIntVal2).mod(Secp256k1Curve.n)
            val modNValResult = modNVal1 + modNVal2
            assertEquals(bigIntResult.toString(16), modNValResult.toString())
        }
    }

    @TestFactory
    fun mul(): Stream<DynamicTest> {
        return listOf(
            Tuple4("zero * zero", "0", "0", "0"),
            Tuple4("one * zero", "1", "0", "0"),
            Tuple4("one * one", "1", "1", "1"),
            Tuple4("(group order-1) * 2", "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140", "2", "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd036413f"),
            Tuple4("(group order-1) * (group order-1)", "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140", "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140", "1"),
            Tuple4("slightly over group order", "7fffffffffffffffffffffffffffffff5d576e7357a4501ddfe92f46681b20a1", "2", "1"),
            Tuple4("group order (aka 0) * 3", "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", "3", "0"),
            Tuple4("overflow in word eight", "100000000000000000000000000000000", "100000000000000000000000000000000", "14551231950b75fc4402da1732fc9bebf"),
            Tuple4("overflow in word nine", "1000000000000000000000000000000000000", "1000000000000000000000000000000000000", "14551231950b75fc4402da1732fc9bebf00000000"),
            Tuple4("overflow in word ten", "10000000000000000000000000000000000000000", "10000000000000000000000000000000000000000", "14551231950b75fc4402da1732fc9bebf0000000000000000"),
            Tuple4("overflow in word eleven", "100000000000000000000000000000000000000000000", "100000000000000000000000000000000000000000000", "14551231950b75fc4402da1732fc9bebf000000000000000000000000"),
            Tuple4("overflow in word twelve", "1000000000000000000000000000000000000000000000000", "1000000000000000000000000000000000000000000000000", "4551231950b75fc4402da1732fc9bec04551231950b75fc4402da1732fc9bebf"),
            Tuple4("overflow in word thirteen", "10000000000000000000000000000000000000000000000000000", "10000000000000000000000000000000000000000000000000000", "50b75fc4402da1732fc9bec09d671cd51b343a1b66926b57d2a4c1c61536bda7"),
            Tuple4("overflow in word fourteen", "100000000000000000000000000000000000000000000000000000000", "100000000000000000000000000000000000000000000000000000000", "402da1732fc9bec09d671cd581c69bc59509b0b074ec0aea8f564d667ec7eb3c"),
            Tuple4("overflow in word fifteen", "1000000000000000000000000000000000000000000000000000000000000", "1000000000000000000000000000000000000000000000000000000000000", "2fc9bec09d671cd581c69bc5e697f5e41f12c33a0a7b6f4e3302b92ea029cecd"),
            Tuple4("double overflow in internal accumulator", "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140", "55555555555555555555555555555554e8e4f44ce51835693ff0ca2ef01215c2", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa9d1c9e899ca306ad27fe1945de0242b7f"),
            Tuple4("alternating bits", "a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5", "5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a", "88edea3d29272800e7988455cfdf19b039dbfbb1c93b5b44a48c2ba462316838"),
            Tuple4("alternating bits 2", "5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a", "a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5", "88edea3d29272800e7988455cfdf19b039dbfbb1c93b5b44a48c2ba462316838"),
        ).map { (name: String, in1: String, in2: String, expected: String) ->
            DynamicTest.dynamicTest("Test: $name") {
                val s1 = setHex(in1)
                val s2 = setHex(in2)
                val exp = setHex(expected)
                assertEquals(exp, s1 * s2)
            }
        }.stream()
    }

    @Test
    fun mulRandom() {
        for (i in 0..1024) {
            val (bigIntVal1, modNVal1) = randIntAndModNScalar()
            val (bigIntVal2, modNVal2) = randIntAndModNScalar()
            val bigIntResult = bigIntVal1.multiply(bigIntVal2).mod(Secp256k1Curve.n)
            val modNValResult = modNVal1 * modNVal2
            assertEquals(bigIntResult.toString(16), modNValResult.toString())
        }
    }

    @TestFactory
    fun square(): Stream<DynamicTest> {
        return listOf(
            Tuple3("zero", "0", "0"),
            Tuple3("one", "1", "1"),
            Tuple3("over group order", "0000000000000000000000000000000100000000000000000000000000000000", "000000000000000000000000000000014551231950b75fc4402da1732fc9bebf"),
            Tuple3("group order - 1", "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140", "0000000000000000000000000000000000000000000000000000000000000001"),
            Tuple3("overflow in word eight", "100000000000000000000000000000000", "14551231950b75fc4402da1732fc9bebf"),
            Tuple3("overflow in word nine", "1000000000000000000000000000000000000", "14551231950b75fc4402da1732fc9bebf00000000"),
            Tuple3("overflow in word ten", "10000000000000000000000000000000000000000", "14551231950b75fc4402da1732fc9bebf0000000000000000"),
            Tuple3("overflow in word eleven", "100000000000000000000000000000000000000000000", "14551231950b75fc4402da1732fc9bebf000000000000000000000000"),
            Tuple3("overflow in word twelve", "1000000000000000000000000000000000000000000000000", "4551231950b75fc4402da1732fc9bec04551231950b75fc4402da1732fc9bebf"),
            Tuple3("overflow in word thirteen", "10000000000000000000000000000000000000000000000000000", "50b75fc4402da1732fc9bec09d671cd51b343a1b66926b57d2a4c1c61536bda7"),
            Tuple3("overflow in word fourteen", "100000000000000000000000000000000000000000000000000000000", "402da1732fc9bec09d671cd581c69bc59509b0b074ec0aea8f564d667ec7eb3c"),
            Tuple3("overflow in word fifteen", "1000000000000000000000000000000000000000000000000000000000000", "2fc9bec09d671cd581c69bc5e697f5e41f12c33a0a7b6f4e3302b92ea029cecd"),
            Tuple3("alternating bits", "a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5", "fb0982c5761d1eac534247f2a7c3af186a134d709b977ca88300faad5eafe9bc"),
            Tuple3("alternating bits 2", "5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a", "9081c595b95b2d17c424a546144b25488104c5889d914635bc9d1a51859e1c19"),
        ).map { (name: String, input: String, expected: String) ->
            DynamicTest.dynamicTest("Test: $name") {
                val s = setHex(input)
                val exp = setHex(expected)
                assertEquals(exp, s.square())
            }
        }.stream()
    }

    @Test
    fun squareRandom() {
        for (i in 0..1024) {
            val (bigIntVal, modNVal) = randIntAndModNScalar()
            val bigIntResult = bigIntVal.multiply(bigIntVal).mod(Secp256k1Curve.n)
            val modNValResult = modNVal.square()
            assertEquals(bigIntResult.toString(16), modNValResult.toString())
        }
    }

    @TestFactory
    fun negate(): Stream<DynamicTest> {
        return listOf(
            Tuple3("zero", "0", "0"),
            Tuple3("one", "1", "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140"),
            Tuple3("negation in word one", "0000000000000000000000000000000000000000000000000000000100000000", "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8bd0364141"),
            Tuple3("negation in word two", "0000000000000000000000000000000000000000000000010000000000000000", "fffffffffffffffffffffffffffffffebaaedce6af48a03abfd25e8cd0364141"),
            Tuple3("negation in word three", "0000000000000000000000000000000000000001000000000000000000000000", "fffffffffffffffffffffffffffffffebaaedce5af48a03bbfd25e8cd0364141"),
            Tuple3("negation in word four", "0000000000000000000000000000000100000000000000000000000000000000", "fffffffffffffffffffffffffffffffdbaaedce6af48a03bbfd25e8cd0364141"),
            Tuple3("negation in word five", "0000000000000000000000010000000000000000000000000000000000000000", "fffffffffffffffffffffffefffffffebaaedce6af48a03bbfd25e8cd0364141"),
            Tuple3("negation in word six", "0000000000000001000000000000000000000000000000000000000000000000", "fffffffffffffffefffffffffffffffebaaedce6af48a03bbfd25e8cd0364141"),
            Tuple3("negation in word seven", "0000000100000000000000000000000000000000000000000000000000000000", "fffffffefffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141"),
            Tuple3("alternating bits", "a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5", "5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a591509374109a2fa961a2cb8e72a909b9c"),
            Tuple3("alternating bits 2", "5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a", "a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a46054828c54ee45e16578043275dbe6e7"),
        ).map { (name: String, input: String, expected: String) ->
            DynamicTest.dynamicTest("Test: $name") {
                val s = setHex(input)
                val exp = setHex(expected)
                assertEquals(exp, -s)
            }
        }.stream()
    }

    @Test
    fun negateRandom() {
        for (i in 0..1024) {
            val (bigIntVal, modNVal) = randIntAndModNScalar()
            val bigIntResult = bigIntVal.negate().mod(Secp256k1Curve.n)
            val modNValResult = -modNVal
            assertEquals(bigIntResult.toString(16), modNValResult.toString())
        }
    }

    @TestFactory
    fun inverse(): Stream<DynamicTest> {
        return listOf(
            Tuple3("zero", "0", "0"),
            Tuple3("one", "1", "1"),
            Tuple3("inverse carry in word one", "0000000000000000000000000000000000000000000000000000000100000000", "5588b13effffffffffffffffffffffff934e5b00ca8417bf50177f7ba415411a"),
            Tuple3("inverse carry in word two", "0000000000000000000000000000000000000000000000010000000000000000", "4b0dff665588b13effffffffffffffffa09f710af01555259d4ad302583de6dc"),
            Tuple3("inverse carry in word three", "0000000000000000000000000000000000000001000000000000000000000000", "34b9ec244b0dff665588b13effffffffbcff4127932a971a78274c9d74176b38"),
            Tuple3("inverse carry in word four", "0000000000000000000000000000000100000000000000000000000000000000", "50a51ac834b9ec244b0dff665588b13e9984d5b3cf80ef0fd6a23766a3ee9f22"),
            Tuple3("inverse carry in word five", "0000000000000000000000010000000000000000000000000000000000000000", "27cfab5e50a51ac834b9ec244b0dff6622f16e85b683d5a059bcd5a3b29d9dff"),
            Tuple3("inverse carry in word six", "0000000000000001000000000000000000000000000000000000000000000000", "897f30c127cfab5e50a51ac834b9ec239c53f268b4700c14f19b9499ac58d8ad"),
            Tuple3("inverse carry in word seven", "0000000100000000000000000000000000000000000000000000000000000000", "6494ef93897f30c127cfab5e50a51ac7b4e8f713e0cddd182234e907286ae6b3"),
            Tuple3("alternating bits", "a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5", "cb6086e560b8597a85c934e46f5b6e8a445bf3f0a88e4160d7fa8d83fd10338d"),
            Tuple3("alternating bits 2", "5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a", "9f864ca486a74eb5f546364d76d24aa93716dc78f84847aa6c1c09fca2707d77"),
        ).map { (name: String, input: String, expected: String) ->
            DynamicTest.dynamicTest("Test: $name") {
                val s = setHex(input)
                val exp = setHex(expected)
                assertEquals(exp, s.inverse())
            }
        }.stream()
    }

    @Test
    fun inverseRandom() {
        for (i in 0..1024) {
            val (bigIntVal, modNVal) = randIntAndModNScalar()
            val bigIntResult = bigIntVal.modInverse(Secp256k1Curve.n)
            val modNValResult = modNVal.inverse()
            assertEquals(bigIntResult.toString(16), modNValResult.toString())
        }
    }

    @TestFactory
    fun isOverHalfOrder(): Stream<DynamicTest> {
        return listOf(
            Tuple3("zero", "0", false),
            Tuple3("one", "1", false),
            Tuple3("group half order - 1", "7fffffffffffffffffffffffffffffff5d576e7357a4501ddfe92f46681b209f", false),
            Tuple3("group half order", "7fffffffffffffffffffffffffffffff5d576e7357a4501ddfe92f46681b20a0", false),
            Tuple3("group half order + 1", "7fffffffffffffffffffffffffffffff5d576e7357a4501ddfe92f46681b20a1", true),
            Tuple3("over half order word one", "7fffffffffffffffffffffffffffffff5d576e7357a4501ddfe92f47681b20a0", true),
            Tuple3("over half order word two", "7fffffffffffffffffffffffffffffff5d576e7357a4501edfe92f46681b20a0", true),
            Tuple3("over half order word three", "7fffffffffffffffffffffffffffffff5d576e7457a4501ddfe92f46681b20a0", true),
            Tuple3("over half order word seven", "8fffffffffffffffffffffffffffffff5d576e7357a4501ddfe92f46681b20a0", true),
        ).map { (name: String, input: String, expected: Boolean) ->
            DynamicTest.dynamicTest("Test: $name") {
                val s = setHex(input)
                assertEquals(expected, s.isOverHalfOrder())
            }
        }.stream()
    }

    @Test
    fun isOverHalfOrderRandom() {
        val bigHalfOrder = Secp256k1Curve.n.shiftRight(1)
        for (i in 0..1024) {
            val (bigIntVal, modNVal) = randIntAndModNScalar()
            val bigIntResult = bigIntVal.compareTo(bigHalfOrder) > 0
            val modNValResult = modNVal.isOverHalfOrder()
            assertEquals(bigIntResult, modNValResult)
        }
    }

    private fun setHex(hexString: String): ModNScalar {
        if (hexString.length % 2 != 0) {
            return ModNScalar.setByteSlice(Hex.decodeOrThrow("0$hexString"))
        }
        val bytes = Hex.decodeOrThrow(hexString)
        return ModNScalar.setByteSlice(bytes)
    }

    private fun randIntAndModNScalar(): Tuple2<BigInteger, ModNScalar> {
        val r = SecureRandom()
        val buf = ByteArray(32)
        r.nextBytes(buf)
        val big = BigInt.fromBytes(buf)
        return Tuple(big.mod(Secp256k1Curve.n), ModNScalar.setBytes(buf))
    }

    private fun assertModNScalar(expected: UIntArray, scalar: ModNScalar) {
        val n = scalar.n
        for (i in 0..7) {
            assertEquals(expected[i], n[i])
        }
    }
}
