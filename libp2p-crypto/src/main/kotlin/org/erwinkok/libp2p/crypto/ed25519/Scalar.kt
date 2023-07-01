// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ed25519

import org.erwinkok.util.Hex
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.and
import kotlin.experimental.or

// A Scalar is an integer modulo
//
//     l = 2^252 + 27742317777372353535851937790883648493
//
// which is the prime order of the edwards25519 group.
//
// This type works similarly to math/big.Int, and all arguments and
// receivers are allowed to alias.
//
// The zero value is a valid zero element.
class Scalar(src: ByteArray) {
    private val _n = ByteArray(32)

    init {
        if (src.size != 32) {
            throw NumberFormatException("Scalar must be 32 bytes!")
        }
        System.arraycopy(src, 0, _n, 0, 32)
    }

    val n: ByteArray
        get() = _n

    // isReduced returns whether the given scalar is reduced modulo l.
    val isReduced: Boolean
        get() {
            for (i in _n.indices.reversed()) {
                if (_n[i].toUByte() > MinusOne._n[i].toUByte()) {
                    return false
                } else if (_n[i].toUByte() < MinusOne._n[i].toUByte()) {
                    return true
                }
            }
            return true
        }

    // MultiplyAdd sets s = x * y + z mod l, and returns s.
    fun multiplyAdd(y: Scalar, z: Scalar): Scalar {
        return scMulAdd(this._n, y._n, z._n)
    }

    // Add sets s = x + y mod l, and returns s.
    operator fun plus(y: Scalar): Scalar {
        // s = 1 * x + y mod l
        return scMulAdd(One._n, this._n, y._n)
    }

    // Subtract sets s = x - y mod l, and returns s.
    operator fun minus(y: Scalar): Scalar {
        // s = -1 * y + x mod l
        return scMulAdd(MinusOne._n, y._n, this._n)
    }

    // Negate sets s = -x mod l, and returns s.
    operator fun unaryMinus(): Scalar {
        // s = -1 * x + 0 mod l
        return scMulAdd(MinusOne._n, this._n, Zero._n)
    }

    // Multiply sets s = x * y mod l, and returns s.
    operator fun times(y: Scalar): Scalar {
        // s = x * y + 0 mod l
        return scMulAdd(this._n, y._n, Zero._n)
    }

    // Bytes returns the canonical 32-byte little-endian encoding of s.
    fun bytes(): ByteArray {
        val buf = ByteArray(32)
        System.arraycopy(_n, 0, buf, 0, 32)
        return buf
    }

    override fun hashCode(): Int {
        return _n.contentHashCode()
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
        if (other !is Scalar) {
            return super.equals(other)
        }
        return _n.contentEquals(other._n)
    }

    override fun toString(): String {
        return Hex.encode(_n)
    }

    // nonAdjacentForm computes a width-w non-adjacent form for this scalar.
    //
    // w must be between 2 and 8, or nonAdjacentForm will panic.
    fun nonAdjacentForm(w: Int): ByteArray {
        // This implementation is adapted from the one
        // in curve25519-dalek and is documented there:
        // https://github.com/dalek-cryptography/curve25519-dalek/blob/f630041af28e9a405255f98a8a93adca18e4315b/src/scalar.rs#L800-L871
        if (w < 2) {
            throw NumberFormatException("w must be at least 2 by the definition of NAF")
        } else if (w > 8) {
            throw NumberFormatException("NAF digits must fit in int8")
        }
        val naf = ByteArray(256)
        val digits = LongArray(5)
        val buffer = ByteBuffer.wrap(_n)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0..3) {
            digits[i] = buffer.long
        }
        val width = (1 shl w).toLong()
        val windowMask = width - 1
        var pos = 0
        var carry: Long = 0
        while (pos < 256) {
            val indexU64 = pos / 64
            val indexBit = pos % 64
            val bitBuf: Long = if (indexBit < 64 - w) {
                // This window's bits are contained in a single u64
                digits[indexU64] ushr indexBit
            } else {
                // Combine the current 64 bits with bits from the next 64
                digits[indexU64] ushr indexBit or (digits[1 + indexU64] shl 64 - indexBit)
            }

            // Add carry into the current window
            val window = carry + (bitBuf and windowMask)
            if (window and 1 == 0L) {
                // If the window value is even, preserve the carry and continue.
                // Why is the carry preserved?
                // If carry == 0 and window & 1 == 0,
                //    then the next carry should be 0
                // If carry == 1 and window & 1 == 0,
                //    then bit_buf & 1 == 1 so the next carry should be 1
                pos += 1
                continue
            }
            if (window < width / 2) {
                carry = 0
                naf[pos] = window.toByte()
            } else {
                carry = 1
                naf[pos] = (window.toByte() - width.toByte()).toByte()
            }
            pos += w
        }
        return naf
    }

    fun signedRadix16(): ByteArray {
        val digits = ByteArray(64)

        // Compute unsigned radix-16 digits:
        for (i in 0..31) {
            digits[2 * i] = _n[i] and 15
            digits[2 * i + 1] = (_n[i].toInt() ushr 4 and 15).toByte()
        }

        // Recenter coefficients:
        for (i in 0..62) {
            val carry = digits[i] + 8 ushr 4
            digits[i] = (digits[i] - (carry shl 4).toByte()).toByte()
            digits[i + 1] = (digits[i + 1] + carry.toByte()).toByte()
        }
        return digits
    }

    companion object {
        val Zero = Scalar(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
        val One = Scalar(byteArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
        val MinusOne = Scalar(Hex.decodeOrThrow("ecd3f55c1a631258d69cf7a2def9de1400000000000000000000000000000010"))

        // SetCanonicalBytes sets s = x, where x is a 32-byte little-endian encoding of
        // s, and returns s. If x is not a canonical encoding of s, SetCanonicalBytes
        // returns nil and an error, and the receiver is unchanged.
        fun setCanonicalBytes(x: ByteArray): Scalar {
            if (x.size != 32) {
                throw NumberFormatException("invalid scalar length")
            }
            val b = ByteArray(32)
            System.arraycopy(x, 0, b, 0, 32)
            val s = Scalar(b)
            if (!s.isReduced) {
                throw NumberFormatException("invalid scalar encoding")
            }
            return s
        }

        // SetUniformBytes sets s to an uniformly distributed value given 64 uniformly
        // distributed random bytes.
        fun setUniformBytes(x: ByteArray): Scalar {
            if (x.size != 64) {
                throw NumberFormatException("edwards25519: invalid SetUniformBytes input length")
            }
            val wideBytes = ByteArray(64)
            System.arraycopy(x, 0, wideBytes, 0, 64)
            return scReduce(wideBytes)
        }

        // SetBytesWithClamping applies the buffer pruning described in RFC 8032,
        // Section 5.1.5 (also known as clamping) and sets s to the result. The input
        // must be 32 bytes, and it is not modified.
        //
        // Note that since Scalar values are always reduced modulo the prime order of
        // the curve, the resulting value will not preserve any of the cofactor-clearing
        // properties that clamping is meant to provide. It will however work as
        // expected as long as it is applied to points on the prime order subgroup, like
        // in Ed25519. In fact, it is lost to history why RFC 8032 adopted the
        // irrelevant RFC 7748 clamping, but it is now required for compatibility.
        fun setBytesWithClamping(x: ByteArray): Scalar {
            // The description above omits the purpose of the high bits of the clamping
            // for brevity, but those are also lost to reductions, and are also
            // irrelevant to edwards25519 as they protect against a specific
            // implementation bug that was once observed in a generic Montgomery ladder.
            if (x.size != 32) {
                throw NumberFormatException("edwards25519: invalid SetBytesWithClamping input length")
            }
            val wideBytes = ByteArray(64)
            System.arraycopy(x, 0, wideBytes, 0, 32)
            wideBytes[0] = (wideBytes[0].toInt() and 248).toByte()
            wideBytes[31] = wideBytes[31] and 63
            wideBytes[31] = wideBytes[31] or 64
            return scReduce(wideBytes)
        }

        // Input:
        //   a[0]+256*a[1]+...+256^31*a[31] = a
        //   b[0]+256*b[1]+...+256^31*b[31] = b
        //   c[0]+256*c[1]+...+256^31*c[31] = c
        //
        // Output:
        //   s[0]+256*s[1]+...+256^31*s[31] = (ab+c) mod l
        //   where l = 2^252 + 27742317777372353535851937790883648493.
        private fun scMulAdd(a: ByteArray, b: ByteArray, c: ByteArray): Scalar {
            val a0 = (2097151L and load3(a, 0))
            val a1 = (2097151L and (load4(a, 2) shr 5))
            val a2 = (2097151L and (load3(a, 5) shr 2))
            val a3 = (2097151L and (load4(a, 7) shr 7))
            val a4 = (2097151L and (load4(a, 10) shr 4))
            val a5 = (2097151L and (load3(a, 13) shr 1))
            val a6 = (2097151L and (load4(a, 15) shr 6))
            val a7 = (2097151L and (load3(a, 18) shr 3))
            val a8 = (2097151L and load3(a, 21))
            val a9 = (2097151L and (load4(a, 23) shr 5))
            val a10 = (2097151L and (load3(a, 26) shr 2))
            val a11 = load4(a, 28) shr 7
            val b0 = (2097151L and load3(b, 0))
            val b1 = (2097151L and (load4(b, 2) shr 5))
            val b2 = (2097151L and (load3(b, 5) shr 2))
            val b3 = (2097151L and (load4(b, 7) shr 7))
            val b4 = (2097151L and (load4(b, 10) shr 4))
            val b5 = (2097151L and (load3(b, 13) shr 1))
            val b6 = (2097151L and (load4(b, 15) shr 6))
            val b7 = (2097151L and (load3(b, 18) shr 3))
            val b8 = (2097151L and load3(b, 21))
            val b9 = (2097151L and (load4(b, 23) shr 5))
            val b10 = (2097151L and (load3(b, 26) shr 2))
            val b11 = load4(b, 28) shr 7
            val c0 = (2097151L and load3(c, 0))
            val c1 = (2097151L and (load4(c, 2) shr 5))
            val c2 = (2097151L and (load3(c, 5) shr 2))
            val c3 = (2097151L and (load4(c, 7) shr 7))
            val c4 = (2097151L and (load4(c, 10) shr 4))
            val c5 = (2097151L and (load3(c, 13) shr 1))
            val c6 = (2097151L and (load4(c, 15) shr 6))
            val c7 = (2097151L and (load3(c, 18) shr 3))
            val c8 = (2097151L and load3(c, 21))
            val c9 = (2097151L and (load4(c, 23) shr 5))
            val c10 = (2097151L and (load3(c, 26) shr 2))
            val c11 = load4(c, 28) shr 7
            val carry = LongArray(23)
            var s0 = c0 + a0 * b0
            var s1 = c1 + a0 * b1 + a1 * b0
            var s2 = c2 + a0 * b2 + a1 * b1 + a2 * b0
            var s3 = c3 + a0 * b3 + a1 * b2 + a2 * b1 + a3 * b0
            var s4 = c4 + a0 * b4 + a1 * b3 + a2 * b2 + a3 * b1 + a4 * b0
            var s5 = c5 + a0 * b5 + a1 * b4 + a2 * b3 + a3 * b2 + a4 * b1 + a5 * b0
            var s6 = c6 + a0 * b6 + a1 * b5 + a2 * b4 + a3 * b3 + a4 * b2 + a5 * b1 + a6 * b0
            var s7 = c7 + a0 * b7 + a1 * b6 + a2 * b5 + a3 * b4 + a4 * b3 + a5 * b2 + a6 * b1 + a7 * b0
            var s8 = c8 + a0 * b8 + a1 * b7 + a2 * b6 + a3 * b5 + a4 * b4 + a5 * b3 + a6 * b2 + a7 * b1 + a8 * b0
            var s9 = c9 + a0 * b9 + a1 * b8 + a2 * b7 + a3 * b6 + a4 * b5 + a5 * b4 + a6 * b3 + a7 * b2 + a8 * b1 + a9 * b0
            var s10 = c10 + a0 * b10 + a1 * b9 + a2 * b8 + a3 * b7 + a4 * b6 + a5 * b5 + a6 * b4 + a7 * b3 + a8 * b2 + a9 * b1 + a10 * b0
            var s11 = c11 + a0 * b11 + a1 * b10 + a2 * b9 + a3 * b8 + a4 * b7 + a5 * b6 + a6 * b5 + a7 * b4 + a8 * b3 + a9 * b2 + a10 * b1 + a11 * b0
            var s12 = a1 * b11 + a2 * b10 + a3 * b9 + a4 * b8 + a5 * b7 + a6 * b6 + a7 * b5 + a8 * b4 + a9 * b3 + a10 * b2 + a11 * b1
            var s13 = a2 * b11 + a3 * b10 + a4 * b9 + a5 * b8 + a6 * b7 + a7 * b6 + a8 * b5 + a9 * b4 + a10 * b3 + a11 * b2
            var s14 = a3 * b11 + a4 * b10 + a5 * b9 + a6 * b8 + a7 * b7 + a8 * b6 + a9 * b5 + a10 * b4 + a11 * b3
            var s15 = a4 * b11 + a5 * b10 + a6 * b9 + a7 * b8 + a8 * b7 + a9 * b6 + a10 * b5 + a11 * b4
            var s16 = a5 * b11 + a6 * b10 + a7 * b9 + a8 * b8 + a9 * b7 + a10 * b6 + a11 * b5
            var s17 = a6 * b11 + a7 * b10 + a8 * b9 + a9 * b8 + a10 * b7 + a11 * b6
            var s18 = a7 * b11 + a8 * b10 + a9 * b9 + a10 * b8 + a11 * b7
            var s19 = a8 * b11 + a9 * b10 + a10 * b9 + a11 * b8
            var s20 = a9 * b11 + a10 * b10 + a11 * b9
            var s21 = a10 * b11 + a11 * b10
            var s22 = a11 * b11
            var s23: Long = 0
            carry[0] = s0 + (1 shl 20) shr 21
            s1 += carry[0]
            s0 -= carry[0] shl 21
            carry[2] = s2 + (1 shl 20) shr 21
            s3 += carry[2]
            s2 -= carry[2] shl 21
            carry[4] = s4 + (1 shl 20) shr 21
            s5 += carry[4]
            s4 -= carry[4] shl 21
            carry[6] = s6 + (1 shl 20) shr 21
            s7 += carry[6]
            s6 -= carry[6] shl 21
            carry[8] = s8 + (1 shl 20) shr 21
            s9 += carry[8]
            s8 -= carry[8] shl 21
            carry[10] = s10 + (1 shl 20) shr 21
            s11 += carry[10]
            s10 -= carry[10] shl 21
            carry[12] = s12 + (1 shl 20) shr 21
            s13 += carry[12]
            s12 -= carry[12] shl 21
            carry[14] = s14 + (1 shl 20) shr 21
            s15 += carry[14]
            s14 -= carry[14] shl 21
            carry[16] = s16 + (1 shl 20) shr 21
            s17 += carry[16]
            s16 -= carry[16] shl 21
            carry[18] = s18 + (1 shl 20) shr 21
            s19 += carry[18]
            s18 -= carry[18] shl 21
            carry[20] = s20 + (1 shl 20) shr 21
            s21 += carry[20]
            s20 -= carry[20] shl 21
            carry[22] = s22 + (1 shl 20) shr 21
            s23 += carry[22]
            s22 -= carry[22] shl 21
            carry[1] = s1 + (1 shl 20) shr 21
            s2 += carry[1]
            s1 -= carry[1] shl 21
            carry[3] = s3 + (1 shl 20) shr 21
            s4 += carry[3]
            s3 -= carry[3] shl 21
            carry[5] = s5 + (1 shl 20) shr 21
            s6 += carry[5]
            s5 -= carry[5] shl 21
            carry[7] = s7 + (1 shl 20) shr 21
            s8 += carry[7]
            s7 -= carry[7] shl 21
            carry[9] = s9 + (1 shl 20) shr 21
            s10 += carry[9]
            s9 -= carry[9] shl 21
            carry[11] = s11 + (1 shl 20) shr 21
            s12 += carry[11]
            s11 -= carry[11] shl 21
            carry[13] = s13 + (1 shl 20) shr 21
            s14 += carry[13]
            s13 -= carry[13] shl 21
            carry[15] = s15 + (1 shl 20) shr 21
            s16 += carry[15]
            s15 -= carry[15] shl 21
            carry[17] = s17 + (1 shl 20) shr 21
            s18 += carry[17]
            s17 -= carry[17] shl 21
            carry[19] = s19 + (1 shl 20) shr 21
            s20 += carry[19]
            s19 -= carry[19] shl 21
            carry[21] = s21 + (1 shl 20) shr 21
            s22 += carry[21]
            s21 -= carry[21] shl 21
            s11 += s23 * 666643
            s12 += s23 * 470296
            s13 += s23 * 654183
            s14 -= s23 * 997805
            s15 += s23 * 136657
            s16 -= s23 * 683901

            s10 += s22 * 666643
            s11 += s22 * 470296
            s12 += s22 * 654183
            s13 -= s22 * 997805
            s14 += s22 * 136657
            s15 -= s22 * 683901

            s9 += s21 * 666643
            s10 += s21 * 470296
            s11 += s21 * 654183
            s12 -= s21 * 997805
            s13 += s21 * 136657
            s14 -= s21 * 683901

            s8 += s20 * 666643
            s9 += s20 * 470296
            s10 += s20 * 654183
            s11 -= s20 * 997805
            s12 += s20 * 136657
            s13 -= s20 * 683901

            s7 += s19 * 666643
            s8 += s19 * 470296
            s9 += s19 * 654183
            s10 -= s19 * 997805
            s11 += s19 * 136657
            s12 -= s19 * 683901

            s6 += s18 * 666643
            s7 += s18 * 470296
            s8 += s18 * 654183
            s9 -= s18 * 997805
            s10 += s18 * 136657
            s11 -= s18 * 683901

            carry[6] = s6 + (1 shl 20) shr 21
            s7 += carry[6]
            s6 -= carry[6] shl 21
            carry[8] = s8 + (1 shl 20) shr 21
            s9 += carry[8]
            s8 -= carry[8] shl 21
            carry[10] = s10 + (1 shl 20) shr 21
            s11 += carry[10]
            s10 -= carry[10] shl 21
            carry[12] = s12 + (1 shl 20) shr 21
            s13 += carry[12]
            s12 -= carry[12] shl 21
            carry[14] = s14 + (1 shl 20) shr 21
            s15 += carry[14]
            s14 -= carry[14] shl 21
            carry[16] = s16 + (1 shl 20) shr 21
            s17 += carry[16]
            s16 -= carry[16] shl 21
            carry[7] = s7 + (1 shl 20) shr 21
            s8 += carry[7]
            s7 -= carry[7] shl 21
            carry[9] = s9 + (1 shl 20) shr 21
            s10 += carry[9]
            s9 -= carry[9] shl 21
            carry[11] = s11 + (1 shl 20) shr 21
            s12 += carry[11]
            s11 -= carry[11] shl 21
            carry[13] = s13 + (1 shl 20) shr 21
            s14 += carry[13]
            s13 -= carry[13] shl 21
            carry[15] = s15 + (1 shl 20) shr 21
            s16 += carry[15]
            s15 -= carry[15] shl 21
            s5 += s17 * 666643
            s6 += s17 * 470296
            s7 += s17 * 654183
            s8 -= s17 * 997805
            s9 += s17 * 136657
            s10 -= s17 * 683901

            s4 += s16 * 666643
            s5 += s16 * 470296
            s6 += s16 * 654183
            s7 -= s16 * 997805
            s8 += s16 * 136657
            s9 -= s16 * 683901

            s3 += s15 * 666643
            s4 += s15 * 470296
            s5 += s15 * 654183
            s6 -= s15 * 997805
            s7 += s15 * 136657
            s8 -= s15 * 683901

            s2 += s14 * 666643
            s3 += s14 * 470296
            s4 += s14 * 654183
            s5 -= s14 * 997805
            s6 += s14 * 136657
            s7 -= s14 * 683901

            s1 += s13 * 666643
            s2 += s13 * 470296
            s3 += s13 * 654183
            s4 -= s13 * 997805
            s5 += s13 * 136657
            s6 -= s13 * 683901

            s0 += s12 * 666643
            s1 += s12 * 470296
            s2 += s12 * 654183
            s3 -= s12 * 997805
            s4 += s12 * 136657
            s5 -= s12 * 683901
            s12 = 0
            carry[0] = s0 + (1 shl 20) shr 21
            s1 += carry[0]
            s0 -= carry[0] shl 21
            carry[2] = s2 + (1 shl 20) shr 21
            s3 += carry[2]
            s2 -= carry[2] shl 21
            carry[4] = s4 + (1 shl 20) shr 21
            s5 += carry[4]
            s4 -= carry[4] shl 21
            carry[6] = s6 + (1 shl 20) shr 21
            s7 += carry[6]
            s6 -= carry[6] shl 21
            carry[8] = s8 + (1 shl 20) shr 21
            s9 += carry[8]
            s8 -= carry[8] shl 21
            carry[10] = s10 + (1 shl 20) shr 21
            s11 += carry[10]
            s10 -= carry[10] shl 21
            carry[1] = s1 + (1 shl 20) shr 21
            s2 += carry[1]
            s1 -= carry[1] shl 21
            carry[3] = s3 + (1 shl 20) shr 21
            s4 += carry[3]
            s3 -= carry[3] shl 21
            carry[5] = s5 + (1 shl 20) shr 21
            s6 += carry[5]
            s5 -= carry[5] shl 21
            carry[7] = s7 + (1 shl 20) shr 21
            s8 += carry[7]
            s7 -= carry[7] shl 21
            carry[9] = s9 + (1 shl 20) shr 21
            s10 += carry[9]
            s9 -= carry[9] shl 21
            carry[11] = s11 + (1 shl 20) shr 21
            s12 += carry[11]
            s11 -= carry[11] shl 21
            s0 += s12 * 666643
            s1 += s12 * 470296
            s2 += s12 * 654183
            s3 -= s12 * 997805
            s4 += s12 * 136657
            s5 -= s12 * 683901
            s12 = 0
            carry[0] = s0 shr 21
            s1 += carry[0]
            s0 -= carry[0] shl 21
            carry[1] = s1 shr 21
            s2 += carry[1]
            s1 -= carry[1] shl 21
            carry[2] = s2 shr 21
            s3 += carry[2]
            s2 -= carry[2] shl 21
            carry[3] = s3 shr 21
            s4 += carry[3]
            s3 -= carry[3] shl 21
            carry[4] = s4 shr 21
            s5 += carry[4]
            s4 -= carry[4] shl 21
            carry[5] = s5 shr 21
            s6 += carry[5]
            s5 -= carry[5] shl 21
            carry[6] = s6 shr 21
            s7 += carry[6]
            s6 -= carry[6] shl 21
            carry[7] = s7 shr 21
            s8 += carry[7]
            s7 -= carry[7] shl 21
            carry[8] = s8 shr 21
            s9 += carry[8]
            s8 -= carry[8] shl 21
            carry[9] = s9 shr 21
            s10 += carry[9]
            s9 -= carry[9] shl 21
            carry[10] = s10 shr 21
            s11 += carry[10]
            s10 -= carry[10] shl 21
            carry[11] = s11 shr 21
            s12 += carry[11]
            s11 -= carry[11] shl 21
            s0 += s12 * 666643
            s1 += s12 * 470296
            s2 += s12 * 654183
            s3 -= s12 * 997805
            s4 += s12 * 136657
            s5 -= s12 * 683901

            carry[0] = s0 shr 21
            s1 += carry[0]
            s0 -= carry[0] shl 21
            carry[1] = s1 shr 21
            s2 += carry[1]
            s1 -= carry[1] shl 21
            carry[2] = s2 shr 21
            s3 += carry[2]
            s2 -= carry[2] shl 21
            carry[3] = s3 shr 21
            s4 += carry[3]
            s3 -= carry[3] shl 21
            carry[4] = s4 shr 21
            s5 += carry[4]
            s4 -= carry[4] shl 21
            carry[5] = s5 shr 21
            s6 += carry[5]
            s5 -= carry[5] shl 21
            carry[6] = s6 shr 21
            s7 += carry[6]
            s6 -= carry[6] shl 21
            carry[7] = s7 shr 21
            s8 += carry[7]
            s7 -= carry[7] shl 21
            carry[8] = s8 shr 21
            s9 += carry[8]
            s8 -= carry[8] shl 21
            carry[9] = s9 shr 21
            s10 += carry[9]
            s9 -= carry[9] shl 21
            carry[10] = s10 shr 21
            s11 += carry[10]
            s10 -= carry[10] shl 21
            val sn = ByteArray(32)
            sn[0] = (s0 shr 0).toByte()
            sn[1] = (s0 shr 8).toByte()
            sn[2] = (s0 shr 16 or (s1 shl 5)).toByte()
            sn[3] = (s1 shr 3).toByte()
            sn[4] = (s1 shr 11).toByte()
            sn[5] = (s1 shr 19 or (s2 shl 2)).toByte()
            sn[6] = (s2 shr 6).toByte()
            sn[7] = (s2 shr 14 or (s3 shl 7)).toByte()
            sn[8] = (s3 shr 1).toByte()
            sn[9] = (s3 shr 9).toByte()
            sn[10] = (s3 shr 17 or (s4 shl 4)).toByte()
            sn[11] = (s4 shr 4).toByte()
            sn[12] = (s4 shr 12).toByte()
            sn[13] = (s4 shr 20 or (s5 shl 1)).toByte()
            sn[14] = (s5 shr 7).toByte()
            sn[15] = (s5 shr 15 or (s6 shl 6)).toByte()
            sn[16] = (s6 shr 2).toByte()
            sn[17] = (s6 shr 10).toByte()
            sn[18] = (s6 shr 18 or (s7 shl 3)).toByte()
            sn[19] = (s7 shr 5).toByte()
            sn[20] = (s7 shr 13).toByte()
            sn[21] = (s8 shr 0).toByte()
            sn[22] = (s8 shr 8).toByte()
            sn[23] = (s8 shr 16 or (s9 shl 5)).toByte()
            sn[24] = (s9 shr 3).toByte()
            sn[25] = (s9 shr 11).toByte()
            sn[26] = (s9 shr 19 or (s10 shl 2)).toByte()
            sn[27] = (s10 shr 6).toByte()
            sn[28] = (s10 shr 14 or (s11 shl 7)).toByte()
            sn[29] = (s11 shr 1).toByte()
            sn[30] = (s11 shr 9).toByte()
            sn[31] = (s11 shr 17).toByte()
            return Scalar(sn)
        }

        // Input:
        //   s[0]+256*s[1]+...+256^63*s[63] = s
        //
        // Output:
        //   s[0]+256*s[1]+...+256^31*s[31] = s mod l
        //   where l = 2^252 + 27742317777372353535851937790883648493.
        private fun scReduce(s: ByteArray): Scalar {
            var s0 = (2097151L and load3(s, 0))
            var s1 = (2097151L and (load4(s, 2) shr 5))
            var s2 = (2097151L and (load3(s, 5) shr 2))
            var s3 = (2097151L and (load4(s, 7) shr 7))
            var s4 = (2097151L and (load4(s, 10) shr 4))
            var s5 = (2097151L and (load3(s, 13) shr 1))
            var s6 = (2097151L and (load4(s, 15) shr 6))
            var s7 = (2097151L and (load3(s, 18) shr 3))
            var s8 = (2097151L and load3(s, 21))
            var s9 = (2097151L and (load4(s, 23) shr 5))
            var s10 = (2097151L and (load3(s, 26) shr 2))
            var s11 = (2097151L and (load4(s, 28) shr 7))
            var s12 = (2097151L and (load4(s, 31) shr 4))
            var s13 = (2097151L and (load3(s, 34) shr 1))
            var s14 = (2097151L and (load4(s, 36) shr 6))
            var s15 = (2097151L and (load3(s, 39) shr 3))
            var s16 = (2097151L and load3(s, 42))
            var s17 = (2097151L and (load4(s, 44) shr 5))
            val s18 = (2097151L and (load3(s, 47) shr 2))
            val s19 = (2097151L and (load4(s, 49) shr 7))
            val s20 = (2097151L and (load4(s, 52) shr 4))
            val s21 = (2097151L and (load3(s, 55) shr 1))
            val s22 = (2097151L and (load4(s, 57) shr 6))
            val s23 = load4(s, 60) shr 3
            s11 += s23 * 666643
            s12 += s23 * 470296
            s13 += s23 * 654183
            s14 -= s23 * 997805
            s15 += s23 * 136657
            s16 -= s23 * 683901

            s10 += s22 * 666643
            s11 += s22 * 470296
            s12 += s22 * 654183
            s13 -= s22 * 997805
            s14 += s22 * 136657
            s15 -= s22 * 683901

            s9 += s21 * 666643
            s10 += s21 * 470296
            s11 += s21 * 654183
            s12 -= s21 * 997805
            s13 += s21 * 136657
            s14 -= s21 * 683901

            s8 += s20 * 666643
            s9 += s20 * 470296
            s10 += s20 * 654183
            s11 -= s20 * 997805
            s12 += s20 * 136657
            s13 -= s20 * 683901

            s7 += s19 * 666643
            s8 += s19 * 470296
            s9 += s19 * 654183
            s10 -= s19 * 997805
            s11 += s19 * 136657
            s12 -= s19 * 683901

            s6 += s18 * 666643
            s7 += s18 * 470296
            s8 += s18 * 654183
            s9 -= s18 * 997805
            s10 += s18 * 136657
            s11 -= s18 * 683901

            val carry = LongArray(17)
            carry[6] = s6 + (1 shl 20) shr 21
            s7 += carry[6]
            s6 -= carry[6] shl 21
            carry[8] = s8 + (1 shl 20) shr 21
            s9 += carry[8]
            s8 -= carry[8] shl 21
            carry[10] = s10 + (1 shl 20) shr 21
            s11 += carry[10]
            s10 -= carry[10] shl 21
            carry[12] = s12 + (1 shl 20) shr 21
            s13 += carry[12]
            s12 -= carry[12] shl 21
            carry[14] = s14 + (1 shl 20) shr 21
            s15 += carry[14]
            s14 -= carry[14] shl 21
            carry[16] = s16 + (1 shl 20) shr 21
            s17 += carry[16]
            s16 -= carry[16] shl 21
            carry[7] = s7 + (1 shl 20) shr 21
            s8 += carry[7]
            s7 -= carry[7] shl 21
            carry[9] = s9 + (1 shl 20) shr 21
            s10 += carry[9]
            s9 -= carry[9] shl 21
            carry[11] = s11 + (1 shl 20) shr 21
            s12 += carry[11]
            s11 -= carry[11] shl 21
            carry[13] = s13 + (1 shl 20) shr 21
            s14 += carry[13]
            s13 -= carry[13] shl 21
            carry[15] = s15 + (1 shl 20) shr 21
            s16 += carry[15]
            s15 -= carry[15] shl 21
            s5 += s17 * 666643
            s6 += s17 * 470296
            s7 += s17 * 654183
            s8 -= s17 * 997805
            s9 += s17 * 136657
            s10 -= s17 * 683901

            s4 += s16 * 666643
            s5 += s16 * 470296
            s6 += s16 * 654183
            s7 -= s16 * 997805
            s8 += s16 * 136657
            s9 -= s16 * 683901

            s3 += s15 * 666643
            s4 += s15 * 470296
            s5 += s15 * 654183
            s6 -= s15 * 997805
            s7 += s15 * 136657
            s8 -= s15 * 683901

            s2 += s14 * 666643
            s3 += s14 * 470296
            s4 += s14 * 654183
            s5 -= s14 * 997805
            s6 += s14 * 136657
            s7 -= s14 * 683901

            s1 += s13 * 666643
            s2 += s13 * 470296
            s3 += s13 * 654183
            s4 -= s13 * 997805
            s5 += s13 * 136657
            s6 -= s13 * 683901

            s0 += s12 * 666643
            s1 += s12 * 470296
            s2 += s12 * 654183
            s3 -= s12 * 997805
            s4 += s12 * 136657
            s5 -= s12 * 683901
            s12 = 0
            carry[0] = s0 + (1 shl 20) shr 21
            s1 += carry[0]
            s0 -= carry[0] shl 21
            carry[2] = s2 + (1 shl 20) shr 21
            s3 += carry[2]
            s2 -= carry[2] shl 21
            carry[4] = s4 + (1 shl 20) shr 21
            s5 += carry[4]
            s4 -= carry[4] shl 21
            carry[6] = s6 + (1 shl 20) shr 21
            s7 += carry[6]
            s6 -= carry[6] shl 21
            carry[8] = s8 + (1 shl 20) shr 21
            s9 += carry[8]
            s8 -= carry[8] shl 21
            carry[10] = s10 + (1 shl 20) shr 21
            s11 += carry[10]
            s10 -= carry[10] shl 21
            carry[1] = s1 + (1 shl 20) shr 21
            s2 += carry[1]
            s1 -= carry[1] shl 21
            carry[3] = s3 + (1 shl 20) shr 21
            s4 += carry[3]
            s3 -= carry[3] shl 21
            carry[5] = s5 + (1 shl 20) shr 21
            s6 += carry[5]
            s5 -= carry[5] shl 21
            carry[7] = s7 + (1 shl 20) shr 21
            s8 += carry[7]
            s7 -= carry[7] shl 21
            carry[9] = s9 + (1 shl 20) shr 21
            s10 += carry[9]
            s9 -= carry[9] shl 21
            carry[11] = s11 + (1 shl 20) shr 21
            s12 += carry[11]
            s11 -= carry[11] shl 21
            s0 += s12 * 666643
            s1 += s12 * 470296
            s2 += s12 * 654183
            s3 -= s12 * 997805
            s4 += s12 * 136657
            s5 -= s12 * 683901
            s12 = 0
            carry[0] = s0 shr 21
            s1 += carry[0]
            s0 -= carry[0] shl 21
            carry[1] = s1 shr 21
            s2 += carry[1]
            s1 -= carry[1] shl 21
            carry[2] = s2 shr 21
            s3 += carry[2]
            s2 -= carry[2] shl 21
            carry[3] = s3 shr 21
            s4 += carry[3]
            s3 -= carry[3] shl 21
            carry[4] = s4 shr 21
            s5 += carry[4]
            s4 -= carry[4] shl 21
            carry[5] = s5 shr 21
            s6 += carry[5]
            s5 -= carry[5] shl 21
            carry[6] = s6 shr 21
            s7 += carry[6]
            s6 -= carry[6] shl 21
            carry[7] = s7 shr 21
            s8 += carry[7]
            s7 -= carry[7] shl 21
            carry[8] = s8 shr 21
            s9 += carry[8]
            s8 -= carry[8] shl 21
            carry[9] = s9 shr 21
            s10 += carry[9]
            s9 -= carry[9] shl 21
            carry[10] = s10 shr 21
            s11 += carry[10]
            s10 -= carry[10] shl 21
            carry[11] = s11 shr 21
            s12 += carry[11]
            s11 -= carry[11] shl 21
            s0 += s12 * 666643
            s1 += s12 * 470296
            s2 += s12 * 654183
            s3 -= s12 * 997805
            s4 += s12 * 136657
            s5 -= s12 * 683901

            carry[0] = s0 shr 21
            s1 += carry[0]
            s0 -= carry[0] shl 21
            carry[1] = s1 shr 21
            s2 += carry[1]
            s1 -= carry[1] shl 21
            carry[2] = s2 shr 21
            s3 += carry[2]
            s2 -= carry[2] shl 21
            carry[3] = s3 shr 21
            s4 += carry[3]
            s3 -= carry[3] shl 21
            carry[4] = s4 shr 21
            s5 += carry[4]
            s4 -= carry[4] shl 21
            carry[5] = s5 shr 21
            s6 += carry[5]
            s5 -= carry[5] shl 21
            carry[6] = s6 shr 21
            s7 += carry[6]
            s6 -= carry[6] shl 21
            carry[7] = s7 shr 21
            s8 += carry[7]
            s7 -= carry[7] shl 21
            carry[8] = s8 shr 21
            s9 += carry[8]
            s8 -= carry[8] shl 21
            carry[9] = s9 shr 21
            s10 += carry[9]
            s9 -= carry[9] shl 21
            carry[10] = s10 shr 21
            s11 += carry[10]
            s10 -= carry[10] shl 21
            val sn = ByteArray(32)
            sn[0] = (s0 shr 0).toByte()
            sn[1] = (s0 shr 8).toByte()
            sn[2] = (s0 shr 16 or (s1 shl 5)).toByte()
            sn[3] = (s1 shr 3).toByte()
            sn[4] = (s1 shr 11).toByte()
            sn[5] = (s1 shr 19 or (s2 shl 2)).toByte()
            sn[6] = (s2 shr 6).toByte()
            sn[7] = (s2 shr 14 or (s3 shl 7)).toByte()
            sn[8] = (s3 shr 1).toByte()
            sn[9] = (s3 shr 9).toByte()
            sn[10] = (s3 shr 17 or (s4 shl 4)).toByte()
            sn[11] = (s4 shr 4).toByte()
            sn[12] = (s4 shr 12).toByte()
            sn[13] = (s4 shr 20 or (s5 shl 1)).toByte()
            sn[14] = (s5 shr 7).toByte()
            sn[15] = (s5 shr 15 or (s6 shl 6)).toByte()
            sn[16] = (s6 shr 2).toByte()
            sn[17] = (s6 shr 10).toByte()
            sn[18] = (s6 shr 18 or (s7 shl 3)).toByte()
            sn[19] = (s7 shr 5).toByte()
            sn[20] = (s7 shr 13).toByte()
            sn[21] = (s8 shr 0).toByte()
            sn[22] = (s8 shr 8).toByte()
            sn[23] = (s8 shr 16 or (s9 shl 5)).toByte()
            sn[24] = (s9 shr 3).toByte()
            sn[25] = (s9 shr 11).toByte()
            sn[26] = (s9 shr 19 or (s10 shl 2)).toByte()
            sn[27] = (s10 shr 6).toByte()
            sn[28] = (s10 shr 14 or (s11 shl 7)).toByte()
            sn[29] = (s11 shr 1).toByte()
            sn[30] = (s11 shr 9).toByte()
            sn[31] = (s11 shr 17).toByte()
            return Scalar(sn)
        }

        // scMulAdd and scReduce are ported from the public domain, “ref10”
        // implementation of ed25519 from SUPERCOP.
        private fun load3(input: ByteArray, offset: Int): Long {
            return (input[offset + 0].toLong() and 0xffL) or ((input[offset + 1].toLong() and 0xffL) shl 8) or ((input[offset + 2].toLong() and 0xffL) shl 16)
        }

        private fun load4(input: ByteArray, offset: Int): Long {
            return (input[offset + 0].toLong() and 0xffL) or ((input[offset + 1].toLong() and 0xffL) shl 8) or ((input[offset + 2].toLong() and 0xffL) shl 16) or ((input[offset + 3].toLong() and 0xffL) shl 24)
        }
    }
}
