// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ed25519

import org.erwinkok.libp2p.crypto.math.BigInt
import org.erwinkok.util.Hex
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigInteger
import kotlin.experimental.and
import kotlin.random.Random

internal class ScalarTest {
    @Test
    fun generate() {
        for (i in 0..1023) {
            val s = generateScalar()
            assertTrue(s.isReduced, "Generated scalar $s was not reduced")
        }
    }

    @Test
    fun setCanonicalBytes() {
        for (i in 0..1023) {
            val input = Random.nextBytes(32)
            val mask = (1 shl 4) - 1
            input[input.size - 1] = input[input.size - 1] and mask.toByte()
            val s = Scalar.setCanonicalBytes(input)
            assertArrayEquals(input, s.bytes())
            assertTrue(s.isReduced)

            val sc1 = generateScalar()
            val sc2 = Scalar.setCanonicalBytes(sc1.bytes())
            assertEquals(sc1, sc2)

            val b = ByteArray(32)
            System.arraycopy(Scalar.MinusOne.n, 0, b, 0, 32)
            b[31] = (b[31] + 1).toByte()
            assertEquals("invalid scalar encoding", assertThrows<NumberFormatException> { Scalar.setCanonicalBytes(b) }.message)
        }
    }

    @Test
    fun setUniformBytes() {
        var mod = BigInt.fromDecimal("27742317777372353535851937790883648493")
        mod = mod.add(BigInteger.ONE.shiftLeft(252))
        for (i in 0..1023) {
            var input = ByteArray(64)
            Random.nextBytes(input)
            input = Hex.decodeOrThrow("3521f24450efc4a0afe0c1e4c8a11b528b17eec6ae8a419171fd905939d16ae7aa6929ad43201dd8b88b62b0c45688b3d0acaa075d3083e20a47477829fc60f3")
            val s = Scalar.setUniformBytes(input)
            assertTrue(s.isReduced)
            val scBig = bigIntFromLittleEndianBytes(s.n)
            val inBig = bigIntFromLittleEndianBytes(input).mod(mod)
            assertEquals(0, inBig.compareTo(scBig))
        }
    }

    @Test
    fun setBytesWithClamping() {
        val r1 = "633d368491364dc9cd4c1bf891b1d59460face1644813240a313e61f2c88216e"
        val s1 = Scalar.setBytesWithClamping(Hex.decodeOrThrow(r1))
        val p1 = Point.scalarBaseMult(s1)
        assertEquals("1d87a9026fd0126a5736fe1628c95dd419172b5b618457e041c9c861b2494a94", Hex.encode(p1.bytes()))

        val r2 = "0000000000000000000000000000000000000000000000000000000000000000"
        val s2 = Scalar.setBytesWithClamping(Hex.decodeOrThrow(r2))
        val p2 = Point.scalarBaseMult(s2)
        assertEquals("693e47972caf527c7883ad1b39822f026f47db2ab0e1919955b8993aa04411d1", Hex.encode(p2.bytes()))

        val r3 = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
        val s3 = Scalar.setBytesWithClamping(Hex.decodeOrThrow(r3))
        val p3 = Point.scalarBaseMult(s3)
        assertEquals("12e9a68b73fd5aacdbcaf3e88c46fea6ebedb1aa84eed1842f07f8edab65e3a7", Hex.encode(p3.bytes()))
    }

    @Test
    fun scalarMultiplyDistributesOverAdd() {
        // Compute t1 = (x+y)*z
        for (i in 0..1023) {
            val x = generateScalar()
            val y = generateScalar()
            val z = generateScalar()
            val t1 = (x + y) * z
            assertTrue(t1.isReduced)

            // Compute t2 = x*z + y*z
            val t2 = x * z
            val t3 = y * z
            assertTrue(t3.isReduced)

            assertEquals(t1, t2 + t3)
        }
    }

    @Test
    fun scalarAddLikeSubNeg() {
        for (i in 0..1023) {
            val x = generateScalar()
            val y = generateScalar()
            // Compute t1 = x - y
            val t1 = x - y
            // Compute t2 = -y + x
            val t2 = -y + x
            assertEquals(t1, t2)
            assertTrue(t1.isReduced, "Failed for $x and $y")
        }
    }

    @Test
    fun testScalarNonAdjacentForm() {
        val s = Scalar(Hex.decodeOrThrow("1a0e978a90f6622d3747023f8ad8264da758aa1b88e040d1589e7b7f2376ef09"))
        val expectedNaf = Hex.decodeOrThrow(
            "000d0000000000000007000000000000f700000000f500000000030000000001" +
                "000000000900000000fb00000000000003000000000b000000000b0000000000f" +
                "70000000000fd0000000009000000000001000000000000ff0000000000090000" +
                "0000f100000000f900000000f7000000000005000000000d0000000000fd00000" +
                "000f500000000f900000000f3000000000b00000000f700000000000100000000" +
                "00f10000000001000000000700000000000000000500000000000d00000000000" +
                "00b00000000000f0000000000f700000000000000ff0000000000000007000000" +
                "0000f100000000000f000000000f000000000f00000000000100000000",
        )
        val sNaf = s.nonAdjacentForm(5)
        for (i in 0..255) {
            assertEquals(expectedNaf[i], sNaf[i])
        }
    }

    @Test
    fun scalarEqual() {
        assertNotEquals(Scalar.One, Scalar.MinusOne)
        assertEquals(Scalar.MinusOne, Scalar.MinusOne)
    }

    private fun bigIntFromLittleEndianBytes(b: ByteArray): BigInteger {
        val bb = ByteArray(b.size)
        for (i in b.indices) {
            bb[i] = b[b.size - i - 1]
        }
        return BigInt.fromBytes(bb)
    }

    companion object {
        fun generateScalar(): Scalar {
            val diceRoll = Random.nextInt(100)
            if (diceRoll == 0) {
                return Scalar.Zero
            } else if (diceRoll == 1) {
                return Scalar.One
            } else if (diceRoll == 2) {
                return Scalar.MinusOne
            } else if (diceRoll < 5) {
                // Generate a low scalar in [0, 2^125).
                val r = Random.nextBytes(16)
                val v = ByteArray(32)
                System.arraycopy(r, 0, v, 0, 16)
                val mask = (1 shl 5) - 1
                v[15] = v[15] and mask.toByte()
                return Scalar(v)
            } else if (diceRoll < 10) {
                // Generate a high scalar in [2^252, 2^252 + 2^124).
                val v = ByteArray(32)
                v[31] = (1 shl 4).toByte()
                val r = Random.nextBytes(16)
                System.arraycopy(r, 0, v, 0, 16)
                val mask = (1 shl 4) - 1
                v[15] = v[15] and mask.toByte()
                return Scalar(v)
            } else {
                // Generate a valid scalar in [0, l) by returning [0, 2^252) which has a
                // negligibly different distribution (the former has a 2^-127.6 chance
                // of being out of the latter range).
                val v = Random.nextBytes(32)
                val byte = (1 shl 4) - 1
                v[31] = v[31] and byte.toByte()
                return Scalar(v)
            }
        }
    }
}
