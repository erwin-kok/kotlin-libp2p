// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.secp256k1

import org.erwinkok.libp2p.crypto.ecdsa.Curve
import org.erwinkok.libp2p.crypto.ecdsa.CurvePoint
import org.erwinkok.libp2p.crypto.math.BigInt
import java.math.BigInteger

// References:
//   [SECG]: Recommended Elliptic Curve Domain Parameters
//     https://www.secg.org/sec2-v2.pdf
//
//   [GECC]: Guide to Elliptic Curve Cryptography (Hankerson, Menezes, Vanstone)
//
//   [BRID]: On Binary Representations of Integers with Digits -1, 0, 1
//           (Prodinger, Helmut)

// All group operations are performed using Jacobian coordinates.  For a given
// (x, y) position on the curve, the Jacobian coordinates are (x1, y1, z1)
// where x = x1/z1^2 and y = y1/z1^3.

// hexToFieldVal converts the passed hex string into a FieldVal and will panic
// if there is an error.  This is only provided for the hard-coded constants so
// errors in the source code can be detected. It will only (and must only) be
// called with hard-coded values.
class KoblitzCurve(
    p: BigInteger,
    n: BigInteger,
    b: BigInteger,
    g: CurvePoint,
    bitSize: Int,
    name: String,
) : Curve(p, n, b, g, bitSize, name) {
    // bigAffineToJacobian takes an affine point (x, y) as big integers and converts
    // it to Jacobian point with Z=1.
    private fun bigAffineToJacobian(curvePoint: CurvePoint): JacobianPoint {
        return JacobianPoint(
            FieldVal.setByteSlice(BigInt.toBytes(curvePoint.x)),
            FieldVal.setByteSlice(BigInt.toBytes(curvePoint.y)),
            FieldVal.One,
        )
    }

    // jacobianToBigAffine takes a Jacobian point (x, y, z) as field values and
    // converts it to an affine point as big integers.
    private fun jacobianToBigAffine(point: JacobianPoint): CurvePoint {
        val p = point.toAffine()

        // Convert the field values for the now affine point to big.Ints.
        return CurvePoint(
            BigInt.fromBytes(p.x.bytes()),
            BigInt.fromBytes(p.y.bytes()),
        )
    }

    // IsOnCurve returns boolean if the point (x,y) is on the curve.
    // Part of the elliptic.Curve interface. This function differs from the
    // crypto/elliptic algorithm since a = 0 not -3.
    override fun isOnCurve(cp: CurvePoint): Boolean {
        // Convert big ints to field values for faster arithmetic.
        val f = bigAffineToJacobian(cp)
        return Secp256k1Curve.isOnCurve(f.x, f.y)
    }

    // Add returns the sum of (x1,y1) and (x2,y2). Part of the elliptic.Curve
    // interface.
    override fun addPoint(cp1: CurvePoint, cp2: CurvePoint): CurvePoint {
        // A point at infinity is the identity according to the group law for
        // elliptic curve cryptography.  Thus, ∞ + P = P and P + ∞ = P.
        if (cp1.x.signum() == 0 && cp1.y.signum() == 0) {
            return cp2
        }
        if (cp2.x.signum() == 0 && cp2.y.signum() == 0) {
            return cp1
        }

        // Convert the affine coordinates from big integers to field values
        // and do the point addition in Jacobian projective space.
        val p1 = bigAffineToJacobian(cp1)
        val p2 = bigAffineToJacobian(cp2)
        val q1 = Secp256k1Curve.addNonConst(p1, p2)

        // Convert the Jacobian coordinate field values back to affine big
        // integers.
        return jacobianToBigAffine(q1)
    }

    // Double returns 2*(x1,y1). Part of the elliptic.Curve interface.
    override fun doublePoint(cp: CurvePoint): CurvePoint {
        if (cp.y.signum() == 0) {
            return CurvePoint(BigInteger.ZERO, BigInteger.ZERO)
        }

        // Convert the affine coordinates from big integers to field values
        // and do the point doubling in Jacobian projective space.
        val p = bigAffineToJacobian(cp)
        val result = Secp256k1Curve.doubleNonConst(p)

        // Convert the Jacobian coordinate field values back to affine big
        // integers.
        return jacobianToBigAffine(result)
    }

    // moduloReduce reduces k from more than 32 bytes to 32 bytes and under.  This
    // is done by doing a simple modulo curve.N.  We can do this since G^N = 1 and
    // thus any other valid point on the elliptic curve has the same order.
    private fun moduloReduce(k: ByteArray): ByteArray {
        // Since the order of G is curve.N, we can use a much smaller number
        // by doing modulo curve.N
        if (k.size > Secp256k1Curve.byteSize) {
            // Reduce k by performing modulo curve.N.
            val tmpK = BigInt.fromBytes(k).mod(Secp256k1Curve.n)
            return BigInt.toBytes(tmpK)
        }
        return k
    }

    // ScalarMult returns k*(Bx, By) where k is a big endian integer.
    override fun scalarMult(b: CurvePoint, k: ByteArray): CurvePoint {
        val kModN = ModNScalar.setByteSlice(moduloReduce(k))
        val point = bigAffineToJacobian(b)
        val result = Secp256k1Curve.scalarMultNonConst(kModN, point)
        return jacobianToBigAffine(result)
    }

    // ScalarBaseMult returns k*G where G is the base point of the group and k is a
    // big endian integer.
    override fun scalarBaseMult(k: ByteArray): CurvePoint {
        val kModN = ModNScalar.setByteSlice(moduloReduce(k))
        val result = Secp256k1Curve.scalarBaseMultNonConst(kModN)
        return jacobianToBigAffine(result)
    }

    companion object {
        val secp256k1: KoblitzCurve by lazy { s256Init() }

        private fun s256Init(): KoblitzCurve {
            // Curve parameters taken from [SECG] section 2.4.1.
            // Curve name taken from https://safecurves.cr.yp.to/.
            return KoblitzCurve(
                name = "secp256k1",
                p = Secp256k1Curve.p,
                n = Secp256k1Curve.n,
                b = BigInt.fromHex("0000000000000000000000000000000000000000000000000000000000000007"),
                g = Secp256k1Curve.g,
                bitSize = Secp256k1Curve.bitSize,
            )
        }
    }
}
