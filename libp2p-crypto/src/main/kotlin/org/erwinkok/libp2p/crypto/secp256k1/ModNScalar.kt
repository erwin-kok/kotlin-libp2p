// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
@file:OptIn(ExperimentalUnsignedTypes::class)

package org.erwinkok.libp2p.crypto.secp256k1

import org.erwinkok.libp2p.crypto.math.BigInt
import org.erwinkok.libp2p.crypto.math.Subtle.constantTimeEq
import org.erwinkok.libp2p.crypto.math.Subtle.constantTimeGreater
import org.erwinkok.libp2p.crypto.math.Subtle.constantTimeGreaterOrEq
import org.erwinkok.libp2p.crypto.math.Subtle.constantTimeNotEq
import org.erwinkok.util.Hex
import java.math.BigInteger
import kotlin.math.max
import kotlin.math.min

// References:
//   [SECG]: Recommended Elliptic Curve Domain Parameters
//     https://www.secg.org/sec2-v2.pdf
//
//   [HAC]: Handbook of Applied Cryptography Menezes, van Oorschot, Vanstone.
//     http://cacr.uwaterloo.ca/hac/

// Many elliptic curve operations require working with scalars in a finite field
// characterized by the order of the group underlying the secp256k1 curve.
// Given this precision is larger than the biggest available native type,
// obviously some form of bignum math is needed.  This code implements
// specialized fixed-precision field arithmetic rather than relying on an
// arbitrary-precision arithmetic package such as math/big for dealing with the
// math modulo the group order since the size is known.  As a result, rather
// large performance gains are achieved by taking advantage of many
// optimizations not available to arbitrary-precision arithmetic and generic
// modular arithmetic algorithms.
//
// There are various ways to internally represent each element.  For example,
// the most obvious representation would be to use an array of 4 uint64s (64
// bits * 4 = 256 bits).  However, that representation suffers from the fact
// that there is no native Go type large enough to handle the intermediate
// results while adding or multiplying two 64-bit numbers.
//
// Given the above, this implementation represents the field elements as 8
// uint32s with each word (array entry) treated as base 2^32.  This was chosen
// because most systems at the current time are 64-bit (or at least have 64-bit
// registers available for specialized purposes such as MMX) so the intermediate
// results can typically be done using a native register (and using uint64s to
// avoid the need for additional half-word arithmetic)

// ModNScalar implements optimized 256-bit constant-time fixed-precision
// arithmetic over the secp256k1 group order. This means all arithmetic is
// performed modulo:
//
// 	0xfffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141
//
// It only implements the arithmetic needed for elliptic curve operations,
// however, the operations that are not implemented can typically be worked
// around if absolutely needed.  For example, subtraction can be performed by
// adding the negation.
//
// Should it be absolutely necessary, conversion to the standard library
// math/big.Int can be accomplished by using the Bytes method, slicing the
// resulting fixed-size array, and feeding it to big.Int.SetBytes.  However,
// that should typically be avoided when possible as conversion to big.Ints
// requires allocations, is not constant time, and is slower when working modulo
// the group order.
class ModNScalar internal constructor(private val _n0: UInt, private val _n1: UInt, private val _n2: UInt, private val _n3: UInt, private val _n4: UInt, private val _n5: UInt, private val _n6: UInt, private val _n7: UInt) {
    // The scalar is represented as 8 32-bit integers in base 2^32.
    //
    // The following depicts the internal representation:
    // 	 ---------------------------------------------------------
    // 	|       n[7]     |      n[6]      | ... |      n[0]      |
    // 	| 32 bits        | 32 bits        | ... | 32 bits        |
    // 	| Mult: 2^(32*7) | Mult: 2^(32*6) | ... | Mult: 2^(32*0) |
    // 	 ---------------------------------------------------------
    //
    // For example, consider the number 2^87 + 2^42 + 1.  It would be
    // represented as:
    // 	n[0] = 1
    // 	n[1] = 2^10
    // 	n[2] = 2^23
    // 	n[3..7] = 0
    //
    // The full 256-bit value is then calculated by looping i from 7..0 and
    // doing sum(n[i] * 2^(32i)) like so:
    // 	n[7] * 2^(32*7) = 0    * 2^224 = 0
    // 	n[6] * 2^(32*6) = 0    * 2^192 = 0
    // 	...
    // 	n[2] * 2^(32*2) = 2^23 * 2^64  = 2^87
    // 	n[1] * 2^(32*1) = 2^10 * 2^32  = 2^42
    // 	n[0] * 2^(32*0) = 1    * 2^0   = 1
    // 	Sum: 0 + 0 + ... + 2^87 + 2^42 + 1 = 2^87 + 2^42 + 1

    val n: UIntArray
        get() = uintArrayOf(_n0, _n1, _n2, _n3, _n4, _n5, _n6, _n7)

    val isZero: Boolean
        get() {
            val bits = _n0 or _n1 or _n2 or _n3 or _n4 or _n5 or _n6 or _n7
            return bits == 0u
        } // Only odd numbers have the bottom bit set.

    // IsOdd returns whether or not the scalar is an odd number in constant time.
    val isOdd: Boolean
        get() {
            // Only odd numbers have the bottom bit set.
            return (_n0 and 1u) == 1u
        }

    // overflows determines if the current scalar is greater than or equal to the
    // group order in constant time and returns 1 if it is or 0 otherwise.
    val overflows: UInt
        get() {
            // The intuition here is that the scalar is greater than the group order if
            // one of the higher individual words is greater than corresponding word of
            // the group order and all higher words in the scalar are equal to their
            // corresponding word of the group order.  Since this type is modulo the
            // group order, being equal is also an overflow back to 0.
            //
            // Note that the words 5, 6, and 7 are all the max uint32 value, so there is
            // no need to test if those individual words of the scalar exceeds them,
            // hence, only equality is checked for them.
            var highWordsEqual = constantTimeEq(_n7, orderWordSeven)
            highWordsEqual = highWordsEqual and constantTimeEq(_n6, orderWordSix)
            highWordsEqual = highWordsEqual and constantTimeEq(_n5, orderWordFive)
            var overflow = highWordsEqual and constantTimeGreater(_n4, orderWordFour)
            highWordsEqual = highWordsEqual and constantTimeEq(_n4, orderWordFour)
            overflow = overflow or (highWordsEqual and constantTimeGreater(_n3, orderWordThree))
            highWordsEqual = highWordsEqual and constantTimeEq(_n3, orderWordThree)
            overflow = overflow or (highWordsEqual and constantTimeGreater(_n2, orderWordTwo))
            highWordsEqual = highWordsEqual and constantTimeEq(_n2, orderWordTwo)
            overflow = overflow or (highWordsEqual and constantTimeGreater(_n1, orderWordOne))
            highWordsEqual = highWordsEqual and constantTimeEq(_n1, orderWordOne)
            overflow = overflow or (highWordsEqual and constantTimeGreaterOrEq(_n0, orderWordZero))
            return overflow
        }

    // reduce256 reduces the current scalar modulo the group order in accordance
    // with the overflows parameter in constant time.  The overflows parameter
    // specifies whether or not the scalar is known to be greater than the group
    // order and MUST either be 1 in the case it is or 0 in the case it is not for a
    // correct result.
    fun reduce256(overflows: UInt): ModNScalar {
        // Notice that since s < 2^256 < 2N (where N is the group order), the max
        // possible number of reductions required is one.  Therefore, in the case a
        // reduction is needed, it can be performed with a single subtraction of N.
        // Also, recall that subtraction is equivalent to addition by the two's
        // complement while ignoring the carry.
        //
        // When s >= N, the overflows parameter will be 1.  Conversely, it will be 0
        // when s < N.  Thus multiplying by the overflows parameter will either
        // result in 0 or the multiplicand itself.
        //
        // Combining the above along with the fact that s + 0 = s, the following is
        // a constant time implementation that works by either adding 0 or the two's
        // complement of N as needed.
        //
        // The final result will be in the range 0 <= s < N as expected.
        val overflows64 = overflows.toULong()
        var c = (_n0).toULong() + overflows64 * orderComplementWordZero.toULong()
        val n0 = (c and uint32Mask).toUInt()
        c = (c shr 32) + _n1.toULong() + overflows64 * orderComplementWordOne.toULong()
        val n1 = (c and uint32Mask).toUInt()
        c = (c shr 32) + _n2.toULong() + overflows64 * orderComplementWordTwo.toULong()
        val n2 = (c and uint32Mask).toUInt()
        c = (c shr 32) + _n3.toULong() + overflows64 * orderComplementWordThree.toULong()
        val n3 = (c and uint32Mask).toUInt()
        c = (c shr 32) + _n4.toULong() + overflows64 // * 1
        val n4 = (c and uint32Mask).toUInt()
        c = (c shr 32) + _n5.toULong() // + overflows64 * 0
        val n5 = (c and uint32Mask).toUInt()
        c = (c shr 32) + _n6.toULong() // + overflows64 * 0
        val n6 = (c and uint32Mask).toUInt()
        c = (c shr 32) + _n7.toULong() // + overflows64 * 0
        val n7 = (c and uint32Mask).toUInt()
        return ModNScalar(n0, n1, n2, n3, n4, n5, n6, n7)
    }

    // PutBytesUnchecked unpacks the scalar to a 32-byte big-endian value directly
    // into the passed byte slice in constant time.  The target slice must must have
    // at least 32 bytes available or it will panic.
    //
    // There is a similar function, PutBytes, which unpacks the scalar into a
    // 32-byte array directly.  This version is provided since it can be useful to
    // write directly into part of a larger buffer without needing a separate
    // allocation.
    //
    // Preconditions:
    //   - The target slice MUST have at least 32 bytes available
    fun putBytes(b: ByteArray, i: Int) {
        // Unpack the 256 total bits from the 8 uint32 words.  This could be done
        // with a for loop, but benchmarks show this unrolled version is about 2
        // times faster than the variant which uses a loop.
        b[i + 31] = (_n0).toByte()
        b[i + 30] = (_n0 shr 8).toByte()
        b[i + 29] = (_n0 shr 16).toByte()
        b[i + 28] = (_n0 shr 24).toByte()
        b[i + 27] = (_n1).toByte()
        b[i + 26] = (_n1 shr 8).toByte()
        b[i + 25] = (_n1 shr 16).toByte()
        b[i + 24] = (_n1 shr 24).toByte()
        b[i + 23] = (_n2).toByte()
        b[i + 22] = (_n2 shr 8).toByte()
        b[i + 21] = (_n2 shr 16).toByte()
        b[i + 20] = (_n2 shr 24).toByte()
        b[i + 19] = (_n3).toByte()
        b[i + 18] = (_n3 shr 8).toByte()
        b[i + 17] = (_n3 shr 16).toByte()
        b[i + 16] = (_n3 shr 24).toByte()
        b[i + 15] = (_n4).toByte()
        b[i + 14] = (_n4 shr 8).toByte()
        b[i + 13] = (_n4 shr 16).toByte()
        b[i + 12] = (_n4 shr 24).toByte()
        b[i + 11] = (_n5).toByte()
        b[i + 10] = (_n5 shr 8).toByte()
        b[i + 9] = (_n5 shr 16).toByte()
        b[i + 8] = (_n5 shr 24).toByte()
        b[i + 7] = (_n6).toByte()
        b[i + 6] = (_n6 shr 8).toByte()
        b[i + 5] = (_n6 shr 16).toByte()
        b[i + 4] = (_n6 shr 24).toByte()
        b[i + 3] = (_n7).toByte()
        b[i + 2] = (_n7 shr 8).toByte()
        b[i + 1] = (_n7 shr 16).toByte()
        b[i + 0] = (_n7 shr 24).toByte()
    }

    fun bytes(): ByteArray {
        val b32 = ByteArray(32)
        putBytes(b32, 0)
        return b32
    }

    // Add2 adds the passed two scalars together modulo the group order in constant
    // time and stores the result in s.
    //
    // The scalar is returned to support chaining.  This enables syntax like:
    // s3.Add2(s, s2).AddInt(1) so that s3 = s + s2 + 1.
    operator fun plus(val2: ModNScalar): ModNScalar {
        var c = _n0.toULong() + val2._n0.toULong()
        val n0 = (c and uint32Mask).toUInt()
        c = (c shr 32) + _n1.toULong() + val2._n1.toULong()
        val n1 = (c and uint32Mask).toUInt()
        c = (c shr 32) + _n2.toULong() + val2._n2.toULong()
        val n2 = (c and uint32Mask).toUInt()
        c = (c shr 32) + _n3.toULong() + val2._n3.toULong()
        val n3 = (c and uint32Mask).toUInt()
        c = (c shr 32) + _n4.toULong() + val2._n4.toULong()
        val n4 = (c and uint32Mask).toUInt()
        c = (c shr 32) + _n5.toULong() + val2._n5.toULong()
        val n5 = (c and uint32Mask).toUInt()
        c = (c shr 32) + _n6.toULong() + val2._n6.toULong()
        val n6 = (c and uint32Mask).toUInt()
        c = (c shr 32) + _n7.toULong() + val2._n7.toULong()
        val n7 = (c and uint32Mask).toUInt()

        // The result is now 256 bits, but it might still be >= N, so use the
        // existing normal reduce method for 256-bit values.
        val s = ModNScalar(n0, n1, n2, n3, n4, n5, n6, n7)
        return s.reduce256((c shr 32).toUInt() + s.overflows)
    }

    // reduce385 reduces the 385-bit intermediate result in the passed terms modulo
    // the group order in constant time and stores the result in s.
    private fun reduce385(t0: ULong, t1: ULong, t2: ULong, t3: ULong, t4: ULong, t5: ULong, t6: ULong, t7: ULong, t8: ULong, t9: ULong, t10: ULong, t11: ULong, t12: ULong): ModNScalar {
        // At this point, the intermediate result in the passed terms has been
        // reduced to fit within 385 bits, so reduce it again using the same method
        // described in reduce512.  As before, the intermediate result will end up
        // being reduced by another 127 bits to 258 bits, thus 9 32-bit terms are
        // needed for this iteration.  The reduced terms are assigned back to t0
        // through t8.
        //
        // Note that several of the intermediate calculations require adding 64-bit
        // products together which would overflow a uint64, so a 96-bit accumulator
        // is used instead until the value is reduced enough to use native uint64s.

        // Terms for 2^(32*0).
        val acc = Accumulator96.Zero
        acc.add(t0)
        acc.add(t8 * orderComplementWordZero.toULong())
        val xt0 = acc.rsh32()

        // Terms for 2^(32*1).
        acc.add(t1)
        acc.add(t8 * orderComplementWordOne.toULong())
        acc.add(t9 * orderComplementWordZero.toULong())
        val xt1 = acc.rsh32()

        // Terms for 2^(32*2).
        acc.add(t2)
        acc.add(t8 * orderComplementWordTwo.toULong())
        acc.add(t9 * orderComplementWordOne.toULong())
        acc.add(t10 * orderComplementWordZero.toULong())
        val xt2 = acc.rsh32()

        // Terms for 2^(32*3).
        acc.add(t3)
        acc.add(t8 * orderComplementWordThree.toULong())
        acc.add(t9 * orderComplementWordTwo.toULong())
        acc.add(t10 * orderComplementWordOne.toULong())
        acc.add(t11 * orderComplementWordZero.toULong())
        val xt3 = acc.rsh32()

        // Terms for 2^(32*4).
        acc.add(t4)
        acc.add(t8) // * uint64(orderComplementWordFour) // * 1
        acc.add(t9 * orderComplementWordThree.toULong())
        acc.add(t10 * orderComplementWordTwo.toULong())
        acc.add(t11 * orderComplementWordOne.toULong())
        acc.add(t12 * orderComplementWordZero.toULong())
        val xt4 = acc.rsh32()

        // Terms for 2^(32*5).
        acc.add(t5)
        // acc.add(t8 * uint64(orderComplementWordFive)) // 0
        acc.add(t9) // * uint64(orderComplementWordFour) // * 1
        acc.add(t10 * orderComplementWordThree.toULong())
        acc.add(t11 * orderComplementWordTwo.toULong())
        acc.add(t12 * orderComplementWordOne.toULong())
        val xt5 = acc.rsh32()

        // Terms for 2^(32*6).
        acc.add(t6)
        // acc.add(t8 * uint64(orderComplementWordSix)) // 0
        // acc.add(t9 * uint64(orderComplementWordFive)) // 0
        acc.add(t10) // * uint64(orderComplementWordFour) // * 1
        acc.add(t11 * orderComplementWordThree.toULong())
        acc.add(t12 * orderComplementWordTwo.toULong())
        val xt6 = acc.rsh32()

        // Terms for 2^(32*7).
        acc.add(t7)
        // acc.add(t8 * uint64(orderComplementWordSeven)) // 0
        // acc.add(t9 * uint64(orderComplementWordSix)) // 0
        // acc.add(t10 * uint64(orderComplementWordFive)) // 0
        acc.add(t11) // * uint64(orderComplementWordFour) // * 1
        acc.add(t12 * orderComplementWordThree.toULong())
        val xt7 = acc.rsh32()

        // Terms for 2^(32*8).
        // acc.add(t9 * uint64(orderComplementWordSeven)) // 0
        // acc.add(t10 * uint64(orderComplementWordSix)) // 0
        // acc.add(t11 * uint64(orderComplementWordFive)) // 0
        acc.add(t12) // * uint64(orderComplementWordFour) // * 1
        val xt8 = acc.rsh32()

        // NOTE: All of the remaining multiplications for this iteration result in 0
        // as they all involve multiplying by combinations of the fifth, sixth, and
        // seventh words of the two's complement of N, which are 0, so skip them.

        // At this point, the result is reduced to fit within 258 bits, so reduce it
        // again using a slightly modified version of the same method.  The maximum
        // value in t8 is 2 at this point and therefore multiplying it by each word
        // of the two's complement of N and adding it to a 32-bit term will result
        // in a maximum requirement of 33 bits, so it is safe to use native uint64s
        // here for the intermediate term carry propagation.
        //
        // Also, since the maximum value in t8 is 2, this ends up reducing by
        // another 2 bits to 256 bits.
        var c = xt0 + xt8 * orderComplementWordZero.toULong()
        val n0 = (c and uint32Mask).toUInt()
        c = (c shr 32) + xt1 + xt8 * orderComplementWordOne.toULong()
        val n1 = (c and uint32Mask).toUInt()
        c = (c shr 32) + xt2 + xt8 * orderComplementWordTwo.toULong()
        val n2 = (c and uint32Mask).toUInt()
        c = (c shr 32) + xt3 + xt8 * orderComplementWordThree.toULong()
        val n3 = (c and uint32Mask).toUInt()
        c = (c shr 32) + xt4 + xt8 // * uint64(orderComplementWordFour) == * 1
        val n4 = (c and uint32Mask).toUInt()
        c = (c shr 32) + xt5 // + t8*uint64(orderComplementWordFive) == 0
        val n5 = (c and uint32Mask).toUInt()
        c = (c shr 32) + xt6 // + t8*uint64(orderComplementWordSix) == 0
        val n6 = (c and uint32Mask).toUInt()
        c = (c shr 32) + xt7 // + t8*uint64(orderComplementWordSeven) == 0
        val n7 = (c and uint32Mask).toUInt()

        // The result is now 256 bits, but it might still be >= N, so use the
        // existing normal reduce method for 256-bit values.
        val s = ModNScalar(n0, n1, n2, n3, n4, n5, n6, n7)
        return s.reduce256((c shr 32).toUInt() + s.overflows)
    }

    // reduce512 reduces the 512-bit intermediate result in the passed terms modulo
    // the group order down to 385 bits in constant time and stores the result in s.
    private fun reduce512(t0: ULong, t1: ULong, t2: ULong, t3: ULong, t4: ULong, t5: ULong, t6: ULong, t7: ULong, t8: ULong, t9: ULong, t10: ULong, t11: ULong, t12: ULong, t13: ULong, t14: ULong, t15: ULong): ModNScalar {
        // At this point, the intermediate result in the passed terms is grouped
        // into the respective bases.
        //
        // Per [HAC] section 14.3.4: Reduction method of moduli of special form,
        // when the modulus is of the special form m = b^t - c, where log_2(c) < t,
        // highly efficient reduction can be achieved per the provided algorithm.
        //
        // The secp256k1 group order fits this criteria since it is:
        //   2^256 - 432420386565659656852420866394968145599
        //
        // Technically the max possible value here is (N-1)^2 since the two scalars
        // being multiplied are always mod N.  Nevertheless, it is safer to consider
        // it to be (2^256-1)^2 = 2^512 - 2^256 + 1 since it is the product of two
        // 256-bit values.
        //
        // The algorithm is to reduce the result modulo the prime by subtracting
        // multiples of the group order N.  However, in order simplify carry
        // propagation, this adds with the two's complement of N to achieve the same
        // result.
        //
        // Since the two's complement of N has 127 leading zero bits, this will end
        // up reducing the intermediate result from 512 bits to 385 bits, resulting
        // in 13 32-bit terms.  The reduced terms are assigned back to t0 through
        // t12.
        //
        // Note that several of the intermediate calculations require adding 64-bit
        // products together which would overflow a uint64, so a 96-bit accumulator
        // is used instead.

        // Terms for 2^(32*0).
        val acc = Accumulator96.Zero
        acc.add(t0)
        acc.add(t8 * orderComplementWordZero.toULong())
        val xt0 = acc.rsh32()

        // Terms for 2^(32*1).
        acc.add(t1)
        acc.add(t8 * orderComplementWordOne.toULong())
        acc.add(t9 * orderComplementWordZero.toULong())
        val xt1 = acc.rsh32()

        // Terms for 2^(32*2).
        acc.add(t2)
        acc.add(t8 * orderComplementWordTwo.toULong())
        acc.add(t9 * orderComplementWordOne.toULong())
        acc.add(t10 * orderComplementWordZero.toULong())
        val xt2 = acc.rsh32()

        // Terms for 2^(32*3).
        acc.add(t3)
        acc.add(t8 * orderComplementWordThree.toULong())
        acc.add(t9 * orderComplementWordTwo.toULong())
        acc.add(t10 * orderComplementWordOne.toULong())
        acc.add(t11 * orderComplementWordZero.toULong())
        val xt3 = acc.rsh32()

        // Terms for 2^(32*4).
        acc.add(t4)
        acc.add(t8) // * uint64(orderComplementWordFour) // * 1
        acc.add(t9 * orderComplementWordThree.toULong())
        acc.add(t10 * orderComplementWordTwo.toULong())
        acc.add(t11 * orderComplementWordOne.toULong())
        acc.add(t12 * orderComplementWordZero.toULong())
        val xt4 = acc.rsh32()

        // Terms for 2^(32*5).
        acc.add(t5)
        // acc.add(t8 * uint64(orderComplementWordFive)) // 0
        acc.add(t9) // * uint64(orderComplementWordFour) // * 1
        acc.add(t10 * orderComplementWordThree.toULong())
        acc.add(t11 * orderComplementWordTwo.toULong())
        acc.add(t12 * orderComplementWordOne.toULong())
        acc.add(t13 * orderComplementWordZero.toULong())
        val xt5 = acc.rsh32()

        // Terms for 2^(32*6).
        acc.add(t6)
        // acc.add(t8 * uint64(orderComplementWordSix)) // 0
        // acc.add(t9 * uint64(orderComplementWordFive)) // 0
        acc.add(t10) // * uint64(orderComplementWordFour)) // * 1
        acc.add(t11 * orderComplementWordThree.toULong())
        acc.add(t12 * orderComplementWordTwo.toULong())
        acc.add(t13 * orderComplementWordOne.toULong())
        acc.add(t14 * orderComplementWordZero.toULong())
        val xt6 = acc.rsh32()

        // Terms for 2^(32*7).
        acc.add(t7)
        // acc.add(t8 * uint64(orderComplementWordSeven)) // 0
        // acc.add(t9 * uint64(orderComplementWordSix)) // 0
        // acc.add(t10 * uint64(orderComplementWordFive)) // 0
        acc.add(t11) // * uint64(orderComplementWordFour) // * 1
        acc.add(t12 * orderComplementWordThree.toULong())
        acc.add(t13 * orderComplementWordTwo.toULong())
        acc.add(t14 * orderComplementWordOne.toULong())
        acc.add(t15 * orderComplementWordZero.toULong())
        val xt7 = acc.rsh32()

        // Terms for 2^(32*8).
        // acc.add(t9 * uint64(orderComplementWordSeven)) // 0
        // acc.add(t10 * uint64(orderComplementWordSix)) // 0
        // acc.add(t11 * uint64(orderComplementWordFive)) // 0
        acc.add(t12) // * uint64(orderComplementWordFour) // * 1
        acc.add(t13 * orderComplementWordThree.toULong())
        acc.add(t14 * orderComplementWordTwo.toULong())
        acc.add(t15 * orderComplementWordOne.toULong())
        val xt8 = acc.rsh32()

        // Terms for 2^(32*9).
        // acc.add(t10 * uint64(orderComplementWordSeven)) // 0
        // acc.add(t11 * uint64(orderComplementWordSix)) // 0
        // acc.add(t12 * uint64(orderComplementWordFive)) // 0
        acc.add(t13) // * uint64(orderComplementWordFour) // * 1
        acc.add(t14 * orderComplementWordThree.toULong())
        acc.add(t15 * orderComplementWordTwo.toULong())
        val xt9 = acc.rsh32()

        // Terms for 2^(32*10).
        // acc.add(t11 * uint64(orderComplementWordSeven)) // 0
        // acc.add(t12 * uint64(orderComplementWordSix)) // 0
        // acc.add(t13 * uint64(orderComplementWordFive)) // 0
        acc.add(t14) // * uint64(orderComplementWordFour) // * 1
        acc.add(t15 * orderComplementWordThree.toULong())
        val xt10 = acc.rsh32()

        // Terms for 2^(32*11).
        // acc.add(t12 * uint64(orderComplementWordSeven)) // 0
        // acc.add(t13 * uint64(orderComplementWordSix)) // 0
        // acc.add(t14 * uint64(orderComplementWordFive)) // 0
        acc.add(t15) // * uint64(orderComplementWordFour) // * 1
        val xt11 = acc.rsh32()

        // NOTE: All of the remaining multiplications for this iteration result in 0
        // as they all involve multiplying by combinations of the fifth, sixth, and
        // seventh words of the two's complement of N, which are 0, so skip them.

        // Terms for 2^(32*12).
        val xt12 = acc.rsh32()

        // At this point, the result is reduced to fit within 385 bits, so reduce it
        // again using the same method accordingly.
        return reduce385(xt0, xt1, xt2, xt3, xt4, xt5, xt6, xt7, xt8, xt9, xt10, xt11, xt12)
    }

    // Mul2 multiplies the passed two scalars together modulo the group order in
    // constant time and stores the result in s.
    //
    // The scalar is returned to support chaining.  This enables syntax like:
    // s3.Mul2(s, s2).AddInt(1) so that s3 = (s * s2) + 1.
    operator fun times(val2: ModNScalar): ModNScalar {
        // This could be done with for loops and an array to store the intermediate
        // terms, but this unrolled version is significantly faster.

        // The overall strategy employed here is:
        // 1) Calculate the 512-bit product of the two scalars using the standard
        //    pencil-and-paper method.
        // 2) Reduce the result modulo the prime by effectively subtracting
        //    multiples of the group order N (actually performed by adding multiples
        //    of the two's complement of N to avoid implementing subtraction).
        // 3) Repeat step 2 noting that each iteration reduces the required number
        //    of bits by 127 because the two's complement of N has 127 leading zero
        //    bits.
        // 4) Once reduced to 256 bits, call the existing reduce method to perform
        //    a final reduction as needed.
        //
        // Note that several of the intermediate calculations require adding 64-bit
        // products together which would overflow a uint64, so a 96-bit accumulator
        // is used instead.

        // Terms for 2^(32*0).
        val acc = Accumulator96.Zero
        acc.add(this._n0.toULong() * val2._n0.toULong())
        val t0 = acc.rsh32()

        // Terms for 2^(32*1).
        acc.add(this._n0.toULong() * val2._n1.toULong())
        acc.add(this._n1.toULong() * val2._n0.toULong())
        val t1 = acc.rsh32()

        // Terms for 2^(32*2).
        acc.add(this._n0.toULong() * val2._n2.toULong())
        acc.add(this._n1.toULong() * val2._n1.toULong())
        acc.add(this._n2.toULong() * val2._n0.toULong())
        val t2 = acc.rsh32()

        // Terms for 2^(32*3).
        acc.add(this._n0.toULong() * val2._n3.toULong())
        acc.add(this._n1.toULong() * val2._n2.toULong())
        acc.add(this._n2.toULong() * val2._n1.toULong())
        acc.add(this._n3.toULong() * val2._n0.toULong())
        val t3 = acc.rsh32()

        // Terms for 2^(32*4).
        acc.add(this._n0.toULong() * val2._n4.toULong())
        acc.add(this._n1.toULong() * val2._n3.toULong())
        acc.add(this._n2.toULong() * val2._n2.toULong())
        acc.add(this._n3.toULong() * val2._n1.toULong())
        acc.add(this._n4.toULong() * val2._n0.toULong())
        val t4 = acc.rsh32()

        // Terms for 2^(32*5).
        acc.add(this._n0.toULong() * val2._n5.toULong())
        acc.add(this._n1.toULong() * val2._n4.toULong())
        acc.add(this._n2.toULong() * val2._n3.toULong())
        acc.add(this._n3.toULong() * val2._n2.toULong())
        acc.add(this._n4.toULong() * val2._n1.toULong())
        acc.add(this._n5.toULong() * val2._n0.toULong())
        val t5 = acc.rsh32()

        // Terms for 2^(32*6).
        acc.add(this._n0.toULong() * val2._n6.toULong())
        acc.add(this._n1.toULong() * val2._n5.toULong())
        acc.add(this._n2.toULong() * val2._n4.toULong())
        acc.add(this._n3.toULong() * val2._n3.toULong())
        acc.add(this._n4.toULong() * val2._n2.toULong())
        acc.add(this._n5.toULong() * val2._n1.toULong())
        acc.add(this._n6.toULong() * val2._n0.toULong())
        val t6 = acc.rsh32()

        // Terms for 2^(32*7).
        acc.add(this._n0.toULong() * val2._n7.toULong())
        acc.add(this._n1.toULong() * val2._n6.toULong())
        acc.add(this._n2.toULong() * val2._n5.toULong())
        acc.add(this._n3.toULong() * val2._n4.toULong())
        acc.add(this._n4.toULong() * val2._n3.toULong())
        acc.add(this._n5.toULong() * val2._n2.toULong())
        acc.add(this._n6.toULong() * val2._n1.toULong())
        acc.add(this._n7.toULong() * val2._n0.toULong())
        val t7 = acc.rsh32()

        // Terms for 2^(32*8).
        acc.add(this._n1.toULong() * val2._n7.toULong())
        acc.add(this._n2.toULong() * val2._n6.toULong())
        acc.add(this._n3.toULong() * val2._n5.toULong())
        acc.add(this._n4.toULong() * val2._n4.toULong())
        acc.add(this._n5.toULong() * val2._n3.toULong())
        acc.add(this._n6.toULong() * val2._n2.toULong())
        acc.add(this._n7.toULong() * val2._n1.toULong())
        val t8 = acc.rsh32()

        // Terms for 2^(32*9).
        acc.add(this._n2.toULong() * val2._n7.toULong())
        acc.add(this._n3.toULong() * val2._n6.toULong())
        acc.add(this._n4.toULong() * val2._n5.toULong())
        acc.add(this._n5.toULong() * val2._n4.toULong())
        acc.add(this._n6.toULong() * val2._n3.toULong())
        acc.add(this._n7.toULong() * val2._n2.toULong())
        val t9 = acc.rsh32()

        // Terms for 2^(32*10).
        acc.add(this._n3.toULong() * val2._n7.toULong())
        acc.add(this._n4.toULong() * val2._n6.toULong())
        acc.add(this._n5.toULong() * val2._n5.toULong())
        acc.add(this._n6.toULong() * val2._n4.toULong())
        acc.add(this._n7.toULong() * val2._n3.toULong())
        val t10 = acc.rsh32()

        // Terms for 2^(32*11).
        acc.add(this._n4.toULong() * val2._n7.toULong())
        acc.add(this._n5.toULong() * val2._n6.toULong())
        acc.add(this._n6.toULong() * val2._n5.toULong())
        acc.add(this._n7.toULong() * val2._n4.toULong())
        val t11 = acc.rsh32()

        // Terms for 2^(32*12).
        acc.add(this._n5.toULong() * val2._n7.toULong())
        acc.add(this._n6.toULong() * val2._n6.toULong())
        acc.add(this._n7.toULong() * val2._n5.toULong())
        val t12 = acc.rsh32()

        // Terms for 2^(32*13).
        acc.add(this._n6.toULong() * val2._n7.toULong())
        acc.add(this._n7.toULong() * val2._n6.toULong())
        val t13 = acc.rsh32()

        // Terms for 2^(32*14).
        acc.add(this._n7.toULong() * val2._n7.toULong())
        val t14 = acc.rsh32()

        // What's left is for 2^(32*15).
        val t15 = acc.rsh32()

        // At this point, all of the terms are grouped into their respective base
        // and occupy up to 512 bits.  Reduce the result accordingly.
        return reduce512(t0, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15)
    }

    operator fun unaryMinus(): ModNScalar {
        // Since the scalar is already in the range 0 <= val < N, where N is the
        // group order, negation modulo the group order is just the group order
        // minus the value.  This implies that the result will always be in the
        // desired range with the sole exception of 0 because N - 0 = N itself.
        //
        // Therefore, in order to avoid the need to reduce the result for every
        // other case in order to achieve constant time, this creates a mask that is
        // all 0s in the case of the scalar being negated is 0 and all 1s otherwise
        // and bitwise ands that mask with each word.
        //
        // Finally, to simplify the carry propagation, this adds the two's
        // complement of the scalar to N in order to achieve the same result.
        val bits = _n0 or _n1 or _n2 or _n3 or _n4 or _n5 or _n6 or _n7
        val mask = (uint32Mask * constantTimeNotEq(bits, 0u))
        var c = orderWordZero.toULong() + (_n0.inv().toULong() + 1u)
        val n0 = (c and mask).toUInt()
        c = (c shr 32) + orderWordOne.toULong() + (_n1.inv()).toULong()
        val n1 = (c and mask).toUInt()
        c = (c shr 32) + orderWordTwo.toULong() + (_n2.inv()).toULong()
        val n2 = (c and mask).toUInt()
        c = (c shr 32) + orderWordThree.toULong() + (_n3.inv()).toULong()
        val n3 = (c and mask).toUInt()
        c = (c shr 32) + orderWordFour.toULong() + (_n4.inv()).toULong()
        val n4 = (c and mask).toUInt()
        c = (c shr 32) + orderWordFive.toULong() + (_n5.inv()).toULong()
        val n5 = (c and mask).toUInt()
        c = (c shr 32) + orderWordSix.toULong() + (_n6.inv()).toULong()
        val n6 = (c and mask).toUInt()
        c = (c shr 32) + orderWordSeven.toULong() + (_n7.inv()).toULong()
        val n7 = (c and mask).toUInt()
        return ModNScalar(n0, n1, n2, n3, n4, n5, n6, n7)
    }

    fun square(): ModNScalar {
        return this.times(this)
    }

    fun inverse(): ModNScalar {
        // This is making use of big integers for now.  Ideally it will be replaced
        // with an implementation that does not depend on big integers.
        val big = BigInt.fromBytes(bytes())
        if (big == BigInteger.ZERO) {
            return Zero
        }
        val bigInv = big.modInverse(Secp256k1Curve.n)
        return setByteSlice(BigInt.toBytes(bigInv))
    }

    // IsOverHalfOrder returns whether or not the scalar exceeds the group order
    // divided by 2 in constant time.
    fun isOverHalfOrder(): Boolean {
        // The intuition here is that the scalar is greater than half of the group
        // order if one of the higher individual words is greater than the
        // corresponding word of the half group order and all higher words in the
        // scalar are equal to their corresponding word of the half group order.
        //
        // Note that the words 4, 5, and 6 are all the max uint32 value, so there is
        // no need to test if those individual words of the scalar exceeds them,
        // hence, only equality is checked for them.
        var result = constantTimeGreater(_n7, halfOrderWordSeven)
        var highWordsEqual = constantTimeEq(_n7, halfOrderWordSeven)
        highWordsEqual = highWordsEqual and constantTimeEq(_n6, halfOrderWordSix)
        highWordsEqual = highWordsEqual and constantTimeEq(_n5, halfOrderWordFive)
        highWordsEqual = highWordsEqual and constantTimeEq(_n4, halfOrderWordFour)
        result = result or (highWordsEqual and constantTimeGreater(_n3, halfOrderWordThree))
        highWordsEqual = highWordsEqual and (constantTimeEq(_n3, halfOrderWordThree))
        result = result or (highWordsEqual and constantTimeGreater(_n2, halfOrderWordTwo))
        highWordsEqual = highWordsEqual and constantTimeEq(_n2, halfOrderWordTwo)
        result = result or (highWordsEqual and constantTimeGreater(_n1, halfOrderWordOne))
        highWordsEqual = highWordsEqual and constantTimeEq(_n1, halfOrderWordOne)
        result = result or (highWordsEqual and constantTimeGreater(_n0, halfOrderWordZero))
        return result != 0u
    }

    override fun hashCode(): Int {
        var result = 1u
        result = 31u * result + _n0
        result = 31u * result + _n1
        result = 31u * result + _n2
        result = 31u * result + _n3
        result = 31u * result + _n4
        result = 31u * result + _n5
        result = 31u * result + _n6
        result = 31u * result + _n7
        return result.toInt()
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
        if (other !is ModNScalar) {
            return super.equals(other)
        }
        val bits = _n0 xor other._n0 or (_n1 xor other._n1) or (_n2 xor other._n2) or
            (_n3 xor other._n3) or (_n4 xor other._n4) or (_n5 xor other._n5) or
            (_n6 xor other._n6) or (_n7 xor other._n7)
        return bits == 0u
    }

    override fun toString(): String {
        return Hex.encode(bytes()).trimStart('0')
    }

    companion object {
        // These fields provide convenient access to each of the words of the
        // secp256k1 curve group order N to improve code readability.
        //
        // The group order of the curve per [SECG] is:
        // 0xffffffff ffffffff ffffffff fffffffe baaedce6 af48a03b bfd25e8c d0364141
        private const val orderWordZero = 0xd0364141u
        private const val orderWordOne = 0xbfd25e8cu
        private const val orderWordTwo = 0xaf48a03bu
        private const val orderWordThree = 0xbaaedce6u
        private const val orderWordFour = 0xfffffffeu
        private const val orderWordFive = 0xffffffffu
        private const val orderWordSix = 0xffffffffu
        private const val orderWordSeven = 0xffffffffu

        // These fields provide convenient access to each of the words of the two's
        // complement of the secp256k1 curve group order N to improve code
        // readability.
        //
        // The two's complement of the group order is:
        // 0x00000000 00000000 00000000 00000001 45512319 50b75fc4 402da173 2fc9bebf
        private val orderComplementWordZero = orderWordZero.inv() + 1u
        private val orderComplementWordOne = orderWordOne.inv()
        private val orderComplementWordTwo = orderWordTwo.inv()
        private val orderComplementWordThree = orderWordThree.inv()

        // These fields provide convenient access to each of the words of the
        // secp256k1 curve group order N / 2 to improve code readability and avoid
        // the need to recalculate them.
        //
        // The half order of the secp256k1 curve group is:
        // 0x7fffffff ffffffff ffffffff ffffffff 5d576e73 57a4501d dfe92f46 681b20a0
        private const val halfOrderWordZero = 0x681b20a0u
        private const val halfOrderWordOne = 0xdfe92f46u
        private const val halfOrderWordTwo = 0x57a4501du
        private const val halfOrderWordThree = 0x5d576e73u
        private const val halfOrderWordFour = 0xffffffffu
        private const val halfOrderWordFive = 0xffffffffu
        private const val halfOrderWordSix = 0xffffffffu
        private const val halfOrderWordSeven = 0x7fffffffu

        // uint32Mask is simply a mask with all bits set for a uint32 and is used to
        // improve the readability of the code.
        private const val uint32Mask = 0xffffffffuL

        val Zero = ModNScalar(0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u)

        fun setInt(ui: UInt): ModNScalar {
            return ModNScalar(ui, 0u, 0u, 0u, 0u, 0u, 0u, 0u)
        }

        fun setBytesUnchecked(b: ByteArray): ModNScalar {
            // Pack the 256 total bits across the 8 uint32 words.  This could be done
            // with a for loop, but benchmarks show this unrolled version is about 2
            // times faster than the variant that uses a loop.
            val n0 = b[31].toUByte().toUInt() or (b[30].toUByte().toUInt() shl 8) or (b[29].toUByte().toUInt() shl 16) or (b[28].toUByte().toUInt() shl 24)
            val n1 = b[27].toUByte().toUInt() or (b[26].toUByte().toUInt() shl 8) or (b[25].toUByte().toUInt() shl 16) or (b[24].toUByte().toUInt() shl 24)
            val n2 = b[23].toUByte().toUInt() or (b[22].toUByte().toUInt() shl 8) or (b[21].toUByte().toUInt() shl 16) or (b[20].toUByte().toUInt() shl 24)
            val n3 = b[19].toUByte().toUInt() or (b[18].toUByte().toUInt() shl 8) or (b[17].toUByte().toUInt() shl 16) or (b[16].toUByte().toUInt() shl 24)
            val n4 = b[15].toUByte().toUInt() or (b[14].toUByte().toUInt() shl 8) or (b[13].toUByte().toUInt() shl 16) or (b[12].toUByte().toUInt() shl 24)
            val n5 = b[11].toUByte().toUInt() or (b[10].toUByte().toUInt() shl 8) or (b[9].toUByte().toUInt() shl 16) or (b[8].toUByte().toUInt() shl 24)
            val n6 = b[7].toUByte().toUInt() or (b[6].toUByte().toUInt() shl 8) or (b[5].toUByte().toUInt() shl 16) or (b[4].toUByte().toUInt() shl 24)
            val n7 = b[3].toUByte().toUInt() or (b[2].toUByte().toUInt() shl 8) or (b[1].toUByte().toUInt() shl 16) or (b[0].toUByte().toUInt() shl 24)
            return ModNScalar(n0, n1, n2, n3, n4, n5, n6, n7)
        }

        // SetBytes interprets the provided array as a 256-bit big-endian unsigned
        // integer, reduces it modulo the group order, sets the scalar to the result,
        // and returns either 1 if it was reduced (aka it overflowed) or 0 otherwise in
        // constant time.
        //
        // Note that a bool is not used here because it is not possible in Go to convert
        // from a bool to numeric value in constant time and many constant-time
        // operations require a numeric value.
        fun setBytes(b: ByteArray): ModNScalar {
            // The value might be >= N, so reduce it as required and return whether or
            // not it was reduced.
            val s = setBytesUnchecked(b)
            return s.reduce256(s.overflows)
        }

        // SetByteSlice interprets the provided slice as a 256-bit big-endian unsigned
        // integer (meaning it is truncated to the first 32 bytes), reduces it modulo
        // the group order, sets the scalar to the result, and returns whether or not
        // the resulting truncated 256-bit integer overflowed in constant time.
        //
        // Note that since passing a slice with more than 32 bytes is truncated, it is
        // possible that the truncated value is less than the order of the curve and
        // hence it will not be reported as having overflowed in that case.  It is up to
        // the caller to decide whether it needs to provide numbers of the appropriate
        // size or it is acceptable to use this function with the described truncation
        // and overflow behavior.
        fun setByteSlice(bytes: ByteArray): ModNScalar {
            val b32 = ByteArray(32)
            System.arraycopy(bytes, 0, b32, max(0, 32 - bytes.size), min(32, bytes.size))
            return setBytes(b32)
        }
    }
}
