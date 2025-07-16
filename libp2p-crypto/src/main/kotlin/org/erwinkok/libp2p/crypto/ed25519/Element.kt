// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ed25519

import org.erwinkok.util.Hex
import org.erwinkok.util.Tuple
import org.erwinkok.util.Tuple2
import kotlin.experimental.or

// Element represents an element of the field GF(2^255-19). Note that this
// is not a cryptographically secure group, and should only be used to interact
// with edwards25519.Point coordinates.
//
// This type works similarly to math/big.Int, and all arguments and receivers
// are allowed to alias.
//
// The zero value is a valid zero element.
class Element {
    // An element t represents the integer
    //     t.l0 + t.l1*2^51 + t.l2*2^102 + t.l3*2^153 + t.l4*2^204
    //
    // Between operations, all limbs are expected to be lower than 2^52.
    private val _l0: Long
    private val _l1: Long
    private val _l2: Long
    private val _l3: Long
    private val _l4: Long

    constructor(l0: Long, l1: Long, l2: Long, l3: Long, l4: Long) {
        this._l0 = l0
        this._l1 = l1
        this._l2 = l2
        this._l3 = l3
        this._l4 = l4
    }

    constructor(other: Element) {
        this._l0 = other._l0
        this._l1 = other._l1
        this._l2 = other._l2
        this._l3 = other._l3
        this._l4 = other._l4
    }

    // SetBytes sets v to x, which must be a 32-byte little-endian encoding.
    //
    // Consistent with RFC 7748, the most significant bit (the high bit of the
    // last byte) is ignored, and non-canonical values (2^255-19 through 2^255-1)
    // are accepted. Note that this is laxer than specified by RFC 8032.
    constructor(x: ByteArray) {
        if (x.size != 32) {
            throw NumberFormatException("edwards25519: invalid field element input size")
        }
        // Bits 0:51 (bytes 0:8, bits 0:64, shift 0, mask 51).
        this._l0 = uint64(x, 0) and MASK_LOW_51_BITS
        // Bits 51:102 (bytes 6:14, bits 48:112, shift 3, mask 51).
        this._l1 = (uint64(x, 6) ushr 3) and MASK_LOW_51_BITS
        // Bits 102:153 (bytes 12:20, bits 96:160, shift 6, mask 51).
        this._l2 = (uint64(x, 12) ushr 6) and MASK_LOW_51_BITS
        // Bits 153:204 (bytes 19:27, bits 152:216, shift 1, mask 51).
        this._l3 = (uint64(x, 19) ushr 1) and MASK_LOW_51_BITS
        // Bits 204:251 (bytes 24:32, bits 192:256, shift 12, mask 51).
        // Note: not bytes 25:33, shift 4, to avoid overread.
        this._l4 = (uint64(x, 24) ushr 12) and MASK_LOW_51_BITS
    }

    // IsNegative returns 1 if v is negative, and 0 otherwise.
    val isNegative: Boolean
        get() = bytes()[0].toInt() and 1 != 0

    // isInBounds returns whether the element is within the expected bit size bounds
    // after a light reduction.
    val isInBounds: Boolean
        get() = len64(_l0) <= 52 && len64(_l1) <= 52 && len64(_l2) <= 52 && len64(_l3) <= 52 && len64(_l4) <= 52

    operator fun plus(b: Element): Element {
        val l0 = _l0 + b._l0
        val l1 = _l1 + b._l1
        val l2 = _l2 + b._l2
        val l3 = _l3 + b._l3
        val l4 = _l4 + b._l4
        // Using the generic implementation here is actually faster than the
        // assembly. Probably because the body of this function is so simple that
        // the compiler can figure out better optimizations by inlining the carry
        // propagation.
        return carryPropagate(l0, l1, l2, l3, l4)
    }

    operator fun minus(b: Element): Element {
        // We first add 2 * p, to guarantee the subtraction won't underflow, and
        // then subtract b (which can be up to 2^255 + 2^13 * 19).
        val l0 = _l0 + 0xFFFFFFFFFFFDAL - b._l0
        val l1 = _l1 + 0xFFFFFFFFFFFFEL - b._l1
        val l2 = _l2 + 0xFFFFFFFFFFFFEL - b._l2
        val l3 = _l3 + 0xFFFFFFFFFFFFEL - b._l3
        val l4 = _l4 + 0xFFFFFFFFFFFFEL - b._l4
        return carryPropagate(l0, l1, l2, l3, l4)
    }

    // Negate sets v = -a, and returns v.
    operator fun unaryMinus(): Element {
        return Zero - this
    }

    operator fun times(b: Element): Element {
        return feMul(this, b)
    }

    // reduce reduces v modulo 2^255 - 19 and returns it.
    fun reduce(): Element {
        val e = carryPropagate(this._l0, this._l1, this._l2, this._l3, this._l4)

        // After the light reduction we now have a field element representation
        // v < 2^255 + 2^13 * 19, but need v < 2^255 - 19.

        // If v >= 2^255 - 19, then v + 19 >= 2^255, which would overflow 2^255 - 1,
        // generating a carry. That is, c will be 0 if v < 2^255 - 19, and 1 otherwise.
        var c = e._l0 + 19 ushr 51
        c = e._l1 + c ushr 51
        c = e._l2 + c ushr 51
        c = e._l3 + c ushr 51
        c = e._l4 + c ushr 51

        // If v < 2^255 - 19 and c = 0, this will be a no-op. Otherwise, it's
        // effectively applying the reduction identity to the carry.
        val l0 = e._l0 + 19 * c
        val l1 = e._l1 + (l0 ushr 51)
        val l2 = e._l2 + (l1 ushr 51)
        val l3 = e._l3 + (l2 ushr 51)
        val l4 = e._l4 + (l3 ushr 51)
        return Element(l0 and MASK_LOW_51_BITS, l1 and MASK_LOW_51_BITS, l2 and MASK_LOW_51_BITS, l3 and MASK_LOW_51_BITS, l4 and MASK_LOW_51_BITS)
    }

    // Invert sets v = 1/z mod p, and returns v.
    //
    // If z == 0, Invert returns v = 0.
    fun invert(): Element {
        // Inversion is implemented as exponentiation with exponent p − 2. It uses the
        // same sequence of 255 squarings and 11 multiplications as [Curve25519].
        val z2 = this.square() // 2
        var t = z2.square() // 4
        t = t.square() // 8
        val z9 = t * this // 9
        val z11 = z9 * z2 // 11
        t = z11.square() // 22
        val z2_5_0 = t * z9 // 31 = 2^5 - 2^0
        t = z2_5_0.square() // 2^6 - 2^1
        for (i in 0..3) {
            t = t.square() // 2^10 - 2^5
        }
        val z2_10_0 = t * z2_5_0 // 2^10 - 2^0
        t = z2_10_0.square() // 2^11 - 2^1
        for (i in 0..8) {
            t = t.square() // 2^20 - 2^10
        }
        val z2_20_0 = t * z2_10_0 // 2^20 - 2^0
        t = z2_20_0.square() // 2^21 - 2^1
        for (i in 0..18) {
            t = t.square() // 2^40 - 2^20
        }
        t *= z2_20_0 // 2^40 - 2^0
        t = t.square() // 2^41 - 2^1
        for (i in 0..8) {
            t = t.square() // 2^50 - 2^10
        }
        val z2_50_0 = t * z2_10_0 // 2^50 - 2^0
        t = z2_50_0.square() // 2^51 - 2^1
        for (i in 0..48) {
            t = t.square() // 2^100 - 2^50
        }
        val z2_100_0 = t * z2_50_0 // 2^100 - 2^0
        t = z2_100_0.square() // 2^101 - 2^1
        for (i in 0..98) {
            t = t.square() // 2^200 - 2^100
        }
        t *= z2_100_0 // 2^200 - 2^0
        t = t.square() // 2^201 - 2^1
        for (i in 0..48) {
            t = t.square() // 2^250 - 2^50
        }
        t *= z2_50_0 // 2^250 - 2^0
        t = t.square() // 2^251 - 2^1
        t = t.square() // 2^252 - 2^2
        t = t.square() // 2^253 - 2^3
        t = t.square() // 2^254 - 2^4
        t = t.square() // 2^255 - 2^5
        return t * z11 // 2^255 - 21
    }

    // Bytes returns the canonical 32-byte little-endian encoding of v.
    fun bytes(): ByteArray {
        // This function is outlined to make the allocations inline in the caller
        // rather than happen on the heap.
        return bytes(ByteArray(32))
    }

    fun bytes(out: ByteArray): ByteArray {
        val t = Element(this).reduce()
        if (0 < out.size) out[0] = out[0] or t._l0.toByte()
        if (1 < out.size) out[1] = out[1] or (t._l0 shr 8).toByte()
        if (2 < out.size) out[2] = out[2] or (t._l0 shr 16).toByte()
        if (3 < out.size) out[3] = out[3] or (t._l0 shr 24).toByte()
        if (4 < out.size) out[4] = out[4] or (t._l0 shr 32).toByte()
        if (5 < out.size) out[5] = out[5] or (t._l0 shr 40).toByte()
        if (6 < out.size) {
            out[6] = out[6] or (t._l0 shr 48).toByte()
            out[6] = out[6] or (t._l1 shl 3).toByte()
        }
        if (7 < out.size) {
            out[7] = out[7] or (t._l0 shr 56).toByte()
            out[7] = out[7] or (t._l1 shl 3 shr 8).toByte()
        }
        if (8 < out.size) out[8] = out[8] or (t._l1 shl 3 shr 16).toByte()
        if (9 < out.size) out[9] = out[9] or (t._l1 shl 3 shr 24).toByte()
        if (10 < out.size) out[10] = out[10] or (t._l1 shl 3 shr 32).toByte()
        if (11 < out.size) out[11] = out[11] or (t._l1 shl 3 shr 40).toByte()
        if (12 < out.size) {
            out[12] = out[12] or (t._l1 shl 3 shr 48).toByte()
            out[12] = out[12] or (t._l2 shl 6).toByte()
        }
        if (13 < out.size) {
            out[13] = out[13] or (t._l1 shl 3 shr 56).toByte()
            out[13] = out[13] or (t._l2 shl 6 shr 8).toByte()
        }
        if (14 < out.size) out[14] = out[14] or (t._l2 shl 6 shr 16).toByte()
        if (15 < out.size) out[15] = out[15] or (t._l2 shl 6 shr 24).toByte()
        if (16 < out.size) out[16] = out[16] or (t._l2 shl 6 shr 32).toByte()
        if (17 < out.size) out[17] = out[17] or (t._l2 shl 6 shr 40).toByte()
        if (18 < out.size) out[18] = out[18] or (t._l2 shl 6 shr 48).toByte()
        if (19 < out.size) {
            out[19] = out[19] or (t._l2 shl 6 shr 56).toByte()
            out[19] = out[19] or (t._l3 shl 1).toByte()
        }
        if (20 < out.size) out[20] = out[20] or (t._l3 shl 1 shr 8).toByte()
        if (21 < out.size) out[21] = out[21] or (t._l3 shl 1 shr 16).toByte()
        if (22 < out.size) out[22] = out[22] or (t._l3 shl 1 shr 24).toByte()
        if (23 < out.size) out[23] = out[23] or (t._l3 shl 1 shr 32).toByte()
        if (24 < out.size) out[24] = out[24] or (t._l3 shl 1 shr 40).toByte()
        if (25 < out.size) {
            out[25] = out[25] or (t._l3 shl 1 shr 48).toByte()
            out[25] = out[25] or (t._l4 shl 4).toByte()
        }
        if (26 < out.size) {
            out[26] = out[26] or (t._l3 shl 1 shr 56).toByte()
            out[26] = out[26] or (t._l4 shl 4 shr 8).toByte()
        }
        if (27 < out.size) out[27] = out[27] or (t._l4 shl 4 shr 16).toByte()
        if (28 < out.size) out[28] = out[28] or (t._l4 shl 4 shr 24).toByte()
        if (29 < out.size) out[29] = out[29] or (t._l4 shl 4 shr 32).toByte()
        if (30 < out.size) out[30] = out[30] or (t._l4 shl 4 shr 40).toByte()
        if (31 < out.size) out[31] = out[31] or (t._l4 shl 4 shr 48).toByte()
        if (32 < out.size) out[32] = out[32] or (t._l4 shl 4 shr 56).toByte()

        return out
    }

    // Square sets v = x * x, and returns v.
    fun square(): Element {
        val l0 = this._l0
        val l1 = this._l1
        val l2 = this._l2
        val l3 = this._l3
        val l4 = this._l4
        // Squaring works precisely like multiplication above, but thanks to its
        // symmetry we get to group a few terms together.
        //
        //                          l4   l3   l2   l1   l0  x
        //                          l4   l3   l2   l1   l0  =
        //                         ------------------------
        //                        l4l0 l3l0 l2l0 l1l0 l0l0  +
        //                   l4l1 l3l1 l2l1 l1l1 l0l1       +
        //              l4l2 l3l2 l2l2 l1l2 l0l2            +
        //         l4l3 l3l3 l2l3 l1l3 l0l3                 +
        //    l4l4 l3l4 l2l4 l1l4 l0l4                      =
        //   ----------------------------------------------
        //      r8   r7   r6   r5   r4   r3   r2   r1   r0
        //
        //            l4l0    l3l0    l2l0    l1l0    l0l0  +
        //            l3l1    l2l1    l1l1    l0l1 19×l4l1  +
        //            l2l2    l1l2    l0l2 19×l4l2 19×l3l2  +
        //            l1l3    l0l3 19×l4l3 19×l3l3 19×l2l3  +
        //            l0l4 19×l4l4 19×l3l4 19×l2l4 19×l1l4  =
        //           --------------------------------------
        //              r4      r3      r2      r1      r0
        //
        // With precomputed 2×, 19×, and 2×19× terms, we can compute each limb with
        // only three Mul64 and four Add64, instead of five and eight.
        val l0_2 = l0 * 2
        val l1_2 = l1 * 2
        val l1_38 = l1 * 38
        val l2_38 = l2 * 38
        val l3_38 = l3 * 38
        val l3_19 = l3 * 19
        val l4_19 = l4 * 19
        // r0 = l0×l0 + 19×(l1×l4 + l2×l3 + l3×l2 + l4×l1) = l0×l0 + 19×2×(l1×l4 + l2×l3)
        var r0 = mul64(l0, l0)
        r0 = addMul64(r0, l1_38, l4)
        r0 = addMul64(r0, l2_38, l3)
        // r1 = l0×l1 + l1×l0 + 19×(l2×l4 + l3×l3 + l4×l2) = 2×l0×l1 + 19×2×l2×l4 + 19×l3×l3
        var r1 = mul64(l0_2, l1)
        r1 = addMul64(r1, l2_38, l4)
        r1 = addMul64(r1, l3_19, l3)
        // r2 = l0×l2 + l1×l1 + l2×l0 + 19×(l3×l4 + l4×l3) = 2×l0×l2 + l1×l1 + 19×2×l3×l4
        var r2 = mul64(l0_2, l2)
        r2 = addMul64(r2, l1, l1)
        r2 = addMul64(r2, l3_38, l4)
        // r3 = l0×l3 + l1×l2 + l2×l1 + l3×l0 + 19×l4×l4 = 2×l0×l3 + 2×l1×l2 + 19×l4×l4
        var r3 = mul64(l0_2, l3)
        r3 = addMul64(r3, l1_2, l2)
        r3 = addMul64(r3, l4_19, l4)
        // r4 = l0×l4 + l1×l3 + l2×l2 + l3×l1 + l4×l0 = 2×l0×l4 + 2×l1×l3 + l2×l2
        var r4 = mul64(l0_2, l4)
        r4 = addMul64(r4, l1_2, l3)
        r4 = addMul64(r4, l2, l2)
        val c0 = shiftRightBy51(r0)
        val c1 = shiftRightBy51(r1)
        val c2 = shiftRightBy51(r2)
        val c3 = shiftRightBy51(r3)
        val c4 = shiftRightBy51(r4)
        val rr0 = (r0.lo and MASK_LOW_51_BITS) + c4 * 19
        val rr1 = (r1.lo and MASK_LOW_51_BITS) + c0
        val rr2 = (r2.lo and MASK_LOW_51_BITS) + c1
        val rr3 = (r3.lo and MASK_LOW_51_BITS) + c2
        val rr4 = (r4.lo and MASK_LOW_51_BITS) + c3
        return carryPropagate(rr0, rr1, rr2, rr3, rr4)
    }

    // Mult32 sets v = x * y, and returns v.
    fun mult32(x: Element, y: Int): Element {
        val (hi, lo) = mul51(x._l0, y)
        val (hi1, lo1) = mul51(x._l1, y)
        val (hi2, lo2) = mul51(x._l2, y)
        val (hi3, lo3) = mul51(x._l3, y)
        val (hi4, lo4) = mul51(x._l4, y)
        val l0 = lo + 19 * hi4 // carried over per the reduction identity
        val l1 = lo1 + hi
        val l2 = lo2 + hi1
        val l3 = lo3 + hi2
        val l4 = lo4 + hi3
        // The hi portions are going to be only 32 bits, plus any previous excess,
        // so we can skip the carry propagation.
        return Element(l0, l1, l2, l3, l4)
    }

    override fun hashCode(): Int {
        return (_l0 xor _l1 xor _l2 xor _l3 xor _l4).toInt()
    }

    // Equals returns whether or not the two field values are the same.  Both
    // field values being compared must be normalized for this function to return
    // the correct result.
    override fun equals(other: Any?): Boolean {
        // Xor only sets bits when they are different, so the two field values
        // can only be the same if no bits are set after xoring each word.
        // This is a constant time implementation.
        if (other === this) {
            return true
        }
        if (other !is Element) {
            return super.equals(other)
        }
        return this.bytes().contentEquals(other.bytes())
    }

    override fun toString(): String {
        return Hex.encode(bytes())
    }

    // mul51 returns lo + hi * 2⁵¹ = a * b.
    private fun mul51(a: Long, b: Int): HiLo {
        val (hi1, lo1) = mul64(a, b.toLong() and 0xffffffffL)
        val lo = lo1 and MASK_LOW_51_BITS
        val hi = hi1 shl 13 or (lo1 ushr 51)
        return HiLo(hi, lo)
    }

    companion object {
        private const val MASK_LOW_51_BITS = (1L shl 51) - 1

        // sqrtM1 is 2^((p-1)/4), which squared is equal to -1 by Euler's Criterion.
        val SqrtM1 = Element(1718705420411056L, 234908883556509L, 2233514472574048L, 2117202627021982L, 765476049583133L)
        val Zero = Element(0, 0, 0, 0, 0)
        val One = Element(1, 0, 0, 0, 0)

        // d is a constant in the curve equation.
        val d = Element(Hex.decodeOrThrow("a3785913ca4deb75abd841414d0a700098e879777940c78c73fe6f2bee6c0352"))
        val d2 = d + d

        // Select sets v to a if cond == 1, and to b if cond == 0.
        fun select(a: Element, b: Element, cond: Boolean): Element {
            val m = mask64Bits(cond)
            val l0 = m and a._l0 or (m.inv() and b._l0)
            val l1 = m and a._l1 or (m.inv() and b._l1)
            val l2 = m and a._l2 or (m.inv() and b._l2)
            val l3 = m and a._l3 or (m.inv() and b._l3)
            val l4 = m and a._l4 or (m.inv() and b._l4)
            return Element(l0, l1, l2, l3, l4)
        }

        // Swap swaps v and u if cond == 1 or leaves them unchanged if cond == 0, and returns v.
        fun swap(v: Element, u: Element, cond: Boolean): Tuple2<Element, Element> {
            val m = mask64Bits(cond)
            var t = m and (v._l0 xor u._l0)
            val vl0 = v._l0 xor t
            val ul0 = u._l0 xor t
            t = m and (v._l1 xor u._l1)
            val vl1 = v._l1 xor t
            val ul1 = u._l1 xor t
            t = m and (v._l2 xor u._l2)
            val vl2 = v._l2 xor t
            val ul2 = u._l2 xor t
            t = m and (v._l3 xor u._l3)
            val vl3 = v._l3 xor t
            val ul3 = u._l3 xor t
            t = m and (v._l4 xor u._l4)
            val vl4 = v._l4 xor t
            val ul4 = u._l4 xor t
            return Tuple(Element(vl0, vl1, vl2, vl3, vl4), Element(ul0, ul1, ul2, ul3, ul4))
        }

        // addMul64 returns v + a * b.
        fun addMul64(v: HiLo, a: Long, b: Long): HiLo {
            val (hi1, lo1) = mul64(a, b)
            val (hi2, lo2) = add64(lo1, v.lo, 0)
            val (hi3) = add64(hi1, v.hi, lo2)
            return HiLo(hi3, hi2)
        }

        fun mul64(x: Long, y: Long): HiLo {
            val mask32 = (1L shl 32) - 1
            val x0 = x and mask32
            val x1 = x ushr 32
            val y0 = y and mask32
            val y1 = y ushr 32
            val t = x1 * y0 + ((x0 * y0) ushr 32)
            val w1 = (t and mask32) + (x0 * y1)
            val hi = x1 * y1 + (t ushr 32) + (w1 ushr 32)
            val lo = x * y
            return HiLo(hi, lo)
        }

        // SqrtRatio sets r to the non-negative square root of the ratio of u and v.
        //
        // If u/v is square, SqrtRatio returns r and 1. If u/v is not square, SqrtRatio
        // sets r according to Section 4.3 of draft-irtf-cfrg-ristretto255-decaf448-00,
        // and returns r and 0.
        fun sqrtRatio(u: Element, v: Element): SqrtRatioResult {
            // r = (u * v3) * (u * v7)^((p-5)/8)
            val v2 = v.square()
            val uv3 = u * v2 * v
            val uv7 = uv3 * v2.square()
            val r = uv3 * pow22523(uv7)
            val check = v * r.square() // check = v * r^2

            val correctSignSqrt = (check == u)
            val flippedSignSqrt = (check == -u)
            val flippedSignSqrtI = (check == -u * SqrtM1)
            val rPrime = r * SqrtM1 // r_prime = SQRT_M1 * r
            // r = CT_SELECT(r_prime IF flipped_sign_sqrt | flipped_sign_sqrt_i ELSE r)
            var q = select(rPrime, r, flippedSignSqrt || flippedSignSqrtI)
            q = absolute(q) // Choose the nonnegative square root.
            return SqrtRatioResult(q, correctSignSqrt || flippedSignSqrt)
        }

        // mask64Bits returns 0xffffffff if cond is 1, and 0 otherwise.
        private fun mask64Bits(cond: Boolean): Long {
            return if (cond) -1 else 0
        }

        // Add64 returns the sum with carry of x, y and carry: sum = x + y + carry.
        // The carry input must be 0 or 1; otherwise the behavior is undefined.
        // The carryOut output is guaranteed to be 0 or 1.
        //
        // This function's execution time does not depend on the inputs.
        private fun add64(x: Long, y: Long, carry: Long): HiLo {
            val sum = x + y + carry
            // The sum will overflow if both top bits are set (x & y) or if one of them
            // is (x | y), and a carry from the lower place happened. If such a carry
            // happens, the top bit will be 1 + 0 + 1 = 0 (&^ sum).
            val carryOut = x and y or (x or y and sum.inv()) ushr 63
            return HiLo(sum, carryOut)
        }

        private fun uint64(b: ByteArray, off: Int): Long {
            return b[off + 0].toLong() and 0xffL or
                (b[off + 1].toLong() and 0xffL shl 8) or
                (b[off + 2].toLong() and 0xffL shl 16) or
                (b[off + 3].toLong() and 0xffL shl 24) or
                (b[off + 4].toLong() and 0xffL shl 32) or
                (b[off + 5].toLong() and 0xffL shl 40) or
                (b[off + 6].toLong() and 0xffL shl 48) or
                (b[off + 7].toLong() and 0xffL shl 56)
        }

        private fun feMul(a: Element, b: Element): Element {
            val a0 = a._l0
            val a1 = a._l1
            val a2 = a._l2
            val a3 = a._l3
            val a4 = a._l4
            val b0 = b._l0
            val b1 = b._l1
            val b2 = b._l2
            val b3 = b._l3
            val b4 = b._l4

            // Limb multiplication works like pen-and-paper columnar multiplication, but
            // with 51-bit limbs instead of digits.
            //
            //                          a4   a3   a2   a1   a0  x
            //                          b4   b3   b2   b1   b0  =
            //                         ------------------------
            //                        a4b0 a3b0 a2b0 a1b0 a0b0  +
            //                   a4b1 a3b1 a2b1 a1b1 a0b1       +
            //              a4b2 a3b2 a2b2 a1b2 a0b2            +
            //         a4b3 a3b3 a2b3 a1b3 a0b3                 +
            //    a4b4 a3b4 a2b4 a1b4 a0b4                      =
            //   ----------------------------------------------
            //      r8   r7   r6   r5   r4   r3   r2   r1   r0
            //
            // We can then use the reduction identity (a * 2²⁵⁵ + b = a * 19 + b) to
            // reduce the limbs that would overflow 255 bits. r5 * 2²⁵⁵ becomes 19 * r5,
            // r6 * 2³⁰⁶ becomes 19 * r6 * 2⁵¹, etc.
            //
            // Reduction can be carried out simultaneously to multiplication. For
            // example, we do not compute r5: whenever the result of a multiplication
            // belongs to r5, like a1b4, we multiply it by 19 and add the result to r0.
            //
            //            a4b0    a3b0    a2b0    a1b0    a0b0  +
            //            a3b1    a2b1    a1b1    a0b1 19×a4b1  +
            //            a2b2    a1b2    a0b2 19×a4b2 19×a3b2  +
            //            a1b3    a0b3 19×a4b3 19×a3b3 19×a2b3  +
            //            a0b4 19×a4b4 19×a3b4 19×a2b4 19×a1b4  =
            //           --------------------------------------
            //              r4      r3      r2      r1      r0
            //
            // Finally we add up the columns into wide, overlapping limbs.
            val a1_19 = a1 * 19
            val a2_19 = a2 * 19
            val a3_19 = a3 * 19
            val a4_19 = a4 * 19

            // r0 = a0×b0 + 19×(a1×b4 + a2×b3 + a3×b2 + a4×b1)
            var r0 = mul64(a0, b0)
            r0 = addMul64(r0, a1_19, b4)
            r0 = addMul64(r0, a2_19, b3)
            r0 = addMul64(r0, a3_19, b2)
            r0 = addMul64(r0, a4_19, b1)

            // r1 = a0×b1 + a1×b0 + 19×(a2×b4 + a3×b3 + a4×b2)
            var r1 = mul64(a0, b1)
            r1 = addMul64(r1, a1, b0)
            r1 = addMul64(r1, a2_19, b4)
            r1 = addMul64(r1, a3_19, b3)
            r1 = addMul64(r1, a4_19, b2)

            // r2 = a0×b2 + a1×b1 + a2×b0 + 19×(a3×b4 + a4×b3)
            var r2 = mul64(a0, b2)
            r2 = addMul64(r2, a1, b1)
            r2 = addMul64(r2, a2, b0)
            r2 = addMul64(r2, a3_19, b4)
            r2 = addMul64(r2, a4_19, b3)

            // r3 = a0×b3 + a1×b2 + a2×b1 + a3×b0 + 19×a4×b4
            var r3 = mul64(a0, b3)
            r3 = addMul64(r3, a1, b2)
            r3 = addMul64(r3, a2, b1)
            r3 = addMul64(r3, a3, b0)
            r3 = addMul64(r3, a4_19, b4)

            // r4 = a0×b4 + a1×b3 + a2×b2 + a3×b1 + a4×b0
            var r4 = mul64(a0, b4)
            r4 = addMul64(r4, a1, b3)
            r4 = addMul64(r4, a2, b2)
            r4 = addMul64(r4, a3, b1)
            r4 = addMul64(r4, a4, b0)

            // After the multiplication, we need to reduce (carry) the five coefficients
            // to obtain a result with limbs that are at most slightly larger than 2⁵¹,
            // to respect the Element invariant.
            //
            // Overall, the reduction works the same as carryPropagate, except with
            // wider inputs: we take the carry for each coefficient by shifting it right
            // by 51, and add it to the limb above it. The top carry is multiplied by 19
            // according to the reduction identity and added to the lowest limb.
            //
            // The largest coefficient (r0) will be at most 111 bits, which guarantees
            // that all carries are at most 111 - 51 = 60 bits, which fits in a uint64.
            //
            //     r0 = a0×b0 + 19×(a1×b4 + a2×b3 + a3×b2 + a4×b1)
            //     r0 < 2⁵²×2⁵² + 19×(2⁵²×2⁵² + 2⁵²×2⁵² + 2⁵²×2⁵² + 2⁵²×2⁵²)
            //     r0 < (1 + 19 × 4) × 2⁵² × 2⁵²
            //     r0 < 2⁷ × 2⁵² × 2⁵²
            //     r0 < 2¹¹¹
            //
            // Moreover, the top coefficient (r4) is at most 107 bits, so c4 is at most
            // 56 bits, and c4 * 19 is at most 61 bits, which again fits in a uint64 and
            // allows us to easily apply the reduction identity.
            //
            //     r4 = a0×b4 + a1×b3 + a2×b2 + a3×b1 + a4×b0
            //     r4 < 5 × 2⁵² × 2⁵²
            //     r4 < 2¹⁰⁷
            //
            val c0 = shiftRightBy51(r0)
            val c1 = shiftRightBy51(r1)
            val c2 = shiftRightBy51(r2)
            val c3 = shiftRightBy51(r3)
            val c4 = shiftRightBy51(r4)
            val rr0 = (r0.lo and MASK_LOW_51_BITS) + c4 * 19
            val rr1 = (r1.lo and MASK_LOW_51_BITS) + c0
            val rr2 = (r2.lo and MASK_LOW_51_BITS) + c1
            val rr3 = (r3.lo and MASK_LOW_51_BITS) + c2
            val rr4 = (r4.lo and MASK_LOW_51_BITS) + c3

            // Now all coefficients fit into 64-bit registers but are still too large to
            // be passed around as a Element. We therefore do one last carry chain,
            // where the carries will be small enough to fit in the wiggle room above 2⁵¹.
            return carryPropagate(rr0, rr1, rr2, rr3, rr4)
        }

        // shiftRightBy51 returns a >> 51. a is assumed to be at most 115 bits.
        private fun shiftRightBy51(a: HiLo): Long {
            return a.hi shl 64 - 51 or (a.lo ushr 51)
        }

        // Pow22523 set v = x^((p-5)/8), and returns v. (p-5)/8 is 2^252-3.
        private fun pow22523(x: Element): Element {
            var t0 = x.square() // x^2
            var t1 = t0.square() // x^4
            t1 = t1.square() // x^8
            t1 = x * t1 // x^9
            t0 *= t1 // x^11
            t0 = t0.square() // x^22
            t0 = t1 * t0 // x^31
            t1 = t0.square() // x^62
            for (i in 1..4) { // x^992
                t1 = t1.square()
            }
            t0 = t1 * t0 // x^1023 -> 1023 = 2^10 - 1
            t1 = t0.square() // 2^11 - 2
            for (i in 1..9) { // 2^20 - 2^10
                t1 = t1.square()
            }
            t1 *= t0 // 2^20 - 1
            var t2 = t1.square() // 2^21 - 2
            for (i in 1..19) { // 2^40 - 2^20
                t2 = t2.square()
            }
            t1 = t2 * t1 // 2^40 - 1
            t1 = t1.square() // 2^41 - 2
            for (i in 1..9) { // 2^50 - 2^10
                t1 = t1.square()
            }
            t0 = t1 * t0 // 2^50 - 1
            t1 = t0.square() // 2^51 - 2
            for (i in 1..49) { // 2^100 - 2^50
                t1 = t1.square()
            }
            t1 *= t0 // 2^100 - 1
            t2 = t1.square() // 2^101 - 2
            for (i in 1..99) { // 2^200 - 2^100
                t2 = t2.square()
            }
            t1 = t2 * t1 // 2^200 - 1
            t1 = t1.square() // 2^201 - 2
            for (i in 1..49) { // 2^250 - 2^50
                t1 = t1.square()
            }
            t0 = t1 * t0 // 2^250 - 1
            t0 = t0.square() // 2^251 - 2
            t0 = t0.square() // 2^252 - 4
            return t0 * x // 2^252 - 3 -> x^(2^252-3)
        }

        // Absolute sets v to |u|, and returns v.
        private fun absolute(u: Element): Element {
            return select(-u, u, u.isNegative)
        }

        private fun carryPropagate(l0: Long, l1: Long, l2: Long, l3: Long, l4: Long): Element {
            val c0 = l0 ushr 51
            val c1 = l1 ushr 51
            val c2 = l2 ushr 51
            val c3 = l3 ushr 51
            val c4 = l4 ushr 51
            return Element(
                (l0 and MASK_LOW_51_BITS) + c4 * 19,
                (l1 and MASK_LOW_51_BITS) + c0,
                (l2 and MASK_LOW_51_BITS) + c1,
                (l3 and MASK_LOW_51_BITS) + c2,
                (l4 and MASK_LOW_51_BITS) + c3,
            )
        }

        // Len64 returns the minimum number of bits required to represent x; the result is 0 for x == 0.
        private fun len64(x: Long): Int {
            var i = x
            var n = 0
            if (i >= 1L shl 32) {
                i = i shr 32
                n = 32
            }
            if (i >= 1 shl 16) {
                i = i shr 16
                n += 16
            }
            if (i >= 1 shl 8) {
                i = i shr 8
                n += 8
            }
            return n + len8tab[i.toInt()]
        }

        private var len8tab = Hex.decodeOrThrow(
            "00010202030303030404040404040404050505050505050505050505050505050606060606060606060606060606060606060606060606060606060606060606" +
                "07070707070707070707070707070707070707070707070707070707070707070707070707070707070707070707070707070707070707070707070707070707" +
                "08080808080808080808080808080808080808080808080808080808080808080808080808080808080808080808080808080808080808080808080808080808" +
                "08080808080808080808080808080808080808080808080808080808080808080808080808080808080808080808080808080808080808080808080808080808",
        )
    }
}
