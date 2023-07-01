// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ed25519

import org.erwinkok.libp2p.crypto.ed25519.tables.AffineLookupTable
import org.erwinkok.libp2p.crypto.ed25519.tables.NafLookupTable5
import org.erwinkok.libp2p.crypto.ed25519.tables.NafLookupTable8
import org.erwinkok.libp2p.crypto.ed25519.tables.ProjLookupTable
import kotlin.experimental.or

// Point represents a point on the edwards25519 curve.
//
// This type works similarly to math/big.Int, and all arguments and receivers
// are allowed to alias.
//
// The zero value is NOT valid, and it may be used only as a receiver.
//
// The point is internally represented in extended coordinates (X, Y, Z, T)
// where x = X/Z, y = Y/Z, and xy = T/Z per https://eprint.iacr.org/2008/522.
class Point {
    val x: Element
    val y: Element
    val z: Element
    val t: Element

    constructor(x: Element, y: Element, z: Element, t: Element) {
        this.x = x
        this.y = y
        this.z = z
        this.t = t
    }

    constructor(p: ProjP1xP1) : this(p.x * p.t, p.y * p.z, p.z * p.t, p.x * p.y)

    constructor(p: ProjP2) : this(p.x * p.z, p.y * p.z, p.z.square(), p.x * p.y)

    // SetBytes sets v = x, where x is a 32-byte encoding of v. If x does not
    // represent a valid point on the curve, SetBytes returns nil and an error and
    // the receiver is unchanged. Otherwise, SetBytes returns v.
    //
    // Note that SetBytes accepts all non-canonical encodings of valid points.
    // That is, it follows decoding rules that match most implementations in
    // the ecosystem rather than RFC 8032.
    constructor(x: ByteArray) {
        // Specifically, the non-canonical encodings that are accepted are
        //   1) the ones where the field element is not reduced (see the
        //      (*field.Element).SetBytes docs) and
        //   2) the ones where the x-coordinate is zero and the sign bit is set.
        //
        // This is consistent with crypto/ed25519/internal/edwards25519. Read more
        // at https://hdevalence.ca/blog/2020-10-04-its-25519am, specifically the
        // "Canonical A, R" section.
        if (x.size != 32) {
            throw NumberFormatException("edwards25519: invalid point encoding length")
        }
        this.y = Element(x) // throws NumberFormatException

        // -x² + y² = 1 + dx²y²
        // x² + dx²y² = x²(dy² + 1) = y² - 1
        // x² = (y² - 1) / (dy² + 1)

        // u = y² - 1
        val y2 = y.square()
        val u = y2 - Element.One

        // v = dy² + 1
        val vv = (y2 * Element.d) + Element.One

        // x = +√(u/v)
        val (r, isSquare) = Element.sqrtRatio(u, vv)
        if (!isSquare) {
            throw NumberFormatException("edwards25519: invalid point encoding")
        }

        // Select the negative square root if the sign bit is set.
        this.x = Element.select(-r, r, x[31].toInt() shr 7 != 0)
        this.z = Element.One
        this.t = this.x * this.y
    }

    // Bytes returns the canonical 32-byte encoding of v, according to RFC 8032,
    // Section 5.1.2.
    fun bytes(): ByteArray {
        return bytes(ByteArray(32))
    }

    fun bytes(buf: ByteArray): ByteArray {
        val zInv = z.invert() // zInv = 1 / Z
        val x = x * zInv // x = X / Z
        val y = y * zInv // y = Y / Z
        val out = copyFieldElement(buf, y)
        if (x.isNegative) {
            out[31] = out[31] or 0x80.toByte()
        }
        return out
    }

    operator fun plus(b: Point): Point {
        return Point(this + ProjCached(b))
    }

    operator fun plus(q: ProjCached): ProjP1xP1 {
        val pp = (y + x) * q.yplusx
        val mm = (y - x) * q.yminusx
        val tt2d = t * q.t2d
        var zz2 = z * q.z
        zz2 += zz2
        return ProjP1xP1(pp - mm, pp + mm, zz2 + tt2d, zz2 - tt2d)
    }

    operator fun plus(q: AffineCached): ProjP1xP1 {
        val pp = (y + x) * q.yplusx
        val mm = (y - x) * q.yminusx
        val tt2d = t * q.t2d
        val z2 = z + z
        return ProjP1xP1(pp - mm, pp + mm, z2 + tt2d, z2 - tt2d)
    }

    operator fun minus(b: Point): Point {
        return Point(this - ProjCached(b))
    }

    operator fun minus(q: ProjCached): ProjP1xP1 {
        val pp = (y + x) * q.yminusx // flipped sign
        val mm = (y - x) * q.yplusx // flipped sign
        val tt2d = t * q.t2d
        var zz2 = z * q.z
        zz2 += zz2
        return ProjP1xP1(pp - mm, pp + mm, zz2 - tt2d, zz2 + tt2d) // z & t flipped sign
    }

    operator fun minus(q: AffineCached): ProjP1xP1 {
        val pp = (y + x) * q.yminusx // flipped sign
        val mm = (y - x) * q.yplusx // flipped sign
        val tt2d = t * q.t2d
        val z2 = z + z
        return ProjP1xP1(pp - mm, pp + mm, z2 - tt2d, z2 + tt2d) // z & t flipped sign
    }

    operator fun unaryMinus(): Point {
        return Point(-x, y, z, -t)
    }

    // ScalarMult sets v = x * q, and returns v.
    //
    // The scalar multiplication is done in constant time.
    fun scalarMult(x: Scalar): Point {
        val table = ProjLookupTable.fromP3(this)

        // Write x = sum(x_i * 16^i)
        // so  x*Q = sum( Q*x_i*16^i )
        //         = Q*x_0 + 16*(Q*x_1 + 16*( ... + Q*x_63) ... )
        //           <------compute inside out---------
        //
        // We use the lookup table to get the x_i*Q values
        // and do four doublings to compute 16*Q
        val digits = x.signedRadix16()

        var tmp1 = IdentityPoint + table.select(digits[63]) // tmp1 = x_63*Q in P1xP1 coords
        for (i in 62 downTo 0) {
            var tmp2 = ProjP2(tmp1) // tmp2 =    (prev) in P2 coords
            tmp1 = tmp2.timesTwo() // tmp1 =  2*(prev) in P1xP1 coords
            tmp2 = ProjP2(tmp1) // tmp2 =  2*(prev) in P2 coords
            tmp1 = tmp2.timesTwo() // tmp1 =  4*(prev) in P1xP1 coords
            tmp2 = ProjP2(tmp1) // tmp2 =  4*(prev) in P2 coords
            tmp1 = tmp2.timesTwo() // tmp1 =  8*(prev) in P1xP1 coords
            tmp2 = ProjP2(tmp1) // tmp2 =  8*(prev) in P2 coords
            tmp1 = tmp2.timesTwo() // tmp1 = 16*(prev) in P1xP1 coords
            tmp1 = Point(tmp1) + table.select(digits[i]) // tmp1 = x_i*Q + 16*(prev) in P1xP1 coords
        }
        return Point(tmp1)
    }

    override fun hashCode(): Int {
        return x.hashCode() xor y.hashCode() xor z.hashCode() xor t.hashCode()
    }

    // Equal returns 1 if v is equivalent to u, and 0 otherwise.
    override fun equals(other: Any?): Boolean {
        // Xor only sets bits when they are different, so the two field values
        // can only be the same if no bits are set after xoring each word.
        // This is a constant time implementation.
        if (other === this) {
            return true
        }
        if (other !is Point) {
            return super.equals(other)
        }
        val t1 = x * other.z
        val t2 = other.x * z
        val t3 = y * other.z
        val t4 = other.y * z
        return t1 == t2 && t3 == t4
    }

    private fun copyFieldElement(buf: ByteArray, v: Element): ByteArray {
        val bytes = v.bytes()
        System.arraycopy(bytes, 0, buf, 0, 32)
        return buf
    }

    companion object {
        // identity is the point at infinity.
        val IdentityPoint = Point(
            byteArrayOf(
                1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
            )
        )

        // generator is the canonical curve basepoint. See TestGenerator for the
        // correspondence of this encoding with the values in RFC 8032.
        val GeneratorPoint = Point(
            byteArrayOf(
                0x58, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66,
                0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66,
                0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66,
                0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66
            )
        )

        val affineLookupTable: Array<AffineLookupTable> by lazy { basepointTable() }
        val nafLookupTable8: NafLookupTable8 by lazy { basepointNafTable() }

        // ScalarBaseMult sets v = x * B, where B is the canonical generator, and
        // returns v.
        //
        // The scalar multiplication is done in constant time.
        fun scalarBaseMult(x: Scalar): Point {
            // Write x = sum(x_i * 16^i) so  x*B = sum( B*x_i*16^i )
            // as described in the Ed25519 paper
            //
            // Group even and odd coefficients
            // x*B     = x_0*16^0*B + x_2*16^2*B + ... + x_62*16^62*B
            //         + x_1*16^1*B + x_3*16^3*B + ... + x_63*16^63*B
            // x*B     = x_0*16^0*B + x_2*16^2*B + ... + x_62*16^62*B
            //    + 16*( x_1*16^0*B + x_3*16^2*B + ... + x_63*16^62*B)
            //
            // We use a lookup table for each i to get x_i*16^(2*i)*B
            // and do four doublings to multiply by 16.
            val digits = x.signedRadix16()

            // Accumulate the odd components first
            var q = IdentityPoint

            var j = 1
            while (j < 64) {
                val tmp1 = q + affineLookupTable[j / 2].select(digits[j])
                q = Point(tmp1)
                j += 2
            }

            // Multiply by 16
            var tmp2 = ProjP2(q) // tmp2 =    v in P2 coords
            var tmp1 = tmp2.timesTwo() // tmp1 =  2*v in P1xP1 coords
            tmp2 = ProjP2(tmp1) // tmp2 =  2*v in P2 coords
            tmp1 = tmp2.timesTwo() // tmp1 =  4*v in P1xP1 coords
            tmp2 = ProjP2(tmp1) // tmp2 =  4*v in P2 coords
            tmp1 = tmp2.timesTwo() // tmp1 =  8*v in P1xP1 coords
            tmp2 = ProjP2(tmp1) // tmp2 =  8*v in P2 coords
            tmp1 = tmp2.timesTwo() // tmp1 = 16*v in P1xP1 coords
            q = Point(tmp1) // now v = 16*(odd components)

            // Accumulate the even components
            var i = 0
            while (i < 64) {
                tmp1 = q + affineLookupTable[i / 2].select(digits[i])
                q = Point(tmp1)
                i += 2
            }
            return q
        }

        // VarTimeDoubleScalarBaseMult sets v = a * point + b * B, where B is the canonical
        // generator, and returns v.
        //
        // Execution time depends on the inputs.
        fun varTimeDoubleScalarBaseMult(a: Scalar, point: Point, b: Scalar): Point {
            // Similarly to the single variable-base approach, we compute
            // digits and use them with a lookup table.  However, because
            // we are allowed to do variable-time operations, we don't
            // need constant-time lookups or constant-time digit
            // computations.
            //
            // So we use a non-adjacent form of some width w instead of
            // radix 16.  This is like a binary representation (one digit
            // for each binary place) but we allow the digits to grow in
            // magnitude up to 2^{w-1} so that the nonzero digits are as
            // sparse as possible.  Intuitively, this "condenses" the
            // "mass" of the scalar onto sparse coefficients (meaning
            // fewer additions).
            val aTable = NafLookupTable5.fromP3(point)
            // Because the basepoint is fixed, we can use a wider NAF
            // corresponding to a bigger table.
            val aNaf = a.nonAdjacentForm(5)
            val bNaf = b.nonAdjacentForm(8)

            // Find the first nonzero coefficient.
            var i = 255
            for (j in i downTo 0) {
                if (aNaf[j].toInt() != 0 || bNaf[j].toInt() != 0) {
                    break
                }
            }
            var tmp2 = ProjP2.Zero

            // Move from high to low bits, doubling the accumulator
            // at each iteration and checking whether there is a nonzero
            // coefficient to look up a multiple of.
            while (i >= 0) {
                var tmp1 = tmp2.timesTwo()

                // Only update v if we have a nonzero coeff to add in.
                if (aNaf[i] > 0) {
                    tmp1 = Point(tmp1) + aTable.select(aNaf[i])
                } else if (aNaf[i] < 0) {
                    tmp1 = Point(tmp1) - aTable.select((-aNaf[i]).toByte())
                }
                if (bNaf[i] > 0) {
                    tmp1 = Point(tmp1) + nafLookupTable8.select(bNaf[i])
                } else if (bNaf[i] < 0) {
                    tmp1 = Point(tmp1) - nafLookupTable8.select((-bNaf[i]).toByte())
                }
                tmp2 = ProjP2(tmp1)
                i--
            }
            return Point(tmp2)
        }

        // basepointTable is a set of 32 affineLookupTables, where table i is generated
        // from 256i * basepoint. It is precomputed the first time it's used.
        private fun basepointTable(): Array<AffineLookupTable> {
            val affineLookupTable = mutableListOf<AffineLookupTable>()
            var p = GeneratorPoint
            for (i in 0..31) {
                affineLookupTable.add(AffineLookupTable.fromP3(p))
                for (j in 0..7) {
                    p += p
                }
            }
            return affineLookupTable.toTypedArray()
        }

        private fun basepointNafTable(): NafLookupTable8 {
            return NafLookupTable8.fromP3(GeneratorPoint)
        }
    }
}
