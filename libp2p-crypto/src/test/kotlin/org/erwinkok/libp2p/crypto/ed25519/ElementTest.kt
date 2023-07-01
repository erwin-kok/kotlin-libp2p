// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ed25519

import org.erwinkok.libp2p.crypto.math.BigInt
import org.erwinkok.util.Hex
import org.erwinkok.util.Tuple2
import org.erwinkok.util.Tuple4
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.util.stream.Stream
import kotlin.experimental.and
import kotlin.random.Random

internal class ElementTest {
    // weirdLimbs can be combined to generate a range of edge-case field elements.
    // 0 and -1 are intentionally more weighted, as they combine well.
    private val weirdLimbs51 = longArrayOf(
        0L, 0L, 0L, 0L,
        1L,
        19L - 1,
        19L,
        0x2aaaaaaaaaaaaL,
        0x5555555555555L,
        (1L shl 51) - 20,
        (1L shl 51) - 19,
        (1L shl 51) - 1, (1L shl 51) - 1,
        (1L shl 51) - 1, (1L shl 51) - 1,
    )

    private val weirdLimbs52 = longArrayOf(
        0L, 0L, 0L, 0L, 0L, 0L,
        1L,
        19L - 1,
        19L,
        0x2aaaaaaaaaaaaL,
        0x5555555555555L,
        (1L shl 51) - 20,
        (1L shl 51) - 19,
        (1L shl 51) - 1, (1L shl 51) - 1,
        (1L shl 51) - 1, (1L shl 51) - 1,
        (1L shl 51) - 1, (1L shl 51) - 1,
        1L shl 51,
        (1L shl 51) + 1,
        (1L shl 52) - 19,
        (1L shl 52) - 1,
    )

    @Test
    fun multiplyDistributesOverAdd() {
        for (i in 0..1023) {
            val x = generate()
            val y = generate()
            val z = generate()

            // Compute t1 = (x+y)*z
            val t1 = (x + y) * z

            // Compute t2 = x*z + y*z
            val t2 = (x * z) + (y * z)
            assertEquals(t1, t2)
            assertTrue(t1.isInBounds)
            assertTrue(t2.isInBounds)
        }
    }

    @Test
    fun mul64to128() {
        var r = Element.mul64(5L, 5L)
        assertEquals(0x19, r.lo)
        assertEquals(0x0, r.hi)

        r = Element.mul64(18014398509481983L, 18014398509481983L)
        assertEquals(-0x7fffffffffffffL, r.lo)
        assertEquals(0xfffffffffffL, r.hi)

        r = Element.mul64(1125899906842661L, 2097155L)
        r = Element.addMul64(r, 1125899906842661L, 2097155L)
        r = Element.addMul64(r, 1125899906842661L, 2097155L)
        r = Element.addMul64(r, 1125899906842661L, 2097155L)
        r = Element.addMul64(r, 1125899906842661L, 2097155L)
        assertEquals(16888498990613035L, r.lo)
        assertEquals(640L, r.hi)
    }

    @Test
    fun setBytesRoundTrip() {
        for (i in 0..1023) {
            val input = Random.nextBytes(32)
            val fe = Element(input)
            // Mask the most significant bit as it's ignored by SetBytes. (Now
            // instead of earlier so we check the masking in SetBytes is working.)
            val mask = ((1 shl 7) - 1).toByte()
            input[input.size - 1] = input[input.size - 1] and mask
            assertArrayEquals(input, fe.bytes())
            assertTrue(fe.isInBounds)

            val r = Element(fe.bytes())
            // Intentionally not using Equal not to go through Bytes again.
            // Calling reduce because both Generate and SetBytes can produce
            // non-canonical representations.
            assertEquals(fe.reduce(), r.reduce())
        }
    }

    @TestFactory
    fun setBytesRoundTripPredefined(): Stream<DynamicTest> {
        return listOf(
            Tuple2(
                Element(358744748052810L, 1691584618240980L, 977650209285361L, 1429865912637724L, 560044844278676L),
                Hex.decodeOrThrow("4ad145c54646a1de38e2e513703c195cbb4ade38329933e9284a3906a0b9d51f"),
            ),
            Tuple2(
                Element(84926274344903L, 473620666599931L, 365590438845504L, 1028470286882429L, 2146499180330972L),
                Hex.decodeOrThrow("c7176a703d4dd84fba3c0b760d10670f2a2053fa2c39ccc64ec7fd7792ac037a"),
            ),
        ).map { (test_fe: Element, test_b: ByteArray) ->
            DynamicTest.dynamicTest("Test: $test_fe") {
                assertArrayEquals(test_b, test_fe.bytes())
                assertEquals(test_fe, Element(test_b))
            }
        }.stream()
    }

    @Test
    fun bytesBigEquivalence() {
        for (i in 0..1023) {
            val input = Random.nextBytes(32)
            val fe = Element(input)
            val mask = ((1 shl 7) - 1).toByte()
            input[input.size - 1] = input[input.size - 1] and mask // mask the most significant bit
            val fe1 = fromBig(BigInt.fromBytes(swapEndianness(input)))
            assertEquals(fe, fe1)
            val buf = swapEndianness(BigInt.toBytes(toBig(fe))).copyOf(32) // pad with zeroes
            assertEquals(Hex.encode(fe.bytes()), Hex.encode(buf))
            assertTrue(fe.isInBounds)
            assertTrue(fe1.isInBounds)
        }
    }

    @Test
    fun decimalConstants() {
        val sqrtM1String = "19681161376707505956807079304988542015446066515923890162744021073123829784752"
        val exp = fromBig(BigInt.fromDecimal(sqrtM1String))
        assertEquals(Element.SqrtM1, exp)
    }

    @Test
    fun consistency() {
        val x = Element(1, 1, 1, 1, 1)
        assertEquals(x * x, x.square())
        for (i in 0..1023) {
            val bytes = Random.nextBytes(32)
            val x1 = Element(bytes)
            assertEquals(x1 * x1, x1.square())
        }
    }

    @Test
    fun invert() {
        val x = Element(1, 1, 1, 1, 1)
        assertEquals(Element.One, (x * x.invert()).reduce())
        for (i in 0..1023) {
            val bytes = Random.nextBytes(32)
            val x1 = Element(bytes)
            assertEquals(Element.One, (x1 * x1.invert()).reduce())
        }
        assertEquals(Element.Zero, Element.Zero.invert())
    }

    @Test
    fun selectSwap() {
        val a = Element(358744748052810L, 1691584618240980L, 977650209285361L, 1429865912637724L, 560044844278676L)
        val b = Element(84926274344903L, 473620666599931L, 365590438845504L, 1028470286882429L, 2146499180330972L)
        val c = Element.select(a, b, true)
        val d = Element.select(a, b, false)
        assertEquals(c, a)
        assertEquals(d, b)
        val (c1, d1) = Element.swap(c, d, false)
        assertEquals(c1, a)
        assertEquals(d1, b)
        val (c2, d2) = Element.swap(c, d, true)
        assertEquals(c2, b)
        assertEquals(d2, a)
    }

    @Test
    fun mult32() {
        for (i in 0..1023) {
            val x = generate()
            val y = Random.nextInt()
            var t1 = Element.Zero
            for (j in 0..99) {
                t1 = t1.mult32(x, y)
            }
            val ty = Element(Integer.toUnsignedLong(y), 0, 0, 0, 0)
            var t2 = Element.Zero
            for (j in 0..99) {
                t2 = x * ty
            }
            assertEquals(t1, t2)
            assertTrue(t1.isInBounds)
            assertTrue(t2.isInBounds)
        }
    }

    @TestFactory
    fun sqrtRatio(): Stream<DynamicTest> {
        return listOf(
            // If u is 0, the function is defined to return (0, TRUE), even if v
            // is zero. Note that where used in this package, the denominator v
            // is never zero.
            Tuple4(
                "0000000000000000000000000000000000000000000000000000000000000000",
                "0000000000000000000000000000000000000000000000000000000000000000",
                true,
                "0000000000000000000000000000000000000000000000000000000000000000",
            ),
            // 0/1 == 0²
            Tuple4(
                "0000000000000000000000000000000000000000000000000000000000000000",
                "0100000000000000000000000000000000000000000000000000000000000000",
                true,
                "0000000000000000000000000000000000000000000000000000000000000000",
            ),
            // If u is non-zero and v is zero, defined to return (0, FALSE).
            Tuple4(
                "0100000000000000000000000000000000000000000000000000000000000000",
                "0000000000000000000000000000000000000000000000000000000000000000",
                false,
                "0000000000000000000000000000000000000000000000000000000000000000",
            ),
            // 2/1 is not square in this field.
            Tuple4(
                "0200000000000000000000000000000000000000000000000000000000000000",
                "0100000000000000000000000000000000000000000000000000000000000000",
                false,
                "3c5ff1b5d8e4113b871bd052f9e7bcd0582804c266ffb2d4f4203eb07fdb7c54",
            ),
            // 4/1 == 2²
            Tuple4(
                "0400000000000000000000000000000000000000000000000000000000000000",
                "0100000000000000000000000000000000000000000000000000000000000000",
                true,
                "0200000000000000000000000000000000000000000000000000000000000000",
            ),
            // 1/4 == (2⁻¹)² == (2^(p-2))² per Euler's theorem
            Tuple4(
                "0100000000000000000000000000000000000000000000000000000000000000",
                "0400000000000000000000000000000000000000000000000000000000000000",
                true,
                "f6ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff3f",
            ),
        ).map { (test_u: String, test_v: String, test_wasSquare: Boolean, test_r: String) ->
            DynamicTest.dynamicTest("Test: $test_u $test_v") {
                val u = Element(Hex.decodeOrThrow(test_u))
                val v = Element(Hex.decodeOrThrow(test_v))
                val want = Element(Hex.decodeOrThrow(test_r))
                val got = Element.sqrtRatio(u, v)
                assertEquals(want, got.r)
                assertEquals(test_wasSquare, got.isSquare)
            }
        }.stream()
    }

    private fun toBig(v: Element): BigInteger {
        var r = BigInteger.ZERO
        val buf = v.bytes()
        var index = 0
        for (i in 0..31) {
            var b: Int = buf[i].toInt()
            for (j in 0..7) {
                if (b and 0x01 != 0) {
                    r = r.setBit(index)
                }
                index++
                b = b shr 1
            }
        }
        return r
    }

    private fun fromBig(n: BigInteger): Element {
        if (n.bitLength() > 32 * 8) {
            throw NumberFormatException("edwards25519: invalid field element input size")
        }
        val buf = ByteArray(32)
        var index = 0
        for (i in 0..31) {
            var v = 0
            for (j in 0..7) {
                if (n.testBit(index++)) {
                    v = v or (1 shl j)
                }
            }
            buf[i] = v.toByte()
        }
        return Element(buf)
    }

    private fun swapEndianness(buf: ByteArray): ByteArray {
        for (i in 0 until buf.size / 2) {
            val s = buf[i]
            buf[i] = buf[buf.size - i - 1]
            buf[buf.size - i - 1] = s
        }
        return buf
    }

    private fun generateFieldElement(): Element {
        val maskLow52Bits = (1L shl 52) - 1
        return Element(
            Random.nextLong() and maskLow52Bits,
            Random.nextLong() and maskLow52Bits,
            Random.nextLong() and maskLow52Bits,
            Random.nextLong() and maskLow52Bits,
            Random.nextLong() and maskLow52Bits,
        )
    }

    private fun generateWeirdFieldElement(): Element {
        return Element(
            weirdLimbs52[Random.nextInt(weirdLimbs52.size)],
            weirdLimbs51[Random.nextInt(weirdLimbs51.size)],
            weirdLimbs51[Random.nextInt(weirdLimbs51.size)],
            weirdLimbs51[Random.nextInt(weirdLimbs51.size)],
            weirdLimbs51[Random.nextInt(weirdLimbs51.size)],
        )
    }

    private fun generate(): Element {
        return if (Random.nextInt(2) == 0) {
            generateWeirdFieldElement()
        } else {
            generateFieldElement()
        }
    }
}
