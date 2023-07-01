// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.secp256k1

import org.erwinkok.libp2p.crypto.math.BigInt
import org.erwinkok.util.Hex
import org.erwinkok.util.Tuple
import org.erwinkok.util.Tuple2
import org.erwinkok.util.Tuple3
import org.erwinkok.util.Tuple4
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.security.SecureRandom
import java.util.stream.Stream

internal class FieldValTest {
    @TestFactory
    fun setInt(): Stream<DynamicTest> {
        return listOf(
            Tuple3("1", 1, intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
            Tuple3("5", 5, intArrayOf(5, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
            Tuple3("2^16 - 1", 65535, intArrayOf(65535, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
            Tuple3("2^26", 67108864, intArrayOf(67108864, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
            Tuple3("2^26 + 1", 67108865, intArrayOf(67108865, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
            Tuple3("2^32 - 1", 4294967295L.toInt(), intArrayOf(4294967295L.toInt(), 0, 0, 0, 0, 0, 0, 0, 0, 0)),
        ).map { (name: String, input: Int, raw: IntArray) ->
            DynamicTest.dynamicTest("Test: $name") {
                val f = FieldVal.setInt(input)
                assertArrayEquals(raw, f.n)
            }
        }.stream()
    }

    @TestFactory
    fun setBytes(): Stream<DynamicTest> {
        return listOf(
            Tuple4(
                "zero",
                "00",
                intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                false,
            ),
            Tuple4(
                "field prime",
                "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f",
                intArrayOf(
                    0x03fffc2f, 0x03ffffbf, 0x03ffffff, 0x03ffffff, 0x03ffffff,
                    0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x003fffff,
                ),
                true,
            ),
            Tuple4(
                "field prime - 1",
                "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e",
                intArrayOf(
                    0x03fffc2e, 0x03ffffbf, 0x03ffffff, 0x03ffffff, 0x03ffffff,
                    0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x003fffff,
                ),
                false,
            ),
            Tuple4(
                "field prime + 1 (overflow in word zero)",
                "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc30",
                intArrayOf(
                    0x03fffc30, 0x03ffffbf, 0x03ffffff, 0x03ffffff, 0x03ffffff,
                    0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x003fffff,
                ),
                true,
            ),
            Tuple4(
                "field prime first 32 bits",
                "fffffc2f",
                intArrayOf(
                    0x03fffc2f, 0x00000003f, 0x00000000, 0x00000000, 0x00000000,
                    0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
                ),
                false,
            ),
            Tuple4(
                "field prime word zero",
                "03fffc2f",
                intArrayOf(
                    0x03fffc2f, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
                    0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
                ),
                false,
            ),
            Tuple4(
                "field prime first 64 bits",
                "fffffffefffffc2f",
                intArrayOf(
                    0x03fffc2f, 0x03ffffbf, 0x00000fff, 0x00000000, 0x00000000,
                    0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
                ),
                false,
            ),
            Tuple4(
                "field prime word zero and one",
                "0ffffefffffc2f",
                intArrayOf(
                    0x03fffc2f, 0x03ffffbf, 0x00000000, 0x00000000, 0x00000000,
                    0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
                ),
                false,
            ),
            Tuple4(
                "field prime first 96 bits",
                "fffffffffffffffefffffc2f",
                intArrayOf(
                    0x03fffc2f, 0x03ffffbf, 0x03ffffff, 0x0003ffff, 0x00000000,
                    0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
                ),
                false,
            ),
            Tuple4(
                "field prime word zero, one, and two",
                "3ffffffffffefffffc2f",
                intArrayOf(
                    0x03fffc2f, 0x03ffffbf, 0x03ffffff, 0x00000000, 0x00000000,
                    0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
                ),
                false,
            ),
            Tuple4(
                "overflow in word one (prime + 1<<26)",
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffff03fffc2f",
                intArrayOf(
                    0x03fffc2f, 0x03ffffc0, 0x03ffffff, 0x03ffffff, 0x03ffffff,
                    0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x003fffff,
                ),
                true,
            ),
            Tuple4(
                "(field prime - 1) * 2 NOT mod P, truncated >32 bytes",
                "01fffffffffffffffffffffffffffffffffffffffffffffffffffffffdfffff85c",
                intArrayOf(
                    0x01fffff8, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff,
                    0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x00007fff,
                ),
                false,
            ),
            Tuple4(
                "2^256 - 1",
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                intArrayOf(
                    0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff,
                    0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x003fffff,
                ),
                true,
            ),
            Tuple4(
                "alternating bits",
                "a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5",
                intArrayOf(
                    0x01a5a5a5, 0x01696969, 0x025a5a5a, 0x02969696, 0x01a5a5a5,
                    0x01696969, 0x025a5a5a, 0x02969696, 0x01a5a5a5, 0x00296969,
                ),
                false,
            ),
            Tuple4(
                "alternating bits 2",
                "5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a",
                intArrayOf(
                    0x025a5a5a, 0x02969696, 0x01a5a5a5, 0x01696969, 0x025a5a5a,
                    0x02969696, 0x01a5a5a5, 0x01696969, 0x025a5a5a, 0x00169696,
                ),
                false,
            ),
        ).map { (name: String, input: String, exp: IntArray, overflow: Boolean) ->
            DynamicTest.dynamicTest("Test: $name") {
                val inBytes = Hex.decodeOrThrow(input)

                // Ensure setting the bytes via the slice method works as expected.
                val f = FieldVal.setByteSlice(inBytes)
                assertArrayEquals(exp, f.n)

                // Ensure setting the bytes via the array method works as expected.
                val b32 = ByteArray(32)
                var truncatedInBytes = inBytes
                if (truncatedInBytes.size > 32) {
                    truncatedInBytes = truncatedInBytes.copyOf(32)
                }
                System.arraycopy(truncatedInBytes, 0, b32, 32 - truncatedInBytes.size, truncatedInBytes.size)
                val f2 = FieldVal.setBytes(b32)
                assertArrayEquals(exp, f2.n)

                assertEquals(overflow, f2.overflows)
            }
        }.stream()
    }

    @Test
    fun isZero() {
        assertFalse(FieldVal.One.isZero)
        assertTrue(FieldVal.Zero.isZero)
        assertEquals(1u, FieldVal.Zero.isZeroBit)
    }

    @Test
    fun isOne() {
        assertFalse(FieldVal.Zero.isOne)
        assertTrue(FieldVal.One.isOne)
        assertEquals(0u, FieldVal.One.isZeroBit)
    }

    @TestFactory
    fun isOne2(): Stream<DynamicTest> {
        return listOf(
            Tuple4(
                "zero",
                "0",
                true,
                false,
            ),
            Tuple4(
                "one",
                "1",
                true,
                true,
            ),
            Tuple4(
                "secp256k1 prime NOT normalized (would be 0)",
                "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f",
                false,
                false,
            ),
            Tuple4(
                "secp256k1 prime normalized (aka 0)",
                "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f",
                true,
                false,
            ),
            Tuple4(
                "secp256k1 prime + 1 normalized (aka 1)",
                "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc30",
                true,
                true,
            ),
            Tuple4(
                "secp256k1 prime + 1 NOT normalized (would be 1)",
                "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc30",
                false,
                false,
            ),
            Tuple4(
                "2^26 (one bit in second internal field word",
                "4000000",
                false,
                false,
            ),
            Tuple4(
                "2^52 (one bit in third internal field word",
                "10000000000000",
                false,
                false,
            ),
            Tuple4(
                "2^78 (one bit in fourth internal field word",
                "40000000000000000000",
                false,
                false,
            ),
            Tuple4(
                "2^104 (one bit in fifth internal field word",
                "100000000000000000000000000",
                false,
                false,
            ),
            Tuple4(
                "2^130 (one bit in sixth internal field word",
                "400000000000000000000000000000000",
                false,
                false,
            ),
            Tuple4(
                "2^156 (one bit in seventh internal field word",
                "1000000000000000000000000000000000000000",
                false,
                false,
            ),
            Tuple4(
                "2^182 (one bit in eighth internal field word",
                "4000000000000000000000000000000000000000000000",
                false,
                false,
            ),
            Tuple4(
                "2^208 (one bit in ninth internal field word",
                "10000000000000000000000000000000000000000000000000000",
                false,
                false,
            ),
            Tuple4(
                "2^234 (one bit in tenth internal field word",
                "40000000000000000000000000000000000000000000000000000000000",
                false,
                false,
            ),
        ).map { (name: String, input: String, normalize: Boolean, expected: Boolean) ->
            DynamicTest.dynamicTest("Test: $name") {
                var f = FieldVal.fromHex(input)
                if (normalize) {
                    f = f.normalize()
                }
                assertEquals(expected, f.isOne)
                assertEquals(expected, f.isOneBit == 1u)
            }
        }.stream()
    }

    @TestFactory
    fun stringer(): Stream<DynamicTest> {
        return listOf(
            Tuple3("zero", "0", "0000000000000000000000000000000000000000000000000000000000000000"),
            Tuple3("one", "1", "0000000000000000000000000000000000000000000000000000000000000001"),
            Tuple3("ten", "a", "000000000000000000000000000000000000000000000000000000000000000a"),
            Tuple3("eleven", "b", "000000000000000000000000000000000000000000000000000000000000000b"),
            Tuple3("twelve", "c", "000000000000000000000000000000000000000000000000000000000000000c"),
            Tuple3("thirteen", "d", "000000000000000000000000000000000000000000000000000000000000000d"),
            Tuple3("thirteen", "e", "000000000000000000000000000000000000000000000000000000000000000e"),
            Tuple3("thirteen", "f", "000000000000000000000000000000000000000000000000000000000000000f"),
            Tuple3("240", "f0", "00000000000000000000000000000000000000000000000000000000000000f0"),
            Tuple3("2^26 - 1", "3ffffff", "0000000000000000000000000000000000000000000000000000000003ffffff"),
            Tuple3("2^32 - 1", "ffffffff", "00000000000000000000000000000000000000000000000000000000ffffffff"),
            Tuple3("2^64 - 1", "ffffffffffffffff", "000000000000000000000000000000000000000000000000ffffffffffffffff"),
            Tuple3("2^96 - 1", "ffffffffffffffffffffffff", "0000000000000000000000000000000000000000ffffffffffffffffffffffff"),
            Tuple3("2^128 - 1", "ffffffffffffffffffffffffffffffff", "00000000000000000000000000000000ffffffffffffffffffffffffffffffff"),
            Tuple3("2^160 - 1", "ffffffffffffffffffffffffffffffffffffffff", "000000000000000000000000ffffffffffffffffffffffffffffffffffffffff"),
            Tuple3("2^192 - 1", "ffffffffffffffffffffffffffffffffffffffffffffffff", "0000000000000000ffffffffffffffffffffffffffffffffffffffffffffffff"),
            Tuple3("2^224 - 1", "ffffffffffffffffffffffffffffffffffffffffffffffffffffffff", "00000000ffffffffffffffffffffffffffffffffffffffffffffffffffffffff"),
            Tuple3("2^256-4294968273 (the secp256k1 prime, so should result in 0)", "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f", "0000000000000000000000000000000000000000000000000000000000000000"),
            Tuple3("2^256-4294968274 (the secp256k1 prime+1, so should result in 1)", "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc30", "0000000000000000000000000000000000000000000000000000000000000001"),
        ).map { (name, input, expected) ->
            DynamicTest.dynamicTest("Test: $name") {
                val f = FieldVal.fromHex(input)
                assertEquals(expected, f.toString())
            }
        }.stream()
    }

    @TestFactory
    fun normalize(): Stream<DynamicTest> {
        return listOf(
            Tuple3("5", intArrayOf(0x00000005, 0, 0, 0, 0, 0, 0, 0, 0, 0), intArrayOf(0x00000005, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
            Tuple3("2^26", intArrayOf(0x04000000, 0x0, 0, 0, 0, 0, 0, 0, 0, 0), intArrayOf(0x00000000, 0x1, 0, 0, 0, 0, 0, 0, 0, 0)),
            Tuple3("2^26 + 1", intArrayOf(0x04000001, 0x0, 0, 0, 0, 0, 0, 0, 0, 0), intArrayOf(0x00000001, 0x1, 0, 0, 0, 0, 0, 0, 0, 0)),
            Tuple3("2^32 - 1", intArrayOf(-0x1, 0x00, 0, 0, 0, 0, 0, 0, 0, 0), intArrayOf(0x03ffffff, 0x3f, 0, 0, 0, 0, 0, 0, 0, 0)),
            Tuple3("2^32", intArrayOf(0x04000000, 0x3f, 0, 0, 0, 0, 0, 0, 0, 0), intArrayOf(0x00000000, 0x40, 0, 0, 0, 0, 0, 0, 0, 0)),
            Tuple3("2^32 + 1", intArrayOf(0x04000001, 0x3f, 0, 0, 0, 0, 0, 0, 0, 0), intArrayOf(0x00000001, 0x40, 0, 0, 0, 0, 0, 0, 0, 0)),
            Tuple3("2^64 - 1", intArrayOf(-0x1, -0x40, 0xfc0, 0, 0, 0, 0, 0, 0, 0), intArrayOf(0x03ffffff, 0x03ffffff, 0xfff, 0, 0, 0, 0, 0, 0, 0)),
            Tuple3("2^64", intArrayOf(0x04000000, 0x03ffffff, 0x0fff, 0, 0, 0, 0, 0, 0, 0), intArrayOf(0x00000000, 0x00000000, 0x1000, 0, 0, 0, 0, 0, 0, 0)),
            Tuple3("2^64 + 1", intArrayOf(0x04000001, 0x03ffffff, 0x0fff, 0, 0, 0, 0, 0, 0, 0), intArrayOf(0x00000001, 0x00000000, 0x1000, 0, 0, 0, 0, 0, 0, 0)),
            Tuple3("2^96 - 1", intArrayOf(-0x1, -0x40, -0x40, 0x3ffc0, 0, 0, 0, 0, 0, 0), intArrayOf(0x03ffffff, 0x03ffffff, 0x03ffffff, 0x3ffff, 0, 0, 0, 0, 0, 0)),
            Tuple3("2^96", intArrayOf(0x04000000, 0x03ffffff, 0x03ffffff, 0x3ffff, 0, 0, 0, 0, 0, 0), intArrayOf(0x00000000, 0x00000000, 0x00000000, 0x40000, 0, 0, 0, 0, 0, 0)),
            Tuple3("2^128 - 1", intArrayOf(-0x1, -0x40, -0x40, -0x40, 0xffffc0, 0, 0, 0, 0, 0), intArrayOf(0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0xffffff, 0, 0, 0, 0, 0)),
            Tuple3("2^128", intArrayOf(0x04000000, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x0ffffff, 0, 0, 0, 0, 0), intArrayOf(0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x1000000, 0, 0, 0, 0, 0)),
            Tuple3("2^256 - 4294968273 (secp256k1 prime)", intArrayOf(-0x3d1, -0x80, -0x40, -0x40, -0x40, -0x40, -0x40, -0x40, -0x40, 0x3fffc0), intArrayOf(0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x000000)),

            // Prime larger than P where both first and second words are larger
            // than P's first and second words
            Tuple3("Value > P with 1st and 2nd words > P's 1st and 2nd words", intArrayOf(-0x3d0, -0x7a, -0x40, -0x40, -0x40, -0x40, -0x40, -0x40, -0x40, 0x3fffc0), intArrayOf(0x00000001, 0x00000006, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x000000)),

            // Prime larger than P where only the second word is larger
            // than P's second words.
            Tuple3("Value > P with 2nd word > P's 2nd word", intArrayOf(-0x3d6, -0x79, -0x40, -0x40, -0x40, -0x40, -0x40, -0x40, -0x40, 0x3fffc0), intArrayOf(0x03fffffb, 0x00000006, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x000000)),

            Tuple3("2^256 - 1", intArrayOf(-0x1, -0x40, -0x40, -0x40, -0x40, -0x40, -0x40, -0x40, -0x40, 0x3fffc0), intArrayOf(0x000003d0, 0x00000040, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x000000)),

            // Prime with field representation such that the initial
            // reduction does not result in a carry to bit 256.
            Tuple3(
                "2^256 - 4294968273 (secp256k1 prime)",
                intArrayOf(0x03fffc2f, 0x03ffffbf, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x003fffff),
                intArrayOf(0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000),
            ),

            // Prime larger than P that reduces to a value which is still
            // larger than P when it has a magnitude of 1 due to its first
            // word and does not result in a carry to bit 256.
            Tuple3(
                " 2^256 - 4294968272 (secp256k1 prime + 1)",
                intArrayOf(0x03fffc30, 0x03ffffbf, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x003fffff),
                intArrayOf(0x00000001, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000),
            ),

            // Prime larger than P that reduces to a value which is still
            // larger than P when it has a magnitude of 1 due to its second
            // word and does not result in a carry to bit 256.
            Tuple3(
                "2^256 - 4227859409 (secp256k1 prime + 0x4000000)",
                intArrayOf(0x03fffc2f, 0x03ffffc0, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x003fffff),
                intArrayOf(0x00000000, 0x00000001, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000),
            ),

            // Prime larger than P that reduces to a value which is still
            // larger than P when it has a magnitude of 1 due to a carry to
            // bit 256, but would not be without the carry.  These values
            // come from the fact that P is 2^256 - 4294968273 and 977 is
            // the low order word in the internal field representation.
            Tuple3(
                "2^256 * 5 - ((4294968273 - (977+1)) * 4)",
                intArrayOf(0x03ffffff, 0x03fffeff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x0013fffff),
                intArrayOf(0x00001314, 0x00000040, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x000000000),
            ),

            // Prime larger than P that reduces to a value which is still
            // larger than P when it has a magnitude of 1 due to both a
            // carry to bit 256 and the first word.
            Tuple3(
                "Value > P with redux > P at mag 1 due to 1st word and carry to bit 256",
                intArrayOf(0x03fffc30, 0x03ffffbf, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x07ffffff, 0x003fffff),
                intArrayOf(0x00000001, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000001),
            ),

            // Prime larger than P that reduces to a value which is still
            // larger than P when it has a magnitude of 1 due to both a
            // carry to bit 256 and the second word.
            //
            Tuple3(
                "Value > P with redux > P at mag 1 due to 2nd word and carry to bit 256",
                intArrayOf(0x03fffc2f, 0x03ffffc0, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x3ffffff, 0x07ffffff, 0x003fffff),
                intArrayOf(0x00000000, 0x00000001, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x0000000, 0x00000000, 0x00000001),
            ),

            // Prime larger than P that reduces to a value which is still
            // larger than P when it has a magnitude of 1 due to a carry to
            // bit 256 and the first and second words.
            //
            Tuple3(
                "Value > P with redux > P at mag 1 due to 1st and 2nd words and carry to bit 256",
                intArrayOf(0x03fffc30, 0x03ffffc0, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x07ffffff, 0x003fffff),
                intArrayOf(0x00000001, 0x00000001, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000001),
            ),

            // ---------------------------------------------------------------------
            // There are 3 main conditions that must be true if the final reduction
            // is needed after the initial reduction to magnitude 1 when there was
            // NOT a carry to bit 256 (in other words when the original value was <
            // 2^256):
            // 1) The final word of the reduced value is equal to the one of P
            // 2) The 3rd through 9th words are equal to those of P
            // 3) Either:
            //    - The 2nd word is greater than the one of P; or
            //    - The 2nd word is equal to that of P AND the 1st word is greater
            //
            // Therefore the eight possible combinations of those 3 main conditions
            // can be thought of in binary where each bit starting from the left
            // corresponds to the aforementioned conditions as such:
            // 000, 001, 010, 011, 100, 101, 110, 111
            //
            // For example, combination 6 is when both conditons 1 and 2 are true,
            // but condition 3 is NOT true.
            //
            // The following tests hit each of these combinations and refer to each
            // by its decimal equivalent for ease of reference.
            //
            // NOTE: The final combination (7) is already tested above since it only
            // happens when the original value is already the normalized
            // representation of P.
            // ---------------------------------------------------------------------
            Tuple3(
                "Value < 2^256 final reduction combination 0",
                intArrayOf(0x03fff85e, 0x03ffffbf, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03fffffe, 0x003ffffe),
                intArrayOf(0x03fff85e, 0x03ffffbf, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03fffffe, 0x003ffffe),
            ),

            Tuple3(
                "Value < 2^256 final reduction combination 1 via 2nd word",
                intArrayOf(0x03fff85e, 0x03ffffc0, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03fffffe, 0x003ffffe),
                intArrayOf(0x03fff85e, 0x03ffffc0, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03fffffe, 0x003ffffe),
            ),
            Tuple3(
                "Value < 2^256 final reduction combination 1 via 1st word",
                intArrayOf(0x03fffc2f, 0x03ffffbf, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03fffffe, 0x003ffffe),
                intArrayOf(0x03fffc2f, 0x03ffffbf, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03fffffe, 0x003ffffe),
            ),
            Tuple3(
                "Value < 2^256 final reduction combination 2",
                intArrayOf(0x03fff85e, 0x03ffffbf, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x003ffffe),
                intArrayOf(0x03fff85e, 0x03ffffbf, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x003ffffe),
            ),
            Tuple3(
                "Value < 2^256 final reduction combination 3 via 2nd word",
                intArrayOf(0x03fff85e, 0x03ffffc0, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x003ffffe),
                intArrayOf(0x03fff85e, 0x03ffffc0, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x003ffffe),
            ),
            Tuple3(
                "Value < 2^256 final reduction combination 3 via 1st word",
                intArrayOf(0x03fffc2f, 0x03ffffbf, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x003ffffe),
                intArrayOf(0x03fffc2f, 0x03ffffbf, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x003ffffe),
            ),
            Tuple3(
                "Value < 2^256 final reduction combination 4",
                intArrayOf(0x03fff85e, 0x03ffffbf, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03fffffe, 0x003fffff),
                intArrayOf(0x03fff85e, 0x03ffffbf, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03fffffe, 0x003fffff),
            ),
            Tuple3(
                "Value < 2^256 final reduction combination 5 via 2nd word",
                intArrayOf(0x03fff85e, 0x03ffffc0, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03fffffe, 0x003fffff),
                intArrayOf(0x03fff85e, 0x03ffffc0, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03fffffe, 0x003fffff),
            ),
            Tuple3(
                "Value < 2^256 final reduction combination 5 via 1st word",
                intArrayOf(0x03fffc2f, 0x03ffffbf, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03fffffe, 0x003fffff),
                intArrayOf(0x03fffc2f, 0x03ffffbf, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03fffffe, 0x003fffff),
            ),
            Tuple3(
                "Value < 2^256 final reduction combination 6",
                intArrayOf(0x03fff85e, 0x03ffffbf, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x003fffff),
                intArrayOf(0x03fff85e, 0x03ffffbf, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x03ffffff, 0x003fffff),
            ),
        ).map { (name: String, raw: IntArray, normalized: IntArray) ->
            DynamicTest.dynamicTest("Test: $name") {
                val f = FieldVal(raw[0], raw[1], raw[2], raw[3], raw[4], raw[5], raw[6], raw[7], raw[8], raw[9]).normalize()
                assertArrayEquals(normalized, f.n)
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
            Tuple3("secp256k1 prime (not normalized so should be incorrect result)", "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f", true),
            Tuple3("secp256k1 prime + 1 (not normalized so should be incorrect result)", "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc30", false),
        ).map { (name: String, input: String, expected: Boolean) ->
            DynamicTest.dynamicTest("Test: $name") {
                val f = FieldVal.fromHex(input)
                assertEquals(expected, f.isOdd)
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
            Tuple4("0 == prime (mod prime)?", "0", "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f", true),
            Tuple4("1 == prime + 1 (mod prime)?", "1", "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc30", true),
        ).map { (name: String, in1: String, in2: String, expected: Boolean) ->
            DynamicTest.dynamicTest("Test: $name") {
                val f = FieldVal.fromHex(in1).normalize()
                val f2 = FieldVal.fromHex(in2).normalize()
                val result = f == f2
                assertEquals(expected, result)
            }
        }.stream()
    }

    @TestFactory
    fun negate(): Stream<DynamicTest> {
        return listOf(
            Tuple3("zero", "0", "0"),
            Tuple3("secp256k1 prime (direct val in with 0 out)", "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f", "0"),
            Tuple3("secp256k1 prime (0 in with direct val out)", "0", "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f"),
            Tuple3("1 -> secp256k1 prime - 1", "1", "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e"),
            Tuple3("secp256k1 prime - 1 -> 1", "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e", "1"),
            Tuple3("2 -> secp256k1 prime - 2", "2", "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2d"),
            Tuple3("secp256k1 prime - 2 -> 2", "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2d", "2"),
            Tuple3(
                "random sampling #1",
                "b3d9aac9c5e43910b4385b53c7e78c21d4cd5f8e683c633aed04c233efc2e120",
                "4c2655363a1bc6ef4bc7a4ac381873de2b32a07197c39cc512fb3dcb103d1b0f",
            ),
            Tuple3(
                "random sampling #2",
                "f8a85984fee5a12a7c8dd08830d83423c937d77c379e4a958e447a25f407733f",
                "757a67b011a5ed583722f77cf27cbdc36c82883c861b56a71bb85d90bf888f0",
            ),
            Tuple3(
                "random sampling #3",
                "45ee6142a7fda884211e93352ed6cb2807800e419533be723a9548823ece8312",
                "ba119ebd5802577bdee16ccad12934d7f87ff1be6acc418dc56ab77cc131791d",
            ),
            Tuple3(
                "random sampling #4",
                "53c2a668f07e411a2e473e1c3b6dcb495dec1227af27673761d44afe5b43d22b",
                "ac3d59970f81bee5d1b8c1e3c49234b6a213edd850d898c89e2bb500a4bc2a04",
            ),
        ).map { (name: String, input: String, exp: String) ->
            DynamicTest.dynamicTest("Test: $name") {
                val f = FieldVal.fromHex(input).normalize()
                val expected = FieldVal.fromHex(exp).normalize()
                val result = (-f).normalize()
                assertEquals(expected, result)
            }
        }.stream()
    }

    @TestFactory
    fun addInt(): Stream<DynamicTest> {
        return listOf(
            Tuple4("zero + one", "0", 1, "1"),
            Tuple4("one + zero", "1", 0, "1"),
            Tuple4("one + one", "1", 1, "2"),
            Tuple4("secp256k1 prime-1 + 1", "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e", 1, "0"),
            Tuple4("secp256k1 prime + 1", "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f", 1, "1"),
            Tuple4(
                "random sampling #1",
                "ff95ad9315aff04ab4af0ce673620c7145dc85d03bab5ba4b09ca2c4dec2d6c1",
                0x10f,
                "ff95ad9315aff04ab4af0ce673620c7145dc85d03bab5ba4b09ca2c4dec2d7d0",
            ),
            Tuple4(
                "random sampling #2",
                "44bdae6b772e7987941f1ba314e6a5b7804a4c12c00961b57d20f41deea9cecf",
                0x2cf11d41,
                "44bdae6b772e7987941f1ba314e6a5b7804a4c12c00961b57d20f41e1b9aec10",
            ),
            Tuple4(
                "random sampling #3",
                "88c3ecae67b591935fb1f6a9499c35315ffad766adca665c50b55f7105122c9c",
                0x4829aa2d,
                "88c3ecae67b591935fb1f6a9499c35315ffad766adca665c50b55f714d3bd6c9",
            ),
            Tuple4(
                "random sampling #4",
                "8523e9edf360ca32a95aae4e57fcde5a542b471d08a974d94ea0ee09a015e2a6",
                -0x5ded9a5b,
                "8523e9edf360ca32a95aae4e57fcde5a542b471d08a974d94ea0ee0a4228484b",
            ),
        ).map { (name: String, in1: String, in2: Int, exp: String) ->
            DynamicTest.dynamicTest("Test: $name") {
                val f = FieldVal.fromHex(in1).normalize()
                val expected = FieldVal.fromHex(exp).normalize()
                val qw = f + in2
                val result = qw.normalize()
                assertEquals(expected, result)
            }
        }.stream()
    }

    @TestFactory
    fun add(): Stream<DynamicTest> {
        return listOf(
            Tuple4("zero + one", "0", "1", "1"),
            Tuple4("one + zero", "1", "0", "1"),
            Tuple4("one + one", "1", "1", "2"),
            Tuple4("secp256k1 prime-1 + 1", "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e", "1", "0"),
            Tuple4("secp256k1 prime + 1", "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f", "1", "1"),
            Tuple4("close but over the secp256k1 prime", "fffffffffffffffffffffffffffffffffffffffffffffffffffffff000000000", "f1ffff000", "1ffff3d1"),
            Tuple4(
                "random sampling #1",
                "2b2012f975404e5065b4292fb8bed0a5d315eacf24c74d8b27e73bcc5430edcc",
                "2c3cefa4e4753e8aeec6ac4c12d99da4d78accefda3b7885d4c6bab46c86db92",
                "575d029e59b58cdb547ad57bcb986e4aaaa0b7beff02c610fcadf680c0b7c95e",
            ),
            Tuple4(
                "random sampling #2",
                "8131e8722fe59bb189692b96c9f38de92885730f1dd39ab025daffb94c97f79c",
                "ff5454b765f0aab5f0977dcc629becc84cabeb9def48e79c6aadb2622c490fa9",
                "80863d2995d646677a00a9632c8f7ab175315ead0d1c824c9088b21c78e10b16",
            ),
            Tuple4(
                "random sampling #3",
                "c7c95e93d0892b2b2cdd77e80eb646ea61be7a30ac7e097e9f843af73fad5c22",
                "3afe6f91a74dfc1c7f15c34907ee981656c37236d946767dd53ccad9190e437c",
                "02c7ce2577d72747abf33b3116a4df00b881ec6785c47ffc74c105d158bba36f",
            ),
            Tuple4(
                "random sampling #4",
                "fd1c26f6a23381e5d785ba889494ec059369b888ad8431cd67d8c934b580dbe1",
                "a475aa5a31dcca90ef5b53c097d9133d6b7117474b41e7877bb199590fc0489c",
                "a191d150d4104c76c6e10e492c6dff42fedacfcff8c61954e38a628ec541284e",
            ),
            Tuple4(
                "random sampling #5",
                "ad82b8d1cc136e23e9fd77fe2c7db1fe5a2ecbfcbde59ab3529758334f862d28",
                "4d6a4e95d6d61f4f46b528bebe152d408fd741157a28f415639347a84f6f574b",
                "faed0767a2e98d7330b2a0bcea92df3eea060d12380e8ec8b62a9fdb9ef58473",
            ),
            Tuple4(
                "random sampling #6",
                "f3f43a2540054a86e1df98547ec1c0e157b193e5350fb4a3c3ea214b228ac5e7",
                "25706572592690ea3ddc951a1b48b504a4c83dc253756e1b96d56fdfb3199522",
                "19649f97992bdb711fbc2d6e9a0a75e5fc79d1a7888522bf5abf912bd5a45eda",
            ),
            Tuple4(
                "random sampling #7",
                "6915bb94eef13ff1bb9b2633d997e13b9b1157c713363cc0e891416d6734f5b8",
                "11f90d6ac6fe1c4e8900b1c85fb575c251ec31b9bc34b35ada0aea1c21eded22",
                "7b0ec8ffb5ef5c40449bd7fc394d56fdecfd8980cf6af01bc29c2b898922e2da",
            ),
            Tuple4(
                "random sampling #8",
                "48b0c9eae622eed9335b747968544eb3e75cb2dc8128388f948aa30f88cabde4",
                "0989882b52f85f9d524a3a3061a0e01f46d597839d2ba637320f4b9510c8d2d5",
                "523a5216391b4e7685a5aea9c9f52ed32e324a601e53dec6c699eea4999390b9",
            ),
        ).map { (name: String, in1: String, in2: String, exp: String) ->
            DynamicTest.dynamicTest("Test: $name") {
                val f = FieldVal.fromHex(in1).normalize()
                val f2 = FieldVal.fromHex(in2).normalize()
                val expected = FieldVal.fromHex(exp).normalize()
                val result = (f + f2).normalize()
                assertEquals(expected, result)
            }
        }.stream()
    }

    @TestFactory
    fun mulInt(): Stream<DynamicTest> {
        return listOf(
            Tuple4("zero * zero", "0", 0, "0"),
            Tuple4("one * zero", "1", 0, "0"),
            Tuple4("zero * one", "0", 1, "0"),
            Tuple4("one * one", "1", 1, "1"),
            Tuple4(
                "secp256k1 prime-1 * 2",
                "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e",
                2,
                "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2d",
            ),
            Tuple4("secp256k1 prime * 3", "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f", 3, "0"),
            Tuple4(
                "secp256k1 prime-1 * 8",
                "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e",
                8,
                "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc27",
            ),
            // Random samples for first value.  The second value is limited
            // to 8 since that is the maximum int used in the elliptic curve
            // calculations.
            Tuple4(
                "random sampling #1",
                "b75674dc9180d306c692163ac5e089f7cef166af99645c0c23568ab6d967288a",
                6,
                "4c06bd2b6904f228a76c8560a3433bced9a8681d985a2848d407404d186b0280",
            ),
            Tuple4(
                "random sampling #2",
                "54873298ac2b5ba8591c125ae54931f5ea72040aee07b208d6135476fb5b9c0e",
                3,
                "fd9597ca048212f90b543710afdb95e1bf560c20ca17161a8239fd64f212d42a",
            ),
            Tuple4(
                "random sampling #3",
                "7c30fbd363a74c17e1198f56b090b59bbb6c8755a74927a6cba7a54843506401",
                5,
                "6cf4eb20f2447c77657fccb172d38c0aa91ea4ac446dc641fa463a6b5091fba7",
            ),
            Tuple4(
                "random sampling #4",
                "fb4529be3e027a3d1587d8a500b72f2d312e3577340ef5175f96d113be4c2ceb",
                8,
                "da294df1f013d1e8ac3ec52805b979698971abb9a077a8bafcb688a4f261820f",
            ),
        ).map { (name: String, in1: String, in2: Int, exp: String) ->
            DynamicTest.dynamicTest("Test: $name") {
                val f = FieldVal.fromHex(in1).normalize()
                val expected = FieldVal.fromHex(exp).normalize()
                val result = (f * in2).normalize()
                assertEquals(expected, result)
            }
        }.stream()
    }

    @TestFactory
    fun mul(): Stream<DynamicTest> {
        return listOf(
            Tuple4("zero * zero", "0", "0", "0"),
            Tuple4("one * zero", "1", "0", "0"),
            Tuple4("zero * one", "0", "1", "0"),
            Tuple4("one * one", "1", "1", "1"),
            Tuple4(
                "slightly over prime",
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffff1ffff",
                "1000",
                "1ffff3d1",
            ),
            Tuple4(
                "secp256k1 prime-1 * 2",
                "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e",
                "2",
                "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2d",
            ),
            Tuple4("secp256k1 prime * 3", "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f", "3", "0"),
            Tuple4(
                "secp256k1 prime-1 * 8",
                "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e",
                "8",
                "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc27",
            ),
            Tuple4(
                "random sampling #1",
                "cfb81753d5ef499a98ecc04c62cb7768c2e4f1740032946db1c12e405248137e",
                "58f355ad27b4d75fb7db0442452e732c436c1f7c5a7c4e214fa9cc031426a7d3",
                "1018cd2d7c2535235b71e18db9cd98027386328d2fa6a14b36ec663c4c87282b",
            ),
            Tuple4(
                "random sampling #2",
                "26e9d61d1cdf3920e9928e85fa3df3e7556ef9ab1d14ec56d8b4fc8ed37235bf",
                "2dfc4bbe537afee979c644f8c97b31e58be5296d6dbc460091eae630c98511cf",
                "da85f48da2dc371e223a1ae63bd30b7e7ee45ae9b189ac43ff357e9ef8cf107a",
            ),
            Tuple4(
                "random sampling #3",
                "5db64ed5afb71646c8b231585d5b2bf7e628590154e0854c4c29920b999ff351",
                "279cfae5eea5d09ade8e6a7409182f9de40981bc31c84c3d3dfe1d933f152e9a",
                "2c78fbae91792dd0b157abe3054920049b1879a7cc9d98cfda927d83be411b37",
            ),
            Tuple4(
                "random sampling #4",
                "b66dfc1f96820b07d2bdbd559c19319a3a73c97ceb7b3d662f4fe75ecb6819e6",
                "bf774aba43e3e49eb63a6e18037d1118152568f1a3ac4ec8b89aeb6ff8008ae1",
                "c4f016558ca8e950c21c3f7fc15f640293a979c7b01754ee7f8b3340d4902ebb",
            ),
        ).map { (name: String, in1: String, in2: String, exp: String) ->
            DynamicTest.dynamicTest("Test: $name") {
                val f = FieldVal.fromHex(in1).normalize()
                val f2 = FieldVal.fromHex(in2).normalize()
                val expected = FieldVal.fromHex(exp).normalize()
                val result = (f * f2).normalize()
                assertEquals(expected, result)
            }
        }.stream()
    }

    @TestFactory
    fun square(): Stream<DynamicTest> {
        return listOf(
            Tuple3("zero", "0", "0"),
            Tuple3("secp256k1 prime (direct val in with 0 out)", "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f", "0"),
            Tuple3("secp256k1 prime (0 in with direct val out)", "0", "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f"),
            Tuple3("secp256k1 prime-1", "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e", "1"),
            Tuple3("secp256k1 prime-2", "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2d", "4"),
            Tuple3(
                "random sampling #1",
                "b0ba920360ea8436a216128047aab9766d8faf468895eb5090fc8241ec758896",
                "133896b0b69fda8ce9f648b9a3af38f345290c9eea3cbd35bafcadf7c34653d3",
            ),
            Tuple3(
                "random sampling #2",
                "c55d0d730b1d0285a1599995938b042a756e6e8857d390165ffab480af61cbd5",
                "cd81758b3f5877cbe7e5b0a10cebfa73bcbf0957ca6453e63ee8954ab7780bee",
            ),
            Tuple3(
                "random sampling #3",
                "e89c1f9a70d93651a1ba4bca5b78658f00de65a66014a25544d3365b0ab82324",
                "39ffc7a43e5dbef78fd5d0354fb82c6d34f5a08735e34df29da14665b43aa1f",
            ),
            Tuple3(
                "random sampling #4",
                "7dc26186079d22bcbe1614aa20ae627e62d72f9be7ad1e99cac0feb438956f05",
                "bf86bcfc4edb3d81f916853adfda80c07c57745b008b60f560b1912f95bce8ae",
            ),
        ).map { (name: String, input: String, exp: String) ->
            DynamicTest.dynamicTest("Test: $name") {
                val f = FieldVal.fromHex(input).normalize()
                val expected = FieldVal.fromHex(exp).normalize()
                val result = f.square().normalize()
                assertEquals(expected, result)
            }
        }.stream()
    }

    @TestFactory
    fun inverse(): Stream<DynamicTest> {
        return listOf(
            Tuple3(
                "zero",
                "0",
                "0",
            ),
            Tuple3(
                "secp256k1 prime (direct val in with 0 out)",
                "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f",
                "0",
            ),
            Tuple3(
                "secp256k1 prime (0 in with direct val out)",
                "0",
                "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f",
            ),
            Tuple3(
                "secp256k1 prime - 1",
                "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e",
                "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e",
            ),
            Tuple3(
                "secp256k1 prime - 2",
                "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2d",
                "7fffffffffffffffffffffffffffffffffffffffffffffffffffffff7ffffe17",
            ),
            Tuple3(
                "random sampling #1",
                "16fb970147a9acc73654d4be233cc48b875ce20a2122d24f073d29bd28805aca",
                "987aeb257b063df0c6d1334051c47092b6d8766c4bf10c463786d93f5bc54354",
            ),
            Tuple3(
                "random sampling #2",
                "69d1323ce9f1f7b3bd3c7320b0d6311408e30281e273e39a0d8c7ee1c8257919",
                "49340981fa9b8d3dad72de470b34f547ed9179c3953797d0943af67806f4bb6",
            ),
            Tuple3(
                "random sampling #3",
                "e0debf988ae098ecda07d0b57713e97c6d213db19753e8c95aa12a2fc1cc5272",
                "64f58077b68af5b656b413ea366863f7b2819f8d27375d9c4d9804135ca220c2",
            ),
            Tuple3(
                "random sampling #4",
                "dcd394f91f74c2ba16aad74a22bb0ed47fe857774b8f2d6c09e28bfb14642878",
                "fb848ec64d0be572a63c38fe83df5e7f3d032f60bf8c969ef67d36bf4ada22a9",
            ),
        ).map { (name: String, input: String, exp: String) ->
            DynamicTest.dynamicTest("Test: $name") {
                val f = FieldVal.fromHex(input).normalize()
                val expected = FieldVal.fromHex(exp).normalize()
                val result = f.inverse().normalize()
                assertEquals(expected, result)
            }
        }.stream()
    }

    @TestFactory
    fun squareRoot(): Stream<DynamicTest> {
        return listOf(
            Tuple4("secp256k1 prime (as 0 in and out)", "0", true, "0"),
            Tuple4("secp256k1 prime (direct val with 0 out)", "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f", true, "0"),
            Tuple4("secp256k1 prime (as 0 in direct val out)", "0", true, "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f"),
            Tuple4("secp256k1 prime-1", "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e", false, "0000000000000000000000000000000000000000000000000000000000000001"),
            Tuple4("secp256k1 prime-2", "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2d", false, "210c790573632359b1edb4302c117d8a132654692c3feeb7de3a86ac3f3b53f7"),
            Tuple4("(secp256k1 prime-2)^2", "0000000000000000000000000000000000000000000000000000000000000004", true, "0000000000000000000000000000000000000000000000000000000000000002"),
            Tuple4("value 1", "0000000000000000000000000000000000000000000000000000000000000001", true, "0000000000000000000000000000000000000000000000000000000000000001"),
            Tuple4("value 2", "0000000000000000000000000000000000000000000000000000000000000002", true, "210c790573632359b1edb4302c117d8a132654692c3feeb7de3a86ac3f3b53f7"),
            Tuple4("random sampling 1", "16fb970147a9acc73654d4be233cc48b875ce20a2122d24f073d29bd28805aca", false, "6a27dcfca440cf7930a967be533b9620e397f122787c53958aaa7da7ad3d89a4"),
            Tuple4("square of random sampling 1", "f4a8c3738ace0a1c3abf77737ae737f07687b5e24c07a643398298bd96893a18", true, "e90468feb8565338c9ab2b41dcc33b7478a31df5dedd2db0f8c2d641d77fa165"),
            Tuple4("random sampling 2", "69d1323ce9f1f7b3bd3c7320b0d6311408e30281e273e39a0d8c7ee1c8257919", true, "61f4a7348274a52d75dfe176b8e3aaff61c1c833b6678260ba73def0fb2ad148"),
            Tuple4("random sampling 3", "e0debf988ae098ecda07d0b57713e97c6d213db19753e8c95aa12a2fc1cc5272", false, "6e1cc9c311d33d901670135244f994b1ea39501f38002269b34ce231750cfbac"),
            Tuple4("random sampling 4", "dcd394f91f74c2ba16aad74a22bb0ed47fe857774b8f2d6c09e28bfb14642878", true, "72b22fe6f173f8bcb21898806142ed4c05428601256eafce5d36c1b08fb82bab"),
        ).map { (name: String, inp: String, valid: Boolean, want: String) ->
            DynamicTest.dynamicTest("Test: $name") {
                val hInp = FieldVal.fromHex(inp).normalize()
                val hWant = FieldVal.fromHex(want).normalize()
                val (result, isValid) = hInp.sqrt()
                assertEquals(valid, isValid)
                assertEquals(hWant, result.normalize())
            }
        }.stream()
    }

    @Test
    fun squareRootRandom() {
        val random = SecureRandom()
        for (k in 0..1023) {
            // Generate big integer and field value with the same random value.
            val (bigIntVal, fVal) = randIntAndFieldVal(random)
            // Calculate the square root of the value using big ints.
            val bigIntResult = modSqrt(bigIntVal)

            // Calculate the square root of the value using a field value.
            val (fValSqrt, sqrtValid) = fVal.sqrt()
            assertEquals(bigIntResult != null, sqrtValid)
            if (sqrtValid && bigIntResult != null) {
                assertEquals(Hex.encode(BigInt.toBytes(bigIntResult, 32)), fValSqrt.toString())
            }
        }
    }

    @TestFactory
    fun isGtOrEqPrimeMinusOrder(): Stream<DynamicTest> {
        return listOf(
            Tuple3(
                "zero",
                "00",
                false,
            ),

            Tuple3(
                "one",
                "01",
                false,
            ),

            Tuple3(
                "p - n - 1",
                "014551231950b75fc4402da1722fc9baed",
                false,
            ),

            Tuple3(
                "p - n",
                "014551231950b75fc4402da1722fc9baee",
                true,
            ),

            Tuple3(
                "p - n + 1",
                "014551231950b75fc4402da1722fc9baef",
                true,
            ),

            Tuple3(
                "over p - n word one",
                "014551231950b75fc4402da17233c9baee",
                true,
            ),

            Tuple3(
                "over p - n word two",
                "014551231950b75fc4403da1722fc9baee",
                true,
            ),

            Tuple3(
                "over p - n word three",
                "014551231950b79fc4402da1722fc9baee",
                true,
            ),

            Tuple3(
                "over p - n word four",
                "014551241950b75fc4402da1722fc9baee",
                true,
            ),

            Tuple3(
                "over p - n word five",
                "054551231950b75fc4402da1722fc9baee",
                true,
            ),

            Tuple3(
                "over p - n word six",
                "100000014551231950b75fc4402da1722fc9baee",
                true,
            ),

            Tuple3(
                "over p - n word seven",
                "000000000000000000400000000000014551231950b75fc4402da1722fc9baee",
                true,
            ),

            Tuple3(
                "over p - n word eight",
                "000000000001000000000000000000014551231950b75fc4402da1722fc9baee",
                true,
            ),

            Tuple3(
                "over p - n word nine",
                "000004000000000000000000000000014551231950b75fc4402da1722fc9baee",
                true,
            ),
        ).map { (name: String, inp: String, expected: Boolean) ->
            DynamicTest.dynamicTest("Test: $name") {
                val f = FieldVal.setByteSlice(Hex.decodeOrThrow(inp))
                assertEquals(expected, f.isGtOrEqPrimeMinusOrder())
            }
        }.stream()
    }

    @Test
    fun isGtOrEqPrimeMinusOrderRandom() {
        val bigPMinusN = Secp256k1Curve.p - Secp256k1Curve.n
        val random = SecureRandom()
        for (k in 0..1023) {
            // Generate big integer and field value with the same random value.
            val (bigIntVal, fVal) = randIntAndFieldVal(random)

            // Determine the value is greater than or equal to the prime minus the
            // order using big ints.
            val bigIntResult = bigIntVal >= bigPMinusN

            // Determine the value is greater than or equal to the prime minus the
            // order using a field value.
            val fValResult = fVal.isGtOrEqPrimeMinusOrder()

            assertEquals(bigIntResult, fValResult)
        }
    }

    // Calculate the modSqrt according to Shanks-Tonelli algorithm
    // Since p is always Secp256k1Curve.p, we can greatly simplify this algorithm.
    // https://www.maa.org/sites/default/files/pdf/upload_library/22/Polya/07468342.di020786.02p0470a.pdf
    private fun modSqrt(a: BigInteger): BigInteger? {
        // s = (p - 1) / 2
        val s = (Secp256k1Curve.p - BigInteger.ONE).shiftRight(1)
        // if (a^((p-1)/2 (mod p) == (-1) (mod p)), there is no square root
        // if (a^s (mod p) == (p - 1)), there is no square root
        if (a.modPow(s, Secp256k1Curve.p) == (Secp256k1Curve.p - BigInteger.ONE)) {
            return null
        }
        // result = a^((s + 1) / 2) (mod p) = a^x (mod p), x = (s + 1) / 2
        // x = (s + 1) / 2, s = (p - 1) / 2 --> x = ((p - 1) / 2 + 1) / 2 = (p + 1) / 4
        // result = a^x (mod p), x = (p + 1) / 4
        val x = (Secp256k1Curve.p + BigInteger.ONE).shiftRight(2)
        return a.modPow(x, Secp256k1Curve.p)
    }

    private fun randIntAndFieldVal(secureRandom: SecureRandom): Tuple2<BigInteger, FieldVal> {
        val data = ByteArray(32)
        secureRandom.nextBytes(data)
        // Create and return both a big integer and a field value.
        val bigIntVal = BigInt.fromBytes(data).mod(Secp256k1Curve.n)
        return Tuple(bigIntVal, FieldVal.setBytes(data))
    }
}
