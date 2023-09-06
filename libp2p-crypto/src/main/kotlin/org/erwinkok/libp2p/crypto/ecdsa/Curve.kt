// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ecdsa

import org.erwinkok.libp2p.crypto.math.BigInt
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import java.math.BigInteger
import java.security.SecureRandom
import kotlin.experimental.and
import kotlin.experimental.xor

open class Curve(
    val p: BigInteger,
    val n: BigInteger,
    val b: BigInteger,
    val g: CurvePoint,
    val bitSize: Int,
    val name: String
) {
    open fun isOnCurve(cp: CurvePoint): Boolean {
        if (cp.x.signum() < 0 || cp.x >= p || cp.y.signum() < 0 || cp.y >= p) {
            return false
        }
        // y² = x³ - 3x + b
        val y2 = (cp.y * cp.y).mod(p)
        return polynomial(cp.x) == y2
    }

    private fun polynomial(x: BigInteger): BigInteger {
        val threeX = (x shl 1) + x
        return ((x * x * x) - threeX + b).mod(p)
    }

    open fun doublePoint(cp: CurvePoint): CurvePoint {
        val z = zForAffine(cp)
        val jp = JacobianPoint(cp.x, cp.y, z)
        return affineFromJacobian(doubleJacobian(jp))
    }

    open fun addPoint(cp1: CurvePoint, cp2: CurvePoint): CurvePoint {
        require(isOnCurve(cp1))
        require(isOnCurve(cp2))
        val z1 = zForAffine(cp1)
        val z2 = zForAffine(cp2)
        return affineFromJacobian(addJacobian(JacobianPoint(cp1.x, cp1.y, z1), JacobianPoint(cp2.x, cp2.y, z2)))
    }

    // zForAffine returns a Jacobian Z value for the affine point (x, y). If x and
    // y are zero, it assumes that they represent the point at infinity because (0,
    // 0) is not on the any of the curves handled here.
    private fun zForAffine(cp: CurvePoint): BigInteger {
        return if (cp.x.signum() != 0 || cp.y.signum() != 0) {
            BigInteger.ONE
        } else {
            BigInteger.ZERO
        }
    }

    open fun scalarBaseMult(k: ByteArray): CurvePoint {
        return scalarMult(g, k)
    }

    open fun scalarMult(b: CurvePoint, k: ByteArray): CurvePoint {
        val point = JacobianPoint(b.x, b.y, BigInteger.ONE)
        var jp = JacobianPoint(BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO)
        for (bt in k) {
            var bit = bt.toInt()
            for (bitNum in 0..7) {
                jp = doubleJacobian(jp)
                if ((bit and 0x80) == 0x80) {
                    jp = addJacobian(point, jp)
                }
                bit = bit shl 1
            }
        }
        return affineFromJacobian(jp)
    }

    // doubleJacobian takes a point in Jacobian coordinates, (x, y, z), and
    // returns its double, also in Jacobian form.
    private fun doubleJacobian(jp: JacobianPoint): JacobianPoint {
        // See https://hyperelliptic.org/EFD/g1p/auto-shortw-jacobian-3.html#doubling-dbl-2001-b

        // delta = z^2
        val delta = (jp.z * jp.z).mod(p)

        // gamma = y^2
        var gamma = (jp.y * jp.y).mod(p)

        // alpha = 3 * (x - delta) * (x + delta)
        var alpha = jp.x - delta
        if (alpha.signum() < 0) {
            alpha += p
        }
        alpha *= (jp.x + delta)
        alpha = (alpha shl 1) + alpha

        // beta = x * gamma
        val beta = jp.x * gamma

        // x3 = alpha^2 - beta * 8
        var x3 = (alpha * alpha) - ((beta shl 3).mod(p))
        if (x3.signum() < 0) {
            x3 += p
        }
        x3 = x3.mod(p)

        // z3 = (y + z)^2 - gamma - delta
        var z3 = jp.y + jp.z
        z3 = (z3 * z3) - gamma
        if (z3.signum() < 0) {
            z3 += p
        }
        z3 -= delta
        if (z3.signum() < 0) {
            z3 += p
        }
        z3 = z3.mod(p)

        // y3 = alpha * (beta*4 - x3) - gamma^2 * 8
        var beta4 = (beta shl 2) - x3
        if (beta4.signum() < 0) {
            beta4 += p
        }
        var y3 = alpha * beta4
        gamma = ((gamma * gamma) shl 3).mod(p)
        y3 -= gamma
        if (y3.signum() < 0) {
            y3 += p
        }
        y3 = y3.mod(p)
        return JacobianPoint(x3, y3, z3)
    }

    // addJacobian takes two points in Jacobian coordinates, (x1, y1, z1) and
    // (x2, y2, z2) and returns their sum, also in Jacobian form.
    private fun addJacobian(p1: JacobianPoint, p2: JacobianPoint): JacobianPoint {
        // See https://hyperelliptic.org/EFD/g1p/auto-shortw-jacobian-3.html#addition-add-2007-bl
        if (p1.z.signum() == 0) {
            return p2
        }
        if (p2.z.signum() == 0) {
            return p1
        }
        // z1z1 = z1^2
        val z1z1 = (p1.z * p1.z).mod(p)
        // z2z2 = z2^2
        val z2z2 = (p2.z * p2.z).mod(p)
        // u1 = x1 * z2z2
        val u1 = (p1.x * z2z2).mod(p)
        // u2 = x2 * z1z1
        val u2 = (p2.x * z1z1).mod(p)
        // h = u1 - u2
        var h = u2 - u1
        val xEqual = h.signum() == 0
        if (h.signum() < 0) {
            h = h.add(p)
        }
        // (2 * h)^2
        var i = h shl 1
        i *= i
        // j = h * i
        val j = h * i
        // s1 = y1 * z2 * z2z2
        val s1 = (p1.y * p2.z * z2z2).mod(p)
        // s2 = y2 * z1 * z1z1
        val s2 = (p2.y * p1.z * z1z1).mod(p)
        // r = (s2 - s1) * 2
        var r = s2 - s1
        if (r.signum() < 0) {
            r += p
        }
        val yEqual = r.signum() == 0
        if (xEqual && yEqual) {
            return doubleJacobian(p1)
        }
        r = r shl 1
        // v = u1 * i
        val v = u1 * i
        // x3 = r^2 - j - 2 * v
        val x3 = ((r * r) - j - v - v).mod(p)
        // y3 = r * (v - x3) - (s1 * J * 2)
        val y3 = (r * (v - x3) - ((s1 * j) shl 1)).mod(p)
        // z3 = ((z1 + z2)^2 - z1z1 - z2z2) * h
        var z3 = p1.z + p2.z
        z3 = (((z3 * z3) - z1z1 - z2z2) * h).mod(p)
        return JacobianPoint(x3, y3, z3)
    }

    // affineFromJacobian reverses the Jacobian transform. See the comment at the
    // top of the file. If the point is ∞ it returns 0, 0.
    private fun affineFromJacobian(jp: JacobianPoint): CurvePoint {
        if (jp.z.signum() == 0) {
            return CurvePoint(BigInteger.ZERO, BigInteger.ZERO)
        }
        val zinv = jp.z.modInverse(p)
        val zinvsq = zinv * zinv
        val xOut = (jp.x * zinvsq).mod(p)
        val yOut = (jp.y * zinvsq * zinv).mod(p)
        return CurvePoint(xOut, yOut)
    }

    companion object {
        val p244: Curve by lazy { p244() }
        val p256: Curve by lazy { p256() }
        val p384: Curve by lazy { p384() }
        val p521: Curve by lazy { p521() }

        private fun p244(): Curve {
            // See FIPS 186-3, section D.2.2
            return Curve(
                name = "P-224",
                p = BigInt.fromDecimal("26959946667150639794667015087019630673557916260026308143510066298881"),
                n = BigInt.fromDecimal("26959946667150639794667015087019625940457807714424391721682722368061"),
                b = BigInt.fromHex("b4050a850c04b3abf54132565044b0b7d7bfd8ba270b39432355ffb4"),
                g = CurvePoint(
                    BigInt.fromHex("b70e0cbd6bb4bf7f321390b94a03c1d356c21122343280d6115c1d21"),
                    BigInt.fromHex("bd376388b5f723fb4c22dfe6cd4375a05a07476444d5819985007e34")
                ),
                bitSize = 224
            )
        }

        private fun p256(): Curve {
            // See FIPS 186-3, section D.2.3
            return Curve(
                name = "P-256",
                p = BigInt.fromDecimal("115792089210356248762697446949407573530086143415290314195533631308867097853951"),
                n = BigInt.fromDecimal("115792089210356248762697446949407573529996955224135760342422259061068512044369"),
                b = BigInt.fromHex("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b"),
                g = CurvePoint(
                    BigInt.fromHex("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296"),
                    BigInt.fromHex("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5")
                ),
                bitSize = 256
            )
        }

        private fun p384(): Curve {
            // See FIPS 186-3, section D.2.4
            return Curve(
                name = "P-384",
                p = BigInt.fromDecimal("39402006196394479212279040100143613805079739270465446667948293404245721771496870329047266088258938001861606973112319"),
                n = BigInt.fromDecimal("39402006196394479212279040100143613805079739270465446667946905279627659399113263569398956308152294913554433653942643"),
                b = BigInt.fromHex("b3312fa7e23ee7e4988e056be3f82d19181d9c6efe8141120314088f5013875ac656398d8a2ed19d2a85c8edd3ec2aef"),
                g = CurvePoint(
                    BigInt.fromHex("aa87ca22be8b05378eb1c71ef320ad746e1d3b628ba79b9859f741e082542a385502f25dbf55296c3a545e3872760ab7"),
                    BigInt.fromHex("3617de4a96262c6f5d9e98bf9292dc29f8f41dbd289a147ce9da3113b5f0b8c00a60b1ce1d7e819d7a431d7c90ea0e5f")
                ),
                bitSize = 384
            )
        }

        private fun p521(): Curve {
            // See FIPS 186-3, section D.2.5
            return Curve(
                name = "P-521",
                p = BigInt.fromDecimal("6864797660130609714981900799081393217269435300143305409394463459185543183397656052122559640661454554977296311391480858037121987999716643812574028291115057151"),
                n = BigInt.fromDecimal("6864797660130609714981900799081393217269435300143305409394463459185543183397655394245057746333217197532963996371363321113864768612440380340372808892707005449"),
                b = BigInt.fromHex("051953eb9618e1c9a1f929a21a0b68540eea2da725b99b315f3b8b489918ef109e156193951ec7e937b1652c0bd3bb1bf073573df883d2c34f1ef451fd46b503f00"),
                g = CurvePoint(
                    BigInt.fromHex("c6858e06b70404e9cd9e3ecb662395b4429c648139053fb521f828af606b4d3dbaa14b5e77efe75928fe1dc127a2ffa8de3348b3c1856a429bf97e7e31c2e5bd66"),
                    BigInt.fromHex("11839296a789a3bc0045c8a5fb42c7d1bd998f54449579b446817afbd17273e662c97ee72995ef42640c550b9013fad0761353c7086a272c24088be94769fd16650")
                ),
                bitSize = 521
            )
        }

        private val mask = byteArrayOf(0xff.toByte(), 0x1.toByte(), 0x3.toByte(), 0x7.toByte(), 0xf.toByte(), 0x1f.toByte(), 0x3f.toByte(), 0x7f.toByte())

        // GenerateKey returns a public/private key pair. The private key is
        // generated using the given reader, which must return random data.
        fun generateKey(curve: Curve, secureRandom: SecureRandom): CurvePoint {
            val n = curve.n
            val bitSize = n.bitLength()
            val byteLength = (bitSize + 7) / 8
            val priv = ByteArray(byteLength)
            var cp: CurvePoint? = null
            while (cp == null) {
                secureRandom.nextBytes(priv)
                // We have to mask off any excess bits in the case that the size of the
                // underlying field is not a whole number of bytes.
                priv[0] = priv[0] and mask[bitSize % 8]
                // This is because, in tests, rand will return all zeros and we don't
                // want to get the point at infinity and loop forever.
                priv[1] = priv[1] xor 0x42
                // If the scalar is out of range, sample another random number.
                if (BigInt.fromBytes(priv) < n) {
                    cp = curve.scalarBaseMult(priv)
                }
            }
            return cp
        }

        // Marshal converts a point on the curve into the uncompressed form specified in
        // section 4.3.6 of ANSI X9.62.
        fun marshal(curve: Curve, cp: CurvePoint): ByteArray {
            val byteLen = (curve.bitSize + 7) / 8
            val ret = ByteArray(1 + 2 * byteLen)
            ret[0] = 4 // uncompressed point
            BigInt.toBytes(cp.x, ret, 1, byteLen)
            BigInt.toBytes(cp.y, ret, byteLen + 1, byteLen)
            return ret
        }

        // Unmarshal converts a point, serialized by Marshal, into a CurvePoint.
        // It is an error if the point is not in uncompressed form or is not on the curve.
        // On error, return = null.
        fun unmarshal(curve: Curve, data: ByteArray): Result<CurvePoint> {
            val byteLen = (curve.bitSize + 7) / 8
            if (data.size != 1 + 2 * byteLen) {
                return Err("Could not unmarshal curve: wrong data size")
            }
            if (data[0].toInt() != 4) { // uncompressed form
                return Err("Could not unmarshal curve: invalid format")
            }
            val p = curve.p
            val x = BigInt.fromBytes(data.copyOfRange(1, byteLen + 1))
            val y = BigInt.fromBytes(data.copyOfRange(byteLen + 1, 2 * byteLen + 1))
            if (x >= p || y >= p) {
                return Err("Could not unmarshal curve: invalid points")
            }
            val curvePoint = CurvePoint(x, y)
            if (!curve.isOnCurve(curvePoint)) {
                return Err("Could not unmarshal curve: point is not on curve")
            }
            return Ok(curvePoint)
        }
    }
}
