// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ecdsa

import org.erwinkok.libp2p.crypto.math.BigInt
import org.erwinkok.result.assertErrorResult
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.security.SecureRandom

internal class CurveTest {
    @Test
    fun onCurveP244() {
        val curve = Curve.p244
        assertTrue(curve.isOnCurve(curve.g))
    }

    @Test
    fun largeOffCurveP244() {
        val curve = Curve.p244
        val large = BigInteger.ONE.shiftLeft(1000)
        assertFalse(curve.isOnCurve(CurvePoint(large, large)))
    }

    @Test
    fun offCurveP244() {
        val curve = Curve.p244
        assertFalse(curve.isOnCurve(CurvePoint(BigInteger.ONE, BigInteger.ONE)))
    }

    @Test
    fun marshalOffCurveP244() {
        val curve = Curve.p244
        val cp = CurvePoint(BigInteger.ONE, BigInteger.ONE)
        val data = Curve.marshal(curve, cp)
        assertErrorResult("Could not unmarshal curve: point is not on curve") { Curve.unmarshal(curve, data) }
    }

    @Test
    fun infinityP244() {
        val curve = Curve.p244
        val cp = Curve.generateKey(curve, SecureRandom())
        assertTrue(curve.isOnCurve(cp))
        val (x, y) = curve.scalarMult(cp, BigInt.toBytes(curve.n))
        assertEquals(0, x.signum())
        assertEquals(0, y.signum())
    }

    @Test
    fun marshalP244() {
        for (i in 0..999) {
            val curve = Curve.p244
            val cp = Curve.generateKey(curve, SecureRandom())
            assertTrue(curve.isOnCurve(cp))
            val data = Curve.marshal(curve, cp)
            val cp2 = Curve.unmarshal(curve, data).expectNoErrors()
            assertEquals(cp, cp2)
        }
    }

    @Test
    fun onCurveP256() {
        val curve = Curve.p256
        assertTrue(curve.isOnCurve(curve.g))
    }

    @Test
    fun largeOffCurveP256() {
        val curve = Curve.p256
        val large = BigInteger.ONE.shiftLeft(1000)
        assertFalse(curve.isOnCurve(CurvePoint(large, large)))
    }

    @Test
    fun offCurveP256() {
        val curve = Curve.p256
        assertFalse(curve.isOnCurve(CurvePoint(BigInteger.ONE, BigInteger.ONE)))
    }

    @Test
    fun marshalOffCurveP256() {
        val curve = Curve.p256
        val cp = CurvePoint(BigInteger.ONE, BigInteger.ONE)
        val data = Curve.marshal(curve, cp)
        assertErrorResult("Could not unmarshal curve: point is not on curve") { Curve.unmarshal(curve, data) }
    }

    @Test
    fun infinityP256() {
        val curve = Curve.p256
        val cp = Curve.generateKey(curve, SecureRandom())
        assertTrue(curve.isOnCurve(cp))
        val (x, y) = curve.scalarMult(cp, BigInt.toBytes(curve.n))
        assertEquals(0, x.signum())
        assertEquals(0, y.signum())
    }

    @Test
    fun marshalP256() {
        for (i in 0..999) {
            val curve = Curve.p256
            val cp = Curve.generateKey(curve, SecureRandom())
            assertTrue(curve.isOnCurve(cp))
            val data = Curve.marshal(curve, cp)
            val cp2 = Curve.unmarshal(curve, data).expectNoErrors()
            assertEquals(cp, cp2)
        }
    }

    @Test
    fun onCurveP384() {
        val curve = Curve.p384
        assertTrue(curve.isOnCurve(curve.g))
    }

    @Test
    fun largeOffCurveP384() {
        val curve = Curve.p384
        val large = BigInteger.ONE.shiftLeft(1000)
        assertFalse(curve.isOnCurve(CurvePoint(large, large)))
    }

    @Test
    fun offCurveP384() {
        val curve = Curve.p384
        assertFalse(curve.isOnCurve(CurvePoint(BigInteger.ONE, BigInteger.ONE)))
    }

    @Test
    fun marshalOffCurveP384() {
        val curve = Curve.p384
        val cp = CurvePoint(BigInteger.ONE, BigInteger.ONE)
        val data = Curve.marshal(curve, cp)
        assertErrorResult("Could not unmarshal curve: point is not on curve") { Curve.unmarshal(curve, data) }
    }

    @Test
    fun infinityP384() {
        val curve = Curve.p384
        assertTrue(isInfinity(curve.scalarMult(curve.g, BigInt.toBytes(curve.n))), "x^q != ∞")
        val cp = Curve.generateKey(curve, SecureRandom())
        assertTrue(curve.isOnCurve(cp))
        val (x, y) = curve.scalarMult(cp, BigInt.toBytes(curve.n))
        assertEquals(0, x.signum())
        assertEquals(0, y.signum())
    }

    @Test
    fun marshalP384() {
        for (i in 0..99) {
            val curve = Curve.p384
            val cp = Curve.generateKey(curve, SecureRandom())
            assertTrue(curve.isOnCurve(cp))
            val data = Curve.marshal(curve, cp)
            val cp2 = Curve.unmarshal(curve, data).expectNoErrors()
            assertEquals(cp, cp2)
        }
    }

    @Test
    fun onCurveP512() {
        val curve = Curve.p521
        assertTrue(curve.isOnCurve(curve.g))
    }

    @Test
    fun largeOffCurveP521() {
        val curve = Curve.p521
        val large = BigInteger.ONE.shiftLeft(1000)
        assertFalse(curve.isOnCurve(CurvePoint(large, large)))
    }

    @Test
    fun offCurveP521() {
        val curve = Curve.p521
        assertFalse(curve.isOnCurve(CurvePoint(BigInteger.ONE, BigInteger.ONE)))
    }

    @Test
    fun marshalOffCurveP521() {
        val curve = Curve.p521
        val cp = CurvePoint(BigInteger.ONE, BigInteger.ONE)
        val data = Curve.marshal(curve, cp)
        assertErrorResult("Could not unmarshal curve: point is not on curve") { Curve.unmarshal(curve, data) }
    }

    @Test
    fun infinityP521() {
        val curve = Curve.p521
        val cp = Curve.generateKey(curve, SecureRandom())
        assertTrue(curve.isOnCurve(cp))
        val (x, y) = curve.scalarMult(cp, BigInt.toBytes(curve.n))
        assertEquals(0, x.signum())
        assertEquals(0, y.signum())
    }

    @Test
    fun marshalP521() {
        for (i in 0..99) {
            val curve = Curve.p521
            val cp = Curve.generateKey(curve, SecureRandom())
            assertTrue(curve.isOnCurve(cp))
            val data = Curve.marshal(curve, cp)
            val cp2 = Curve.unmarshal(curve, data).expectNoErrors()
            assertEquals(cp, cp2)
        }
    }

    private fun isInfinity(cp: CurvePoint): Boolean {
        return cp.x.signum() == 0 && cp.y.signum() == 0
    }
}
