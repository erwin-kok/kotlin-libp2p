// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ed25519

import org.erwinkok.libp2p.crypto.ed25519.tables.AffineLookupTable
import org.erwinkok.libp2p.crypto.ed25519.tables.NafLookupTable8
import org.erwinkok.util.Hex
import org.erwinkok.util.Tuple3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows
import java.util.stream.Stream

internal class PointTest {
    @Test
    fun scalarMultSmallScalars() {
        val z1 = Scalar.Zero
        val p1 = Point.GeneratorPoint.scalarMult(z1)
        assertEquals(Point.IdentityPoint, p1)
        checkOnCurve(p1)

        val z2 = Scalar(byteArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
        val p2 = Point.GeneratorPoint.scalarMult(z2)
        assertEquals(Point.GeneratorPoint, p2)
        checkOnCurve(p2)
    }

    @Test
    fun scalarMultVsDalek() {
        val p = Point.GeneratorPoint.scalarMult(dalekScalar)
        assertEquals(dalekScalarBasepoint, p)
        checkOnCurve(p)
    }

    @Test
    fun baseMultVsDalek() {
        val p = Point.scalarBaseMult(dalekScalar)
        assertEquals(dalekScalarBasepoint, p)
        checkOnCurve(p)
    }

    @Test
    fun varTimeDoubleBaseMultVsDalek() {
        var p = Point.varTimeDoubleScalarBaseMult(dalekScalar, Point.GeneratorPoint, Scalar.Zero)
        assertEquals(dalekScalarBasepoint, p)
        checkOnCurve(p)
        p = Point.varTimeDoubleScalarBaseMult(Scalar.Zero, Point.GeneratorPoint, dalekScalar)
        assertEquals(dalekScalarBasepoint, p)
        checkOnCurve(p)
    }

    @Test
    fun scalarMultDistributesOverAdd() {
        for (i in 0..1023) {
            val x = ScalarTest.generateScalar()
            val y = ScalarTest.generateScalar()
            val z = x + y
            val p = Point.GeneratorPoint.scalarMult(x)
            val q = Point.GeneratorPoint.scalarMult(y)
            val r = Point.GeneratorPoint.scalarMult(z)
            val check = p + q
            checkOnCurve(p, q, r, check)
            assertEquals(check, r)
        }
    }

    @Test
    fun scalarMultNonIdentityPoint() {
        val s = ScalarTest.generateScalar()
        val p = Point.GeneratorPoint.scalarMult(s)
        val q = Point.scalarBaseMult(s)
        checkOnCurve(p, q)
        assertEquals(p, q)
    }

    @Test
    fun basepointTableGeneration() {
        // The basepoint table is 32 affineLookupTables,
        // corresponding to (16^2i)*B for table i.
        val basepointTable = Point.affineLookupTable
        var tmp3 = Point.GeneratorPoint
        for (i in 0..31) {
            // Assert equality with the hardcoded one
            assertEquals(basepointTable[i], AffineLookupTable.fromP3(tmp3))

            // Set p = (16^2)*p = 256*p = 2^8*p
            var tmp2 = ProjP2(tmp3)
            for (j in 0..6) {
                tmp2 = ProjP2(tmp2.timesTwo())
            }
            val tmp1 = tmp2.timesTwo()
            tmp3 = Point(tmp1)
            checkOnCurve(tmp3)
        }
    }

    @Test
    fun scalarMultMatchesBaseMult() {
        for (i in 0..1023) {
            val x = ScalarTest.generateScalar()
            val p = Point.GeneratorPoint.scalarMult(x)
            val q = Point.scalarBaseMult(x)
            checkOnCurve(p, q)
            assertEquals(p, q)
        }
    }

    @Test
    fun basepointNafTableGeneration() {
        val table = NafLookupTable8.fromP3(Point.GeneratorPoint)
        assertEquals(table, Point.nafLookupTable8)
    }

    @Test
    fun varTimeDoubleBaseMultMatchesBaseMult() {
        for (i in 0..1023) {
            val x = ScalarTest.generateScalar()
            val y = ScalarTest.generateScalar()
            val p = Point.varTimeDoubleScalarBaseMult(x, Point.GeneratorPoint, y)
            val q1 = Point.scalarBaseMult(x)
            val q2 = Point.scalarBaseMult(y)
            val check = q1 + q2
            checkOnCurve(p, check, q1, q2)
            assertEquals(p, check)
        }
    }

    @Test
    fun generator() {
        // These are the coordinates of B from RFC 8032, Section 5.1, converted to
        // little endian hex.
        val x = "1ad5258f602d56c9b2a7259560c72c695cdcd6fd31e2a4c0fe536ecdd3366921"
        val y = "5866666666666666666666666666666666666666666666666666666666666666"
        assertEquals(x, Hex.encode(Point.GeneratorPoint.x.bytes()))
        assertEquals(y, Hex.encode(Point.GeneratorPoint.y.bytes()))
        assertEquals(Element.One, Point.GeneratorPoint.z)
        // Check that t is correct.
        checkOnCurve(Point.GeneratorPoint)
    }

    @Test
    fun addSubNegOnBasePoint() {
        val checkLhs = Point.GeneratorPoint + Point.GeneratorPoint
        val tmpP2 = ProjP2(Point.GeneratorPoint)
        val tmpP1xP1 = tmpP2.timesTwo()
        val checkRhs = Point(tmpP1xP1)
        assertEquals(checkLhs, checkRhs)
        checkOnCurve(checkLhs, checkRhs)

        assertEquals(Point.GeneratorPoint - Point.GeneratorPoint, Point.GeneratorPoint + -Point.GeneratorPoint)

        assertEquals(Point.IdentityPoint, Point.GeneratorPoint - Point.GeneratorPoint)
        assertEquals(Point.IdentityPoint, Point.GeneratorPoint + -Point.GeneratorPoint)

        checkOnCurve(Point.GeneratorPoint - Point.GeneratorPoint, Point.GeneratorPoint + -Point.GeneratorPoint, -Point.GeneratorPoint)
    }

    @Test
    fun invalidEncodings() {
        // An invalid point, that also happens to have y > p.
        val invalid = "efffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f"
        assertEquals("edwards25519: invalid point encoding", assertThrows<NumberFormatException> { Point(Hex.decodeOrThrow(invalid)) }.message)
    }

    @TestFactory
    fun nonCanonicalPoints(): Stream<DynamicTest> {
        return listOf(
            // Points with x = 0 and the sign bit set. With x = 0 the curve equation
            // gives y² = 1, so y = ±1. 1 has two valid encodings.
            Tuple3(
                "y=1,sign-",
                "0100000000000000000000000000000000000000000000000000000000000080",
                "0100000000000000000000000000000000000000000000000000000000000000",
            ),
            Tuple3(
                "y=p+1,sign-",
                "eeffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "0100000000000000000000000000000000000000000000000000000000000000",
            ),
            Tuple3(
                "y=p-1,sign-",
                "ecffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "ecffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f",
            ),
            // Non-canonical y encodings with values 2²⁵⁵-19 (p) to 2²⁵⁵-1 (p+18).
            Tuple3(
                "y=p,sign+",
                "edffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f",
                "0000000000000000000000000000000000000000000000000000000000000000",
            ),
            Tuple3(
                "y=p,sign-",
                "edffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "0000000000000000000000000000000000000000000000000000000000000080",
            ),
            Tuple3(
                "y=p+1,sign+",
                "eeffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f",
                "0100000000000000000000000000000000000000000000000000000000000000",
            ),
            // "y=p+1,sign-" is already tested above.
            // p+2 is not a valid y-coordinate.
            Tuple3(
                "y=p+3,sign+",
                "f0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f",
                "0300000000000000000000000000000000000000000000000000000000000000",
            ),
            Tuple3(
                "y=p+3,sign-",
                "f0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "0300000000000000000000000000000000000000000000000000000000000080",
            ),
            Tuple3(
                "y=p+4,sign+",
                "f1ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f",
                "0400000000000000000000000000000000000000000000000000000000000000",
            ),
            Tuple3(
                "y=p+4,sign-",
                "f1ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "0400000000000000000000000000000000000000000000000000000000000080",
            ),
            Tuple3(
                "y=p+5,sign+",
                "f2ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f",
                "0500000000000000000000000000000000000000000000000000000000000000",
            ),
            Tuple3(
                "y=p+5,sign-",
                "f2ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "0500000000000000000000000000000000000000000000000000000000000080",
            ),
            Tuple3(
                "y=p+6,sign+",
                "f3ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f",
                "0600000000000000000000000000000000000000000000000000000000000000",
            ),
            Tuple3(
                "y=p+6,sign-",
                "f3ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "0600000000000000000000000000000000000000000000000000000000000080",
            ),
            // p+7 is not a valid y-coordinate.
            // p+8 is not a valid y-coordinate.
            Tuple3(
                "y=p+9,sign+",
                "f6ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f",
                "0900000000000000000000000000000000000000000000000000000000000000",
            ),
            Tuple3(
                "y=p+9,sign-",
                "f6ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "0900000000000000000000000000000000000000000000000000000000000080",
            ),
            Tuple3(
                "y=p+10,sign+",
                "f7ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f",
                "0a00000000000000000000000000000000000000000000000000000000000000",
            ),
            Tuple3(
                "y=p+10,sign-",
                "f7ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "0a00000000000000000000000000000000000000000000000000000000000080",
            ),
            // p+11 is not a valid y-coordinate.
            // p+12 is not a valid y-coordinate.
            // p+13 is not a valid y-coordinate.
            Tuple3(
                "y=p+14,sign+",
                "fbffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f",
                "0e00000000000000000000000000000000000000000000000000000000000000",
            ),
            Tuple3(
                "y=p+14,sign-",
                "fbffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "0e00000000000000000000000000000000000000000000000000000000000080",
            ),
            Tuple3(
                "y=p+15,sign+",
                "fcffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f",
                "0f00000000000000000000000000000000000000000000000000000000000000",
            ),
            Tuple3(
                "y=p+15,sign-",
                "fcffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "0f00000000000000000000000000000000000000000000000000000000000080",
            ),
            Tuple3(
                "y=p+16,sign+",
                "fdffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f",
                "1000000000000000000000000000000000000000000000000000000000000000",
            ),
            Tuple3(
                "y=p+16,sign-",
                "fdffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "1000000000000000000000000000000000000000000000000000000000000080",
            ),
            // p+17 is not a valid y-coordinate.
            Tuple3(
                "y=p+18,sign+",
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f",
                "1200000000000000000000000000000000000000000000000000000000000000",
            ),
            Tuple3(
                "y=p+18,sign-",
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "1200000000000000000000000000000000000000000000000000000000000080",
            ),
        ).map { (name: String, test_encoding: String, test_canonical: String) ->
            DynamicTest.dynamicTest("Test: $name") {
                val p1 = Point(Hex.decodeOrThrow(test_encoding))
                val p2 = Point(Hex.decodeOrThrow(test_canonical))
                assertEquals(p1, p2)
                assertEquals(test_canonical, Hex.encode(p1.bytes()))
                checkOnCurve(p1, p2)
            }
        }.stream()
    }

    companion object {
        // a random scalar generated using dalek.
        private val dalekScalar = Scalar(Hex.decodeOrThrow("db6a7209aef99b5945cbc95d5c74eabb4e7367acb6623e67bb880d64f86e0c04"))

        // the above, times the edwards25519 basepoint.
        private val dalekScalarBasepoint = Point(Hex.decodeOrThrow("f4ef7c0a34557b9f723bb61ef94609911cb9c06c17282d8b432b05186a543e48"))

        private fun checkOnCurve(vararg points: Point) {
            for (p in points) {
                val xx = p.x.square()
                val yy = p.y.square()
                val zz = p.z.square()
                val zzzz = zz.square()
                // -x² + y² = 1 + dx²y²
                // -(X/Z)² + (Y/Z)² = 1 + d(X/Z)²(Y/Z)²
                // (-X² + Y²)/Z² = 1 + (dX²Y²)/Z⁴
                // (-X² + Y²)*Z² = Z⁴ + dX²Y²
                assertEquals((yy - xx) * zz, (Element.d * xx * yy) + zzzz)

                // xy = T/Z
                assertEquals(p.x * p.y, p.z * p.t)
            }
        }
    }
}
