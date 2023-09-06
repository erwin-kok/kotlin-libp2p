// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.secp256k1

import org.erwinkok.libp2p.crypto.math.Subtle.constantTimeEq
import org.erwinkok.libp2p.crypto.math.Subtle.constantTimeGreater
import org.erwinkok.libp2p.crypto.math.Subtle.constantTimeGreaterOrEq
import org.erwinkok.util.Hex
import org.erwinkok.util.Tuple
import org.erwinkok.util.Tuple2
import kotlin.math.max
import kotlin.math.min

// fieldVal implements optimized fixed-precision arithmetic over the
// secp256k1 finite field.  This means all arithmetic is performed modulo
// 0xfffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2  It
// represents each 256-bit value as 10 32-bit integers in base 2^26.  This
// provides 6 bits of overflow in each word (10 bits in the most significant
// word) for a total of 64 bits of overflow (9*6 + 10 = 64).  It only implements
// the arithmetic needed for elliptic curve operations.
//
// The following depicts the internal representation:
// 	 -----------------------------------------------------------------
// 	|        n[9]       |        n[8]       | ... |        n[0]       |
// 	| 32 bits available | 32 bits available | ... | 32 bits available |
// 	| 22 bits for value | 26 bits for value | ... | 26 bits for value |
// 	| 10 bits overflow  |  6 bits overflow  | ... |  6 bits overflow  |
// 	| Mult: 2^(26*9)    | Mult: 2^(26*8)    | ... | Mult: 2^(26*0)    |
// 	 -----------------------------------------------------------------
//
// For example, consider the number 2^49 + 1.  It would be represented as:
// 	n[0] = 1
// 	n[1] = 2^23
// 	n[2..9] = 0
//
// The full 256-bit value is then calculated by looping i from 9..0 and
// doing sum(n[i] * 2^(26i)) like so:
// 	n[9] * 2^(26*9) = 0    * 2^234 = 0
// 	n[8] * 2^(26*8) = 0    * 2^208 = 0
// 	...
// 	n[1] * 2^(26*1) = 2^23 * 2^26  = 2^49
// 	n[0] * 2^(26*0) = 1    * 2^0   = 1
// 	Sum: 0 + 0 + ... + 2^49 + 1 = 2^49 + 1
class FieldVal(private val _n0: Int, private val _n1: Int, private val _n2: Int, private val _n3: Int, private val _n4: Int, private val _n5: Int, private val _n6: Int, private val _n7: Int, private val _n8: Int, private val _n9: Int) {
    val n: IntArray
        get() = intArrayOf(_n0, _n1, _n2, _n3, _n4, _n5, _n6, _n7, _n8, _n9)

    // IsZeroBit returns 1 when the field value is equal to zero or 0 otherwise in
    // constant time.
    //
    // Note that a bool is not used here because it is not possible in Go to convert
    // from a bool to numeric value in constant time and many constant-time
    // operations require a numeric value.  See IsZero for the version that returns
    // a bool.
    //
    // Preconditions:
    //   - The field value MUST be normalized
    val isZeroBit: UInt
        get() {
            // The value can only be zero if no bits are set in any of the words.
            // This is a constant time implementation.
            val bits = _n0 or _n1 or _n2 or _n3 or _n4 or _n5 or _n6 or _n7 or _n8 or _n9
            return constantTimeEq(bits.toUInt(), 0u)
        }

    // This is a constant time implementation.
    // IsZero returns whether or not the field value is equal to zero.
    val isZero: Boolean
        get() {
            // The value can only be zero if no bits are set in any of the words.
            // This is a constant time implementation.
            val bits = _n0 or _n1 or _n2 or _n3 or _n4 or _n5 or _n6 or _n7 or _n8 or _n9
            return bits == 0
        } // Only odd numbers have the bottom bit set.

    // IsOneBit returns 1 when the field value is equal to one or 0 otherwise in
    // constant time.
    //
    // Note that a bool is not used here because it is not possible in Go to convert
    // from a bool to numeric value in constant time and many constant-time
    // operations require a numeric value.  See IsOne for the version that returns a
    // bool.
    //
    // Preconditions:
    //   - The field value MUST be normalized
    val isOneBit: UInt
        get() {
            // The value can only be one if the single lowest significant bit is set in
            // the first word and no other bits are set in any of the other words.
            // This is a constant time implementation.
            val bits = (_n0 xor 1) or _n1 or _n2 or _n3 or _n4 or _n5 or _n6 or _n7 or _n8 or _n9
            return constantTimeEq(bits.toUInt(), 0u)
        }

    // IsOne returns whether or not the field value is equal to one in constant
    // time.
    //
    // Preconditions:
    //   - The field value MUST be normalized
    val isOne: Boolean
        get() {
            // The value can only be one if the single lowest significant bit is set in
            // the first word and no other bits are set in any of the other words.
            // This is a constant time implementation.
            val bits = (n[0] xor 1) or n[1] or n[2] or n[3] or n[4] or n[5] or n[6] or n[7] or n[8] or n[9]
            return bits == 0
        }

    // IsOddBit returns 1 when the field value is an odd number or 0 otherwise in
    // constant time.
    //
    // Note that a bool is not used here because it is not possible in Go to convert
    // from a bool to numeric value in constant time and many constant-time
    // operations require a numeric value.  See IsOdd for the version that returns a
    // bool.
    //
    // Preconditions:
    //   - The field value MUST be normalized
    val isOddBit: UInt
        get() {
            // Only odd numbers have the bottom bit set.
            return (_n0 and 1).toUInt()
        }

    // IsOdd returns whether or not the field value is an odd number.
    //
    // The field value must be normalized for this function to return correct
    // result.
    val isOdd: Boolean
        get() = // Only odd numbers have the bottom bit set.
            _n0 and 1 == 1

    val overflows: Boolean
        get() {
            // The intuition here is that the field value is greater than the prime if
            // one of the higher individual words is greater than corresponding word of
            // the prime and all higher words in the field value are equal to their
            // corresponding word of the prime.  Since this type is modulo the prime,
            // being equal is also an overflow back to 0.
            //
            // Note that because the input is 32 bytes and it was just packed into the
            // field representation, the only words that can possibly be greater are
            // zero and one, because ceil(log_2(2^256 - 1 - P)) = 33 bits max and the
            // internal field representation encodes 26 bits with each word.
            //
            // Thus, there is no need to test if the upper words of the field value
            // exceeds them, hence, only equality is checked for them.
            var highWordsEq = constantTimeEq(_n9.toUInt(), fieldPrimeWordNine.toUInt())
            highWordsEq = highWordsEq and constantTimeEq(_n8.toUInt(), fieldPrimeWordEight.toUInt())
            highWordsEq = highWordsEq and constantTimeEq(_n7.toUInt(), fieldPrimeWordSeven.toUInt())
            highWordsEq = highWordsEq and constantTimeEq(_n6.toUInt(), fieldPrimeWordSix.toUInt())
            highWordsEq = highWordsEq and constantTimeEq(_n5.toUInt(), fieldPrimeWordFive.toUInt())
            highWordsEq = highWordsEq and constantTimeEq(_n4.toUInt(), fieldPrimeWordFour.toUInt())
            highWordsEq = highWordsEq and constantTimeEq(_n3.toUInt(), fieldPrimeWordThree.toUInt())
            highWordsEq = highWordsEq and constantTimeEq(_n2.toUInt(), fieldPrimeWordTwo.toUInt())
            var overflow = highWordsEq and constantTimeGreater(_n1.toUInt(), fieldPrimeWordOne.toUInt())
            highWordsEq = highWordsEq and constantTimeEq(_n1.toUInt(), fieldPrimeWordOne.toUInt())
            overflow = overflow or (highWordsEq and constantTimeGreaterOrEq(_n0.toUInt(), fieldPrimeWordZero.toUInt()))
            return overflow != 0u
        }

    // AddInt adds the passed integer to the existing field value and stores the
    // result in   This is a convenience function since it is fairly common to
    // perform some arithemetic with small native integers.
    //
    // The field value is returned to support chaining.  This enables syntax like:
    // AddInt(1).Add(f2) so that f = f + 1 + f2.
    operator fun plus(b: Int): FieldVal {
        // Since the field representation intentionally provides overflow bits,
        // it's ok to use carryless addition as the carry bit is safely part of
        // the word and will be normalized out.
        return FieldVal(_n0 + b, _n1, _n2, _n3, _n4, _n5, _n6, _n7, _n8, _n9)
    }

    // Add adds the passed value to the existing field value and stores the result
    // in
    //
    // The field value is returned to support chaining.  This enables syntax like:
    // Add(f2).AddInt(1) so that f = f + f2 + 1.
    operator fun plus(b: FieldVal): FieldVal {
        return FieldVal(_n0 + b._n0, _n1 + b._n1, _n2 + b._n2, _n3 + b._n3, _n4 + b._n4, _n5 + b._n5, _n6 + b._n6, _n7 + b._n7, _n8 + b._n8, _n9 + b._n9)
    }

    // MulInt multiplies the field value by the passed int and stores the result in
    //   Note that this function can overflow if multiplying the value by any of
    // the individual words exceeds a max (int).  Therefore it is important that
    // the caller ensures no overflows will occur before using this function.
    //
    // The field value is returned to support chaining.  This enables syntax like:
    // MulInt(2).Add(f2) so that f = 2 * f + f2.
    operator fun times(b: Int): FieldVal {
        return FieldVal(_n0 * b, _n1 * b, _n2 * b, _n3 * b, _n4 * b, _n5 * b, _n6 * b, _n7 * b, _n8 * b, _n9 * b)
    }

    // Mul multiplies the passed value to the existing field value and stores the
    // result in   Note that this function can overflow if multiplying any
    // of the individual words exceeds a max (int).  In practice, this means the
    // magnitude of either value involved in the multiplication must be a max of
    // 8.
    //
    operator fun times(b: FieldVal): FieldVal {
        // This could be done with a couple of for loops and an array to store
        // the intermediate terms, but this unrolled version is significantly
        // faster.

        // Terms for 2^(fieldBase*0).
        var m = (_n0.toLong() and 0xffffffffL) * (b._n0.toLong() and 0xffffffffL)
        var t0 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*1).
        m = (m ushr fieldBase) + (_n0.toLong() and 0xffffffffL) * (b._n1.toLong() and 0xffffffffL) + (_n1.toLong() and 0xffffffffL) * (b._n0.toLong() and 0xffffffffL)
        var t1 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*2).
        m = (m ushr fieldBase) + (_n0.toLong() and 0xffffffffL) * (b._n2.toLong() and 0xffffffffL) + (_n1.toLong() and 0xffffffffL) * (b._n1.toLong() and 0xffffffffL) +
            (_n2.toLong() and 0xffffffffL) * (b._n0.toLong() and 0xffffffffL)
        var t2 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*3).
        m = (m ushr fieldBase) + (_n0.toLong() and 0xffffffffL) * (b._n3.toLong() and 0xffffffffL) + (_n1.toLong() and 0xffffffffL) * (b._n2.toLong() and 0xffffffffL) +
            (_n2.toLong() and 0xffffffffL) * (b._n1.toLong() and 0xffffffffL) + (_n3.toLong() and 0xffffffffL) * (b._n0.toLong() and 0xffffffffL)
        var t3 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*4).
        m = (m ushr fieldBase) + (_n0.toLong() and 0xffffffffL) * (b._n4.toLong() and 0xffffffffL) + (_n1.toLong() and 0xffffffffL) * (b._n3.toLong() and 0xffffffffL) +
            (_n2.toLong() and 0xffffffffL) * (b._n2.toLong() and 0xffffffffL) + (_n3.toLong() and 0xffffffffL) * (b._n1.toLong() and 0xffffffffL) +
            (_n4.toLong() and 0xffffffffL) * (b._n0.toLong() and 0xffffffffL)
        var t4 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*5).
        m = (m ushr fieldBase) + (_n0.toLong() and 0xffffffffL) * (b._n5.toLong() and 0xffffffffL) + (_n1.toLong() and 0xffffffffL) * (b._n4.toLong() and 0xffffffffL) +
            (_n2.toLong() and 0xffffffffL) * (b._n3.toLong() and 0xffffffffL) + (_n3.toLong() and 0xffffffffL) * (b._n2.toLong() and 0xffffffffL) +
            (_n4.toLong() and 0xffffffffL) * (b._n1.toLong() and 0xffffffffL) + (_n5.toLong() and 0xffffffffL) * (b._n0.toLong() and 0xffffffffL)
        var t5 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*6).
        m = (m ushr fieldBase) + (_n0.toLong() and 0xffffffffL) * (b._n6.toLong() and 0xffffffffL) + (_n1.toLong() and 0xffffffffL) * (b._n5.toLong() and 0xffffffffL) +
            (_n2.toLong() and 0xffffffffL) * (b._n4.toLong() and 0xffffffffL) + (_n3.toLong() and 0xffffffffL) * (b._n3.toLong() and 0xffffffffL) +
            (_n4.toLong() and 0xffffffffL) * (b._n2.toLong() and 0xffffffffL) + (_n5.toLong() and 0xffffffffL) * (b._n1.toLong() and 0xffffffffL) +
            (_n6.toLong() and 0xffffffffL) * (b._n0.toLong() and 0xffffffffL)
        var t6 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*7).
        m = (m ushr fieldBase) + (_n0.toLong() and 0xffffffffL) * (b._n7.toLong() and 0xffffffffL) + (_n1.toLong() and 0xffffffffL) * (b._n6.toLong() and 0xffffffffL) +
            (_n2.toLong() and 0xffffffffL) * (b._n5.toLong() and 0xffffffffL) + (_n3.toLong() and 0xffffffffL) * (b._n4.toLong() and 0xffffffffL) +
            (_n4.toLong() and 0xffffffffL) * (b._n3.toLong() and 0xffffffffL) + (_n5.toLong() and 0xffffffffL) * (b._n2.toLong() and 0xffffffffL) +
            (_n6.toLong() and 0xffffffffL) * (b._n1.toLong() and 0xffffffffL) + (_n7.toLong() and 0xffffffffL) * (b._n0.toLong() and 0xffffffffL)
        var t7 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*8).
        m = (m ushr fieldBase) + (_n0.toLong() and 0xffffffffL) * (b._n8.toLong() and 0xffffffffL) + (_n1.toLong() and 0xffffffffL) * (b._n7.toLong() and 0xffffffffL) +
            (_n2.toLong() and 0xffffffffL) * (b._n6.toLong() and 0xffffffffL) + (_n3.toLong() and 0xffffffffL) * (b._n5.toLong() and 0xffffffffL) +
            (_n4.toLong() and 0xffffffffL) * (b._n4.toLong() and 0xffffffffL) + (_n5.toLong() and 0xffffffffL) * (b._n3.toLong() and 0xffffffffL) +
            (_n6.toLong() and 0xffffffffL) * (b._n2.toLong() and 0xffffffffL) + (_n7.toLong() and 0xffffffffL) * (b._n1.toLong() and 0xffffffffL) +
            (_n8.toLong() and 0xffffffffL) * (b._n0.toLong() and 0xffffffffL)
        var t8 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*9).
        m = (m ushr fieldBase) + (_n0.toLong() and 0xffffffffL) * (b._n9.toLong() and 0xffffffffL) + (_n1.toLong() and 0xffffffffL) * (b._n8.toLong() and 0xffffffffL) +
            (_n2.toLong() and 0xffffffffL) * (b._n7.toLong() and 0xffffffffL) + (_n3.toLong() and 0xffffffffL) * (b._n6.toLong() and 0xffffffffL) +
            (_n4.toLong() and 0xffffffffL) * (b._n5.toLong() and 0xffffffffL) + (_n5.toLong() and 0xffffffffL) * (b._n4.toLong() and 0xffffffffL) +
            (_n6.toLong() and 0xffffffffL) * (b._n3.toLong() and 0xffffffffL) + (_n7.toLong() and 0xffffffffL) * (b._n2.toLong() and 0xffffffffL) +
            (_n8.toLong() and 0xffffffffL) * (b._n1.toLong() and 0xffffffffL) + (_n9.toLong() and 0xffffffffL) * (b._n0.toLong() and 0xffffffffL)
        var t9 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*10).
        m = (m ushr fieldBase) + (_n1.toLong() and 0xffffffffL) * (b._n9.toLong() and 0xffffffffL) + (_n2.toLong() and 0xffffffffL) * (b._n8.toLong() and 0xffffffffL) +
            (_n3.toLong() and 0xffffffffL) * (b._n7.toLong() and 0xffffffffL) + (_n4.toLong() and 0xffffffffL) * (b._n6.toLong() and 0xffffffffL) +
            (_n5.toLong() and 0xffffffffL) * (b._n5.toLong() and 0xffffffffL) + (_n6.toLong() and 0xffffffffL) * (b._n4.toLong() and 0xffffffffL) +
            (_n7.toLong() and 0xffffffffL) * (b._n3.toLong() and 0xffffffffL) + (_n8.toLong() and 0xffffffffL) * (b._n2.toLong() and 0xffffffffL) +
            (_n9.toLong() and 0xffffffffL) * (b._n1.toLong() and 0xffffffffL)
        val t10 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*11).
        m = (m ushr fieldBase) + (_n2.toLong() and 0xffffffffL) * (b._n9.toLong() and 0xffffffffL) + (_n3.toLong() and 0xffffffffL) * (b._n8.toLong() and 0xffffffffL) +
            (_n4.toLong() and 0xffffffffL) * (b._n7.toLong() and 0xffffffffL) + (_n5.toLong() and 0xffffffffL) * (b._n6.toLong() and 0xffffffffL) +
            (_n6.toLong() and 0xffffffffL) * (b._n5.toLong() and 0xffffffffL) + (_n7.toLong() and 0xffffffffL) * (b._n4.toLong() and 0xffffffffL) +
            (_n8.toLong() and 0xffffffffL) * (b._n3.toLong() and 0xffffffffL) + (_n9.toLong() and 0xffffffffL) * (b._n2.toLong() and 0xffffffffL)
        val t11 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*12).
        m = (m ushr fieldBase) + (_n3.toLong() and 0xffffffffL) * (b._n9.toLong() and 0xffffffffL) + (_n4.toLong() and 0xffffffffL) * (b._n8.toLong() and 0xffffffffL) +
            (_n5.toLong() and 0xffffffffL) * (b._n7.toLong() and 0xffffffffL) + (_n6.toLong() and 0xffffffffL) * (b._n6.toLong() and 0xffffffffL) +
            (_n7.toLong() and 0xffffffffL) * (b._n5.toLong() and 0xffffffffL) + (_n8.toLong() and 0xffffffffL) * (b._n4.toLong() and 0xffffffffL) +
            (_n9.toLong() and 0xffffffffL) * (b._n3.toLong() and 0xffffffffL)
        val t12 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*13).
        m = (m ushr fieldBase) + (_n4.toLong() and 0xffffffffL) * (b._n9.toLong() and 0xffffffffL) + (_n5.toLong() and 0xffffffffL) * (b._n8.toLong() and 0xffffffffL) +
            (_n6.toLong() and 0xffffffffL) * (b._n7.toLong() and 0xffffffffL) + (_n7.toLong() and 0xffffffffL) * (b._n6.toLong() and 0xffffffffL) +
            (_n8.toLong() and 0xffffffffL) * (b._n5.toLong() and 0xffffffffL) + (_n9.toLong() and 0xffffffffL) * (b._n4.toLong() and 0xffffffffL)
        val t13 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*14).
        m = (m ushr fieldBase) + (_n5.toLong() and 0xffffffffL) * (b._n9.toLong() and 0xffffffffL) + (_n6.toLong() and 0xffffffffL) * (b._n8.toLong() and 0xffffffffL) +
            (_n7.toLong() and 0xffffffffL) * (b._n7.toLong() and 0xffffffffL) + (_n8.toLong() and 0xffffffffL) * (b._n6.toLong() and 0xffffffffL) +
            (_n9.toLong() and 0xffffffffL) * (b._n5.toLong() and 0xffffffffL)
        val t14 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*15).
        m = (m ushr fieldBase) + (_n6.toLong() and 0xffffffffL) * (b._n9.toLong() and 0xffffffffL) + (_n7.toLong() and 0xffffffffL) * (b._n8.toLong() and 0xffffffffL) +
            (_n8.toLong() and 0xffffffffL) * (b._n7.toLong() and 0xffffffffL) + (_n9.toLong() and 0xffffffffL) * (b._n6.toLong() and 0xffffffffL)
        val t15 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*16).
        m = (m ushr fieldBase) + (_n7.toLong() and 0xffffffffL) * (b._n9.toLong() and 0xffffffffL) + (_n8.toLong() and 0xffffffffL) * (b._n8.toLong() and 0xffffffffL) +
            (_n9.toLong() and 0xffffffffL) * (b._n7.toLong() and 0xffffffffL)
        val t16 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*17).
        m = (m ushr fieldBase) + (_n8.toLong() and 0xffffffffL) * (b._n9.toLong() and 0xffffffffL) + (_n9.toLong() and 0xffffffffL) * (b._n8.toLong() and 0xffffffffL)
        val t17 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*18).
        m = (m ushr fieldBase) + (_n9.toLong() and 0xffffffffL) * (b._n9.toLong() and 0xffffffffL)
        val t18 = m and fieldBaseMask.toLong()

        // What's left is for 2^(fieldBase*19).
        val t19 = m ushr fieldBase

        // At this point, all of the terms are grouped into their respective
        // base.
        //
        // Per [HAC] section 14.3.4: Reduction method of moduli of special form,
        // when the modulus is of the special form m = b^t - c, highly efficient
        // reduction can be achieved per the provided algorithm.
        //
        // The secp256k1 prime is equivalent to 2^256 - 4294968273, so it fits
        // this criteria.
        //
        // 4294968273 in field representation (base 2^26) is:
        // n[0] = 977
        // n[1] = 64
        // That is to say (2^26 * 64) + 977 = 4294968273
        //
        // Since each word is in base 26, the upper terms (t10 and up) start
        // at 260 bits (versus the final desired range of 256 bits), so the
        // field representation of 'c' from above needs to be adjusted for the
        // extra 4 bits by multiplying it by 2^4 = 16.  4294968273 * 16 =
        // 68719492368.  Thus, the adjusted field representation of 'c' is:
        // n[0] = 977 * 16 = 15632
        // n[1] = 64 * 16 = 1024
        // That is to say (2^26 * 1024) + 15632 = 68719492368
        //
        // To reduce the final term, t19, the entire 'c' value is needed instead
        // of only n[0] because there are no more terms left to handle n[1].
        // This means there might be some magnitude left in the upper bits that
        // is handled below.
        m = t0 + t10 * 15632
        t0 = m and fieldBaseMask.toLong()
        m = (m ushr fieldBase) + t1 + t10 * 1024 + t11 * 15632
        t1 = m and fieldBaseMask.toLong()
        m = (m ushr fieldBase) + t2 + t11 * 1024 + t12 * 15632
        t2 = m and fieldBaseMask.toLong()
        m = (m ushr fieldBase) + t3 + t12 * 1024 + t13 * 15632
        t3 = m and fieldBaseMask.toLong()
        m = (m ushr fieldBase) + t4 + t13 * 1024 + t14 * 15632
        t4 = m and fieldBaseMask.toLong()
        m = (m ushr fieldBase) + t5 + t14 * 1024 + t15 * 15632
        t5 = m and fieldBaseMask.toLong()
        m = (m ushr fieldBase) + t6 + t15 * 1024 + t16 * 15632
        t6 = m and fieldBaseMask.toLong()
        m = (m ushr fieldBase) + t7 + t16 * 1024 + t17 * 15632
        t7 = m and fieldBaseMask.toLong()
        m = (m ushr fieldBase) + t8 + t17 * 1024 + t18 * 15632
        t8 = m and fieldBaseMask.toLong()
        m = (m ushr fieldBase) + t9 + t18 * 1024 + t19 * 68719492368L
        t9 = m and fieldMSBMask.toLong()
        m = m ushr fieldMSBBits

        // At this point, if the magnitude is greater than 0, the overall value
        // is greater than the max possible 256-bit value.  In particular, it is
        // "how many times larger" than the max value it is.
        //
        // The algorithm presented in [HAC] section 14.3.4 repeats until the
        // quotient is zero.  However, due to the above, we already know at
        // least how many times we would need to repeat as it's the value
        // currently in m.  Thus we can simply multiply the magnitude by the
        // field representation of the prime and do a single iteration.  Notice
        // that nothing will be changed when the magnitude is zero, so we could
        // skip this in that case, however always running regardless allows it
        // to run in constant time.  The final result will be in the range
        // 0 <= result <= prime + (2^64 - c), so it is guaranteed to have a
        // magnitude of 1, but it is denormalized.
        var d = t0 + m * 977
        val n0 = (d and fieldBaseMask.toLong()).toInt()
        d = (d ushr fieldBase) + t1 + m * 64
        val n1 = (d and fieldBaseMask.toLong()).toInt()
        val n2 = ((d ushr fieldBase) + t2).toInt()
        val n3 = t3.toInt()
        val n4 = t4.toInt()
        val n5 = t5.toInt()
        val n6 = t6.toInt()
        val n7 = t7.toInt()
        val n8 = t8.toInt()
        val n9 = t9.toInt()
        return FieldVal(n0, n1, n2, n3, n4, n5, n6, n7, n8, n9)
    }

    // Normalize normalizes the internal field words into the desired range and
    // performs fast modular reduction over the secp256k1 prime by making use of the
    // special form of the prime.
    fun normalize(): FieldVal {
        // The field representation leaves 6 bits of overflow in each word so
        // intermediate calculations can be performed without needing to
        // propagate the carry to each higher word during the calculations.  In
        // order to normalize, we need to "compact" the full 256-bit value to
        // the right while propagating any carries through to the high order
        // word.
        //
        // Since this field is doing arithmetic modulo the secp256k1 prime, we
        // also need to perform modular reduction over the prime.
        //
        // Per [HAC] section 14.3.4: Reduction method of moduli of special form,
        // when the modulus is of the special form m = b^t - c, highly efficient
        // reduction can be achieved.
        //
        // The secp256k1 prime is equivalent to 2^256 - 4294968273, so it fits
        // this criteria.
        //
        // 4294968273 in field representation (base 2^26) is:
        // n[0] = 977
        // n[1] = 64
        // That is to say (2^26 * 64) + 977 = 4294968273
        //
        // The algorithm presented in the referenced section typically repeats
        // until the quotient is zero.  However, due to our field representation
        // we already know to within one reduction how many times we would need
        // to repeat as it's the uppermost bits of the high order word.  Thus we
        // can simply multiply the magnitude by the field representation of the
        // prime and do a single iteration.  After this step there might be an
        // additional carry to bit 256 (bit 22 of the high order word).
        var t9 = _n9
        var m = t9 ushr fieldMSBBits
        t9 = t9 and fieldMSBMask
        var t0 = _n0 + m * 977
        var t1 = (t0 ushr fieldBase) + _n1 + (m shl 6)
        t0 = t0 and fieldBaseMask
        var t2 = (t1 ushr fieldBase) + _n2
        t1 = t1 and fieldBaseMask
        var t3 = (t2 ushr fieldBase) + _n3
        t2 = t2 and fieldBaseMask
        var t4 = (t3 ushr fieldBase) + _n4
        t3 = t3 and fieldBaseMask
        var t5 = (t4 ushr fieldBase) + _n5
        t4 = t4 and fieldBaseMask
        var t6 = (t5 ushr fieldBase) + _n6
        t5 = t5 and fieldBaseMask
        var t7 = (t6 ushr fieldBase) + _n7
        t6 = t6 and fieldBaseMask
        var t8 = (t7 ushr fieldBase) + _n8
        t7 = t7 and fieldBaseMask
        t9 = (t8 ushr fieldBase) + t9
        t8 = t8 and fieldBaseMask

        // At this point, the magnitude is guaranteed to be one, however, the
        // value could still be greater than the prime if there was either a
        // carry through to bit 256 (bit 22 of the higher order word) or the
        // value is greater than or equal to the field characteristic.  The
        // following determines if either or these conditions are true and does
        // the final reduction in constant time.
        //
        // Note that the if/else statements here intentionally do the bitwise
        // operators even when it won't change the value to ensure constant time
        // between the branches.  Also note that 'm' will be zero when neither
        // of the aforementioned conditions are true and the value will not be
        // changed when 'm' is zero.
        m = constantTimeEq(t9.toUInt(), fieldMSBMask.toUInt()).toInt()
        m = m and constantTimeEq((t8 and t7 and t6 and t5 and t4 and t3 and t2).toUInt(), fieldBaseMask.toUInt()).toInt()
        m = m and constantTimeGreater((t1 + 64 + ((t0 + 977) shr fieldBase)).toUInt(), fieldBaseMask.toUInt()).toInt()
        m = m or (t9 shr fieldMSBBits)

        t0 += m * 977
        t1 += (t0 ushr fieldBase) + (m shl 6)
        t0 = t0 and fieldBaseMask
        t2 += (t1 ushr fieldBase)
        t1 = t1 and fieldBaseMask
        t3 += (t2 ushr fieldBase)
        t2 = t2 and fieldBaseMask
        t4 += (t3 ushr fieldBase)
        t3 = t3 and fieldBaseMask
        t5 += (t4 ushr fieldBase)
        t4 = t4 and fieldBaseMask
        t6 += (t5 ushr fieldBase)
        t5 = t5 and fieldBaseMask
        t7 += (t6 ushr fieldBase)
        t6 = t6 and fieldBaseMask
        t8 += (t7 ushr fieldBase)
        t7 = t7 and fieldBaseMask
        t9 += (t8 ushr fieldBase)
        t8 = t8 and fieldBaseMask
        t9 = t9 and fieldMSBMask // Remove potential multiple of 2^256.

        // Finally, set the normalized and reduced words.
        return FieldVal(t0, t1, t2, t3, t4, t5, t6, t7, t8, t9)
    }

    // PutBytes unpacks the field value to a 32-byte big-endian value using the
    // passed byte array.  There is a similar function, Bytes, which unpacks the
    // field value into a new array and returns that.  This version is provided
    // since it can be useful to cut down on the number of allocations by allowing
    // the caller to reuse a buffer.
    //
    // The field value must be normalized for this function to return the correct
    // result.
    fun putBytes(b: ByteArray, i: Int) {
        // Unpack the 256 total bits from the 10 (int) words with a max of
        // 26-bits per word.  This could be done with a couple of for loops,
        // but this unrolled version is a bit faster.  Benchmarks show this is
        // about 10 times faster than the variant which uses loops.
        b[i + 31] = (_n0 and eightBitsMask).toByte()
        b[i + 30] = (_n0 ushr 8 and eightBitsMask).toByte()
        b[i + 29] = (_n0 ushr 16 and eightBitsMask).toByte()
        b[i + 28] = (_n0 ushr 24 and twoBitsMask or (_n1 and sixBitsMask shl 2)).toByte()
        b[i + 27] = (_n1 ushr 6 and eightBitsMask).toByte()
        b[i + 26] = (_n1 ushr 14 and eightBitsMask).toByte()
        b[i + 25] = (_n1 ushr 22 and fourBitsMask or (_n2 and fourBitsMask shl 4)).toByte()
        b[i + 24] = (_n2 ushr 4 and eightBitsMask).toByte()
        b[i + 23] = (_n2 ushr 12 and eightBitsMask).toByte()
        b[i + 22] = (_n2 ushr 20 and sixBitsMask or (_n3 and twoBitsMask shl 6)).toByte()
        b[i + 21] = (_n3 ushr 2 and eightBitsMask).toByte()
        b[i + 20] = (_n3 ushr 10 and eightBitsMask).toByte()
        b[i + 19] = (_n3 ushr 18 and eightBitsMask).toByte()
        b[i + 18] = (_n4 and eightBitsMask).toByte()
        b[i + 17] = (_n4 ushr 8 and eightBitsMask).toByte()
        b[i + 16] = (_n4 ushr 16 and eightBitsMask).toByte()
        b[i + 15] = (_n4 ushr 24 and twoBitsMask or (_n5 and sixBitsMask shl 2)).toByte()
        b[i + 14] = (_n5 ushr 6 and eightBitsMask).toByte()
        b[i + 13] = (_n5 ushr 14 and eightBitsMask).toByte()
        b[i + 12] = (_n5 ushr 22 and fourBitsMask or (_n6 and fourBitsMask shl 4)).toByte()
        b[i + 11] = (_n6 ushr 4 and eightBitsMask).toByte()
        b[i + 10] = (_n6 ushr 12 and eightBitsMask).toByte()
        b[i + 9] = (_n6 ushr 20 and sixBitsMask or (_n7 and twoBitsMask shl 6)).toByte()
        b[i + 8] = (_n7 ushr 2 and eightBitsMask).toByte()
        b[i + 7] = (_n7 ushr 10 and eightBitsMask).toByte()
        b[i + 6] = (_n7 ushr 18 and eightBitsMask).toByte()
        b[i + 5] = (_n8 and eightBitsMask).toByte()
        b[i + 4] = (_n8 ushr 8 and eightBitsMask).toByte()
        b[i + 3] = (_n8 ushr 16 and eightBitsMask).toByte()
        b[i + 2] = (_n8 ushr 24 and twoBitsMask or (_n9 and sixBitsMask shl 2)).toByte()
        b[i + 1] = (_n9 ushr 6 and eightBitsMask).toByte()
        b[i + 0] = (_n9 ushr 14 and eightBitsMask).toByte()
    }

    // Bytes unpacks the field value to a 32-byte big-endian value.  See PutBytes
    // for a variant that allows the a buffer to be passed which can be useful to
    // to cut down on the number of allocations by allowing the caller to reuse a
    // buffer.
    //
    // The field value must be normalized for this function to return correct
    // result.
    fun bytes(): ByteArray {
        val b = ByteArray(32)
        putBytes(b, 0)
        return b
    } // The value can only be zero if no bits are set in any of the words.

    operator fun unaryMinus(): FieldVal {
        return negate(1)
    }

    // Negate negates the field value.  The existing field value is modified.  The
    // caller must provide the magnitude of the field value for a correct result.
    //
    // The field value is returned to support chaining.  This enables syntax like:
    // Negate().AddInt(1) so that f = -f + 1.
    fun negate(magnitude: Int): FieldVal {
        // Negation in the field is just the prime minus the value.  However,
        // in order to allow negation against a field value without having to
        // normalize/reduce it first, multiply by the magnitude (that is how
        // "far" away it is from the normalized value) to adjust.  Also, since
        // negating a value pushes it one more order of magnitude away from the
        // normalized range, add 1 to compensate.
        //
        // For some intuition here, imagine you're performing mod 12 arithmetic
        // (picture a clock) and you are negating the number 7.  So you start at
        // 12 (which is of course 0 under mod 12) and count backwards (left on
        // the clock) 7 times to arrive at 5.  Notice this is just 12-7 = 5.
        // Now, assume you're starting with 19, which is a number that is
        // already larger than the modulus and congruent to 7 (mod 12).  When a
        // value is already in the desired range, its magnitude is 1.  Since 19
        // is an additional "step", its magnitude (mod 12) is 2.  Since any
        // multiple of the modulus is conguent to zero (mod m), the answer can
        // be shortcut by simply mulplying the magnitude by the modulus and
        // subtracting.  Keeping with the example, this would be (2*12)-19 = 5.
        val n0 = (magnitude + 1) * fieldPrimeWordZero - this._n0
        val n1 = (magnitude + 1) * fieldPrimeWordOne - this._n1
        val n2 = (magnitude + 1) * fieldBaseMask - this._n2
        val n3 = (magnitude + 1) * fieldBaseMask - this._n3
        val n4 = (magnitude + 1) * fieldBaseMask - this._n4
        val n5 = (magnitude + 1) * fieldBaseMask - this._n5
        val n6 = (magnitude + 1) * fieldBaseMask - this._n6
        val n7 = (magnitude + 1) * fieldBaseMask - this._n7
        val n8 = (magnitude + 1) * fieldBaseMask - this._n8
        val n9 = (magnitude + 1) * fieldMSBMask - this._n9
        return FieldVal(n0, n1, n2, n3, n4, n5, n6, n7, n8, n9)
    }

    // square squares the passed value and stores the result in   Note that
    // this function can overflow if multiplying any of the individual words
    // exceeds a max (int).  In practice, this means the magnitude of the field
    // being squred must be a max of 8 to prevent overflow.
    //
    // The field value is returned to support chaining.  This enables syntax like:
    // f3.square(f).mul(f) so that f3 = f^2 * f = f^3.
    fun square(): FieldVal {
        // This could be done with a couple of for loops and an array to store
        // the intermediate terms, but this unrolled version is significantly
        // faster.

        // Terms for 2^(fieldBase*0).
        var m = (_n0.toLong() and 0xffffffffL) * (_n0.toLong() and 0xffffffffL)
        var t0 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*1).
        m = (m ushr fieldBase) + 2 * (_n0.toLong() and 0xffffffffL) * (_n1.toLong() and 0xffffffffL)
        var t1 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*2).
        m = (m ushr fieldBase) + 2 * (_n0.toLong() and 0xffffffffL) * (_n2.toLong() and 0xffffffffL) + (_n1.toLong() and 0xffffffffL) * (_n1.toLong() and 0xffffffffL)
        var t2 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*3).
        m = (m ushr fieldBase) + 2 * (_n0.toLong() and 0xffffffffL) * (_n3.toLong() and 0xffffffffL) + 2 * (_n1.toLong() and 0xffffffffL) * (_n2.toLong() and 0xffffffffL)
        var t3 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*4).
        m = (m ushr fieldBase) + 2 * (_n0.toLong() and 0xffffffffL) * (_n4.toLong() and 0xffffffffL) + 2 * (_n1.toLong() and 0xffffffffL) * (_n3.toLong() and 0xffffffffL) +
            (_n2.toLong() and 0xffffffffL) * (_n2.toLong() and 0xffffffffL)
        var t4 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*5).
        m = (m ushr fieldBase) + 2 * (_n0.toLong() and 0xffffffffL) * (_n5.toLong() and 0xffffffffL) + 2 * (_n1.toLong() and 0xffffffffL) * (_n4.toLong() and 0xffffffffL) +
            2 * (_n2.toLong() and 0xffffffffL) * (_n3.toLong() and 0xffffffffL)
        var t5 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*6).
        m = (m ushr fieldBase) + 2 * (_n0.toLong() and 0xffffffffL) * (_n6.toLong() and 0xffffffffL) + 2 * (_n1.toLong() and 0xffffffffL) * (_n5.toLong() and 0xffffffffL) +
            2 * (_n2.toLong() and 0xffffffffL) * (_n4.toLong() and 0xffffffffL) + (_n3.toLong() and 0xffffffffL) * (_n3.toLong() and 0xffffffffL)
        var t6 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*7).
        m = (m ushr fieldBase) + 2 * (_n0.toLong() and 0xffffffffL) * (_n7.toLong() and 0xffffffffL) + 2 * (_n1.toLong() and 0xffffffffL) * (_n6.toLong() and 0xffffffffL) +
            2 * (_n2.toLong() and 0xffffffffL) * (_n5.toLong() and 0xffffffffL) + 2 * (_n3.toLong() and 0xffffffffL) * (_n4.toLong() and 0xffffffffL)
        var t7 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*8).
        m = (m ushr fieldBase) + 2 * (_n0.toLong() and 0xffffffffL) * (_n8.toLong() and 0xffffffffL) + 2 * (_n1.toLong() and 0xffffffffL) * (_n7.toLong() and 0xffffffffL) +
            2 * (_n2.toLong() and 0xffffffffL) * (_n6.toLong() and 0xffffffffL) + 2 * (_n3.toLong() and 0xffffffffL) * (_n5.toLong() and 0xffffffffL) +
            (_n4.toLong() and 0xffffffffL) * (_n4.toLong() and 0xffffffffL)
        var t8 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*9).
        m = (m ushr fieldBase) + 2 * (_n0.toLong() and 0xffffffffL) * (_n9.toLong() and 0xffffffffL) + 2 * (_n1.toLong() and 0xffffffffL) * (_n8.toLong() and 0xffffffffL) +
            2 * (_n2.toLong() and 0xffffffffL) * (_n7.toLong() and 0xffffffffL) + 2 * (_n3.toLong() and 0xffffffffL) * (_n6.toLong() and 0xffffffffL) +
            2 * (_n4.toLong() and 0xffffffffL) * (_n5.toLong() and 0xffffffffL)
        var t9 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*10).
        m = (m ushr fieldBase) + 2 * (_n1.toLong() and 0xffffffffL) * (_n9.toLong() and 0xffffffffL) + 2 * (_n2.toLong() and 0xffffffffL) * (_n8.toLong() and 0xffffffffL) +
            2 * (_n3.toLong() and 0xffffffffL) * (_n7.toLong() and 0xffffffffL) + 2 * (_n4.toLong() and 0xffffffffL) * (_n6.toLong() and 0xffffffffL) +
            (_n5.toLong() and 0xffffffffL) * (_n5.toLong() and 0xffffffffL)
        val t10 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*11).
        m = (m ushr fieldBase) + 2 * (_n2.toLong() and 0xffffffffL) * (_n9.toLong() and 0xffffffffL) + 2 * (_n3.toLong() and 0xffffffffL) * (_n8.toLong() and 0xffffffffL) +
            2 * (_n4.toLong() and 0xffffffffL) * (_n7.toLong() and 0xffffffffL) + 2 * (_n5.toLong() and 0xffffffffL) * (_n6.toLong() and 0xffffffffL)
        val t11 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*12).
        m = (m ushr fieldBase) + 2 * (_n3.toLong() and 0xffffffffL) * (_n9.toLong() and 0xffffffffL) + 2 * (_n4.toLong() and 0xffffffffL) * (_n8.toLong() and 0xffffffffL) +
            2 * (_n5.toLong() and 0xffffffffL) * (_n7.toLong() and 0xffffffffL) + (_n6.toLong() and 0xffffffffL) * (_n6.toLong() and 0xffffffffL)
        val t12 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*13).
        m = (m ushr fieldBase) + 2 * (_n4.toLong() and 0xffffffffL) * (_n9.toLong() and 0xffffffffL) + 2 * (_n5.toLong() and 0xffffffffL) * (_n8.toLong() and 0xffffffffL) +
            2 * (_n6.toLong() and 0xffffffffL) * (_n7.toLong() and 0xffffffffL)
        val t13 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*14).
        m = (m ushr fieldBase) + 2 * (_n5.toLong() and 0xffffffffL) * (_n9.toLong() and 0xffffffffL) + 2 * (_n6.toLong() and 0xffffffffL) * (_n8.toLong() and 0xffffffffL) +
            (_n7.toLong() and 0xffffffffL) * (_n7.toLong() and 0xffffffffL)
        val t14 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*15).
        m = (m ushr fieldBase) + 2 * (_n6.toLong() and 0xffffffffL) * (_n9.toLong() and 0xffffffffL) + 2 * (_n7.toLong() and 0xffffffffL) * (_n8.toLong() and 0xffffffffL)
        val t15 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*16).
        m = (m ushr fieldBase) + 2 * (_n7.toLong() and 0xffffffffL) * (_n9.toLong() and 0xffffffffL) + (_n8.toLong() and 0xffffffffL) * (_n8.toLong() and 0xffffffffL)
        val t16 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*17).
        m = (m ushr fieldBase) + 2 * (_n8.toLong() and 0xffffffffL) * (_n9.toLong() and 0xffffffffL)
        val t17 = m and fieldBaseMask.toLong()

        // Terms for 2^(fieldBase*18).
        m = (m ushr fieldBase) + (_n9.toLong() and 0xffffffffL) * (_n9.toLong() and 0xffffffffL)
        val t18 = m and fieldBaseMask.toLong()

        // What's left is for 2^(fieldBase*19).
        val t19 = m ushr fieldBase

        // At this point, all of the terms are grouped into their respective
        // base.
        //
        // Per [HAC] section 14.3.4: Reduction method of moduli of special form,
        // when the modulus is of the special form m = b^t - c, highly efficient
        // reduction can be achieved per the provided algorithm.
        //
        // The secp256k1 prime is equivalent to 2^256 - 4294968273, so it fits
        // this criteria.
        //
        // 4294968273 in field representation (base 2^26) is:
        // n[0] = 977
        // n[1] = 64
        // That is to say (2^26 * 64) + 977 = 4294968273
        //
        // Since each word is in base 26, the upper terms (t10 and up) start
        // at 260 bits (versus the final desired range of 256 bits), so the
        // field representation of 'c' from above needs to be adjusted for the
        // extra 4 bits by multiplying it by 2^4 = 16.  4294968273 * 16 =
        // 68719492368.  Thus, the adjusted field representation of 'c' is:
        // n[0] = 977 * 16 = 15632
        // n[1] = 64 * 16 = 1024
        // That is to say (2^26 * 1024) + 15632 = 68719492368
        //
        // To reduce the final term, t19, the entire 'c' value is needed instead
        // of only n[0] because there are no more terms left to handle n[1].
        // This means there might be some magnitude left in the upper bits that
        // is handled below.
        m = t0 + t10 * 15632
        t0 = m and fieldBaseMask.toLong()
        m = (m ushr fieldBase) + t1 + t10 * 1024 + t11 * 15632
        t1 = m and fieldBaseMask.toLong()
        m = (m ushr fieldBase) + t2 + t11 * 1024 + t12 * 15632
        t2 = m and fieldBaseMask.toLong()
        m = (m ushr fieldBase) + t3 + t12 * 1024 + t13 * 15632
        t3 = m and fieldBaseMask.toLong()
        m = (m ushr fieldBase) + t4 + t13 * 1024 + t14 * 15632
        t4 = m and fieldBaseMask.toLong()
        m = (m ushr fieldBase) + t5 + t14 * 1024 + t15 * 15632
        t5 = m and fieldBaseMask.toLong()
        m = (m ushr fieldBase) + t6 + t15 * 1024 + t16 * 15632
        t6 = m and fieldBaseMask.toLong()
        m = (m ushr fieldBase) + t7 + t16 * 1024 + t17 * 15632
        t7 = m and fieldBaseMask.toLong()
        m = (m ushr fieldBase) + t8 + t17 * 1024 + t18 * 15632
        t8 = m and fieldBaseMask.toLong()
        m = (m ushr fieldBase) + t9 + t18 * 1024 + t19 * 68719492368L
        t9 = m and fieldMSBMask.toLong()
        m = m ushr fieldMSBBits

        // At this point, if the magnitude is greater than 0, the overall value
        // is greater than the max possible 256-bit value.  In particular, it is
        // "how many times larger" than the max value it is.
        //
        // The algorithm presented in [HAC] section 14.3.4 repeats until the
        // quotient is zero.  However, due to the above, we already know at
        // least how many times we would need to repeat as it's the value
        // currently in m.  Thus we can simply multiply the magnitude by the
        // field representation of the prime and do a single iteration.  Notice
        // that nothing will be changed when the magnitude is zero, so we could
        // skip this in that case, however always running regardless allows it
        // to run in constant time.  The final result will be in the range
        // 0 <= result <= prime + (2^64 - c), so it is guaranteed to have a
        // magnitude of 1, but it is denormalized.
        var v = t0 + m * 977
        val n0 = (v and fieldBaseMask.toLong()).toInt()
        v = (v ushr fieldBase) + t1 + m * 64
        val n1 = (v and fieldBaseMask.toLong()).toInt()
        val n2 = ((v ushr fieldBase) + t2).toInt()
        val n3 = t3.toInt()
        val n4 = t4.toInt()
        val n5 = t5.toInt()
        val n6 = t6.toInt()
        val n7 = t7.toInt()
        val n8 = t8.toInt()
        val n9 = t9.toInt()
        return FieldVal(n0, n1, n2, n3, n4, n5, n6, n7, n8, n9)
    }

    // Inverse finds the modular multiplicative inverse of the field value.  The
    // existing field value is modified.
    //
    // The field value is returned to support chaining.  This enables syntax like:
    // Inverse().mul(f2) so that f = f^-1 * f2.
    fun inverse(): FieldVal {
        // Fermat's little theorem states that for a nonzero number a and prime
        // prime p, a^(p-1) = 1 (mod p).  Since the multipliciative inverse is
        // a*b = 1 (mod p), it follows that b = a*a^(p-2) = a^(p-1) = 1 (mod p).
        // Thus, a^(p-2) is the multiplicative inverse.
        //
        // In order to efficiently compute a^(p-2), p-2 needs to be split into
        // a sequence of squares and multipications that minimizes the number of
        // multiplications needed (since they are more costly than squarings).
        // Intermediate results are saved and reused as well.
        //
        // The secp256k1 prime - 2 is 2^256 - 4294968275.
        //
        // This has a cost of 258 field squarings and 33 field multiplications.
        val a2 = this.square()
        val a3 = a2 * this
        val a4 = a2.square()
        val a10 = a4.square() * a2
        val a11 = a10 * this
        val a21 = a10 * a11
        val a42 = a21.square()
        val a45 = a42 * a3
        val a63 = a42 * a21
        val a1019 = a63.square().square().square().square() * a11
        val a1023 = a1019 * a4

        var f = a63 // f = a^(2^6 - 1)
        f = f.square().square().square().square().square() // f = a^(2^11 - 32)
        f = f.square().square().square().square().square() // f = a^(2^16 - 1024)
        f *= a1023 // f = a^(2^16 - 1)
        f = f.square().square().square().square().square() // f = a^(2^21 - 32)
        f = f.square().square().square().square().square() // f = a^(2^26 - 1024)
        f *= a1023 // f = a^(2^26 - 1)
        f = f.square().square().square().square().square() // f = a^(2^31 - 32)
        f = f.square().square().square().square().square() // f = a^(2^36 - 1024)
        f *= a1023 // f = a^(2^36 - 1)
        f = f.square().square().square().square().square() // f = a^(2^41 - 32)
        f = f.square().square().square().square().square() // f = a^(2^46 - 1024)
        f *= a1023 // f = a^(2^46 - 1)
        f = f.square().square().square().square().square() // f = a^(2^51 - 32)
        f = f.square().square().square().square().square() // f = a^(2^56 - 1024)
        f *= a1023 // f = a^(2^56 - 1)
        f = f.square().square().square().square().square() // f = a^(2^61 - 32)
        f = f.square().square().square().square().square() // f = a^(2^66 - 1024)
        f *= a1023 // f = a^(2^66 - 1)
        f = f.square().square().square().square().square() // f = a^(2^71 - 32)
        f = f.square().square().square().square().square() // f = a^(2^76 - 1024)
        f *= a1023 // f = a^(2^76 - 1)
        f = f.square().square().square().square().square() // f = a^(2^81 - 32)
        f = f.square().square().square().square().square() // f = a^(2^86 - 1024)
        f *= a1023 // f = a^(2^86 - 1)
        f = f.square().square().square().square().square() // f = a^(2^91 - 32)
        f = f.square().square().square().square().square() // f = a^(2^96 - 1024)
        f *= a1023 // f = a^(2^96 - 1)
        f = f.square().square().square().square().square() // f = a^(2^101 - 32)
        f = f.square().square().square().square().square() // f = a^(2^106 - 1024)
        f *= a1023 // f = a^(2^106 - 1)
        f = f.square().square().square().square().square() // f = a^(2^111 - 32)
        f = f.square().square().square().square().square() // f = a^(2^116 - 1024)
        f *= a1023 // f = a^(2^116 - 1)
        f = f.square().square().square().square().square() // f = a^(2^121 - 32)
        f = f.square().square().square().square().square() // f = a^(2^126 - 1024)
        f *= a1023 // f = a^(2^126 - 1)
        f = f.square().square().square().square().square() // f = a^(2^131 - 32)
        f = f.square().square().square().square().square() // f = a^(2^136 - 1024)
        f *= a1023 // f = a^(2^136 - 1)
        f = f.square().square().square().square().square() // f = a^(2^141 - 32)
        f = f.square().square().square().square().square() // f = a^(2^146 - 1024)
        f *= a1023 // f = a^(2^146 - 1)
        f = f.square().square().square().square().square() // f = a^(2^151 - 32)
        f = f.square().square().square().square().square() // f = a^(2^156 - 1024)
        f *= a1023 // f = a^(2^156 - 1)
        f = f.square().square().square().square().square() // f = a^(2^161 - 32)
        f = f.square().square().square().square().square() // f = a^(2^166 - 1024)
        f *= a1023 // f = a^(2^166 - 1)
        f = f.square().square().square().square().square() // f = a^(2^171 - 32)
        f = f.square().square().square().square().square() // f = a^(2^176 - 1024)
        f *= a1023 // f = a^(2^176 - 1)
        f = f.square().square().square().square().square() // f = a^(2^181 - 32)
        f = f.square().square().square().square().square() // f = a^(2^186 - 1024)
        f *= a1023 // f = a^(2^186 - 1)
        f = f.square().square().square().square().square() // f = a^(2^191 - 32)
        f = f.square().square().square().square().square() // f = a^(2^196 - 1024)
        f *= a1023 // f = a^(2^196 - 1)
        f = f.square().square().square().square().square() // f = a^(2^201 - 32)
        f = f.square().square().square().square().square() // f = a^(2^206 - 1024)
        f *= a1023 // f = a^(2^206 - 1)
        f = f.square().square().square().square().square() // f = a^(2^211 - 32)
        f = f.square().square().square().square().square() // f = a^(2^216 - 1024)
        f *= a1023 // f = a^(2^216 - 1)
        f = f.square().square().square().square().square() // f = a^(2^221 - 32)
        f = f.square().square().square().square().square() // f = a^(2^226 - 1024)
        f *= a1019 // f = a^(2^226 - 5)
        f = f.square().square().square().square().square() // f = a^(2^231 - 160)
        f = f.square().square().square().square().square() // f = a^(2^236 - 5120)
        f *= a1023 // f = a^(2^236 - 4097)
        f = f.square().square().square().square().square() // f = a^(2^241 - 131104)
        f = f.square().square().square().square().square() // f = a^(2^246 - 4195328)
        f *= a1023 // f = a^(2^246 - 4194305)
        f = f.square().square().square().square().square() // f = a^(2^251 - 134217760)
        f = f.square().square().square().square().square() // f = a^(2^256 - 4294968320)
        return f * a45 // f = a^(2^256 - 4294968275) = a^(p-2)
    }

    // squareRootVal either calculates the square root of the passed value when it
    // exists or the square root of the negation of the value when it does not exist
    // and stores the result in f in constant time.  The return flag is true when
    // the calculated square root is for the passed value itself and false when it
    // is for its negation.
    //
    // Note that this function can overflow if multiplying any of the individual
    // words exceeds a max uint32.  In practice, this means the magnitude of the
    // field must be a max of 8 to prevent overflow.  The magnitude of the result
    // will be 1.
    //
    // Preconditions:
    //   - The input field value MUST have a max magnitude of 8
    // Output Normalized: No
    // Output Max Magnitude: 1
    fun sqrt(): Tuple2<FieldVal, Boolean> {
        // This uses the Tonelli-Shanks method for calculating the square root of
        // the value when it exists.  The key principles of the method follow.
        //
        // Fermat's little theorem states that for a nonzero number 'a' and prime
        // 'p', a^(p-1) ≡ 1 (mod p).
        //
        // Further, Euler's criterion states that an integer 'a' has a square root
        // (aka is a quadratic residue) modulo a prime if a^((p-1)/2) ≡ 1 (mod p)
        // and, conversely, when it does NOT have a square root (aka 'a' is a
        // non-residue) a^((p-1)/2) ≡ -1 (mod p).
        //
        // This can be seen by considering that Fermat's little theorem can be
        // written as (a^((p-1)/2) - 1)(a^((p-1)/2) + 1) ≡ 0 (mod p).  Therefore,
        // one of the two factors must be 0.  Then, when a ≡ x^2 (aka 'a' is a
        // quadratic residue), (x^2)^((p-1)/2) ≡ x^(p-1) ≡ 1 (mod p) which implies
        // the first factor must be zero.  Finally, per Lagrange's theorem, the
        // non-residues are the only remaining possible solutions and thus must make
        // the second factor zero to satisfy Fermat's little theorem implying that
        // a^((p-1)/2) ≡ -1 (mod p) for that case.
        //
        // The Tonelli-Shanks method uses these facts along with factoring out
        // powers of two to solve a congruence that results in either the solution
        // when the square root exists or the square root of the negation of the
        // value when it does not.  In the case of primes that are ≡ 3 (mod 4), the
        // possible solutions are r = ±a^((p+1)/4) (mod p).  Therefore, either r^2 ≡
        // a (mod p) is true in which case ±r are the two solutions, or r^2 ≡ -a
        // (mod p) in which case 'a' is a non-residue and there are no solutions.
        //
        // The secp256k1 prime is ≡ 3 (mod 4), so this result applies.
        //
        // In other words, calculate a^((p+1)/4) and then square it and check it
        // against the original value to determine if it is actually the square
        // root.
        //
        // In order to efficiently compute a^((p+1)/4), (p+1)/4 needs to be split
        // into a sequence of squares and multiplications that minimizes the number
        // of multiplications needed (since they are more costly than squarings).
        //
        // The secp256k1 prime + 1 / 4 is 2^254 - 2^30 - 244.  In binary, that is:
        //
        // 00111111 11111111 11111111 11111111
        // 11111111 11111111 11111111 11111111
        // 11111111 11111111 11111111 11111111
        // 11111111 11111111 11111111 11111111
        // 11111111 11111111 11111111 11111111
        // 11111111 11111111 11111111 11111111
        // 11111111 11111111 11111111 11111111
        // 10111111 11111111 11111111 00001100
        //
        // Notice that can be broken up into three windows of consecutive 1s (in
        // order of least to most signifcant) as:
        //
        //   6-bit window with two bits set (bits 4, 5, 6, 7 unset)
        //   23-bit window with 22 bits set (bit 30 unset)
        //   223-bit window with all 223 bits set
        //
        // Thus, the groups of 1 bits in each window forms the set:
        // S = {2, 22, 223}.
        //
        // The strategy is to calculate a^(2^n - 1) for each grouping via an
        // addition chain with a sliding window.
        //
        // The addition chain used is (credits to Peter Dettman):
        // (0,0),(1,0),(2,2),(3,2),(4,1),(5,5),(6,6),(7,7),(8,8),(9,7),(10,2)
        // => 2^1 2^[2] 2^3 2^6 2^9 2^11 2^[22] 2^44 2^88 2^176 2^220 2^[223]
        //
        // This has a cost of 254 field squarings and 13 field multiplications.
        val a = this
        val a2 = a.square() * a // a2 = a^(2^2 - 1)
        val a3 = a2.square() * a // a3 = a^(2^3 - 1)
        var a6 = a3.square().square().square() // a6 = a^(2^6 - 2^3)
        a6 *= a3 // a6 = a^(2^6 - 1)
        var a9 = a6.square().square().square() // a9 = a^(2^9 - 2^3)
        a9 *= a3 // a9 = a^(2^9 - 1)
        var a11 = a9.square().square() // a11 = a^(2^11 - 2^2)
        a11 *= a2 // a11 = a^(2^11 - 1)
        var a22 = a11.square().square().square().square().square() // a22 = a^(2^16 - 2^5)
        a22 = a22.square().square().square().square().square() // a22 = a^(2^21 - 2^10)
        a22 = a22.square() // a22 = a^(2^22 - 2^11)
        a22 *= a11 // a22 = a^(2^22 - 1)
        var a44 = a22.square().square().square().square().square() // a44 = a^(2^27 - 2^5)
        a44 = a44.square().square().square().square().square() // a44 = a^(2^32 - 2^10)
        a44 = a44.square().square().square().square().square() // a44 = a^(2^37 - 2^15)
        a44 = a44.square().square().square().square().square() // a44 = a^(2^42 - 2^20)
        a44 = a44.square().square() // a44 = a^(2^44 - 2^22)
        a44 *= a22 // a44 = a^(2^44 - 1)
        var a88 = a44.square().square().square().square().square() // a88 = a^(2^49 - 2^5)
        a88 = a88.square().square().square().square().square() // a88 = a^(2^54 - 2^10)
        a88 = a88.square().square().square().square().square() // a88 = a^(2^59 - 2^15)
        a88 = a88.square().square().square().square().square() // a88 = a^(2^64 - 2^20)
        a88 = a88.square().square().square().square().square() // a88 = a^(2^69 - 2^25)
        a88 = a88.square().square().square().square().square() // a88 = a^(2^74 - 2^30)
        a88 = a88.square().square().square().square().square() // a88 = a^(2^79 - 2^35)
        a88 = a88.square().square().square().square().square() // a88 = a^(2^84 - 2^40)
        a88 = a88.square().square().square().square() // a88 = a^(2^88 - 2^44)
        a88 *= a44 // a88 = a^(2^88 - 1)
        var a176 = a88.square().square().square().square().square() // a176 = a^(2^93 - 2^5)
        a176 = a176.square().square().square().square().square() // a176 = a^(2^98 - 2^10)
        a176 = a176.square().square().square().square().square() // a176 = a^(2^103 - 2^15)
        a176 = a176.square().square().square().square().square() // a176 = a^(2^108 - 2^20)
        a176 = a176.square().square().square().square().square() // a176 = a^(2^113 - 2^25)
        a176 = a176.square().square().square().square().square() // a176 = a^(2^118 - 2^30)
        a176 = a176.square().square().square().square().square() // a176 = a^(2^123 - 2^35)
        a176 = a176.square().square().square().square().square() // a176 = a^(2^128 - 2^40)
        a176 = a176.square().square().square().square().square() // a176 = a^(2^133 - 2^45)
        a176 = a176.square().square().square().square().square() // a176 = a^(2^138 - 2^50)
        a176 = a176.square().square().square().square().square() // a176 = a^(2^143 - 2^55)
        a176 = a176.square().square().square().square().square() // a176 = a^(2^148 - 2^60)
        a176 = a176.square().square().square().square().square() // a176 = a^(2^153 - 2^65)
        a176 = a176.square().square().square().square().square() // a176 = a^(2^158 - 2^70)
        a176 = a176.square().square().square().square().square() // a176 = a^(2^163 - 2^75)
        a176 = a176.square().square().square().square().square() // a176 = a^(2^168 - 2^80)
        a176 = a176.square().square().square().square().square() // a176 = a^(2^173 - 2^85)
        a176 = a176.square().square().square() // a176 = a^(2^176 - 2^88)
        a176 *= a88 // a176 = a^(2^176 - 1)
        var a220 = a176.square().square().square().square().square() // a220 = a^(2^181 - 2^5)
        a220 = a220.square().square().square().square().square() // a220 = a^(2^186 - 2^10)
        a220 = a220.square().square().square().square().square() // a220 = a^(2^191 - 2^15)
        a220 = a220.square().square().square().square().square() // a220 = a^(2^196 - 2^20)
        a220 = a220.square().square().square().square().square() // a220 = a^(2^201 - 2^25)
        a220 = a220.square().square().square().square().square() // a220 = a^(2^206 - 2^30)
        a220 = a220.square().square().square().square().square() // a220 = a^(2^211 - 2^35)
        a220 = a220.square().square().square().square().square() // a220 = a^(2^216 - 2^40)
        a220 = a220.square().square().square().square() // a220 = a^(2^220 - 2^44)
        a220 *= a44 // a220 = a^(2^220 - 1)
        var a223 = a220.square().square().square() // a223 = a^(2^223 - 2^3)
        a223 *= a3 // a223 = a^(2^223 - 1)

        var r = a223.square().square().square().square().square() // f = a^(2^228 - 2^5)
        r = r.square().square().square().square().square() // f = a^(2^233 - 2^10)
        r = r.square().square().square().square().square() // f = a^(2^238 - 2^15)
        r = r.square().square().square().square().square() // f = a^(2^243 - 2^20)
        r = r.square().square().square() // f = a^(2^246 - 2^23)
        r *= a22 // f = a^(2^246 - 2^22 - 1)
        r = r.square().square().square().square().square() // f = a^(2^251 - 2^27 - 2^5)
        r = r.square() // f = a^(2^252 - 2^28 - 2^6)
        r *= a2 // f = a^(2^252 - 2^28 - 2^6 - 2^1 - 1)
        r = r.square().square() // f = a^(2^254 - 2^30 - 2^8 - 2^3 - 2^2)
        //                                                     //   = a^(2^254 - 2^30 - 244)
        //                                                     //   = a^((p+1)/4)
        val valid = r.square().normalize() == this.normalize()
        return Tuple(r, valid)
    }

    // IsGtOrEqPrimeMinusOrder returns whether or not the field value exceeds the
    // group order divided by 2 in constant time.
    //
    // Preconditions:
    //   - The field value MUST be normalized
    fun isGtOrEqPrimeMinusOrder(): Boolean {
        // The secp256k1 prime is equivalent to 2^256 - 4294968273 and the group
        // order is 2^256 - 432420386565659656852420866394968145599.  Thus,
        // the prime minus the group order is:
        // 432420386565659656852420866390673177326
        //
        // In hex that is:
        // 0x00000000 00000000 00000000 00000001 45512319 50b75fc4 402da172 2fc9baee
        //
        // Converting that to field representation (base 2^26) is:
        //
        // n[0] = 0x03c9baee
        // n[1] = 0x03685c8b
        // n[2] = 0x01fc4402
        // n[3] = 0x006542dd
        // n[4] = 0x01455123
        //
        // This can be verified with the following test code:
        //   pMinusN := new(big.Int).Sub(curveParams.P, curveParams.N)
        //   var fv FieldVal
        //   fv.SetByteSlice(pMinusN.Bytes())
        //   t.Logf("%x", fv.n)
        //
        //   Outputs: [3c9baee 3685c8b 1fc4402 6542dd 1455123 0 0 0 0 0]

        // The intuition here is that the value is greater than field prime minus
        // the group order if one of the higher individual words is greater than the
        // corresponding word and all higher words in the value are equal.
        var result = constantTimeGreater(_n9.toUInt(), pMinusNWordNine)
        var highWordsEqual = constantTimeEq(_n9.toUInt(), pMinusNWordNine)
        result = result or (highWordsEqual and constantTimeGreater(_n8.toUInt(), pMinusNWordEight))
        highWordsEqual = highWordsEqual and constantTimeEq(_n8.toUInt(), pMinusNWordEight)
        result = result or (highWordsEqual and constantTimeGreater(_n7.toUInt(), pMinusNWordSeven))
        highWordsEqual = highWordsEqual and constantTimeEq(_n7.toUInt(), pMinusNWordSeven)
        result = result or (highWordsEqual and constantTimeGreater(_n6.toUInt(), pMinusNWordSix))
        highWordsEqual = highWordsEqual and constantTimeEq(_n6.toUInt(), pMinusNWordSix)
        result = result or (highWordsEqual and constantTimeGreater(_n5.toUInt(), pMinusNWordFive))
        highWordsEqual = highWordsEqual and constantTimeEq(_n5.toUInt(), pMinusNWordFive)
        result = result or (highWordsEqual and constantTimeGreater(_n4.toUInt(), pMinusNWordFour))
        highWordsEqual = highWordsEqual and constantTimeEq(_n4.toUInt(), pMinusNWordFour)
        result = result or (highWordsEqual and constantTimeGreater(_n3.toUInt(), pMinusNWordThree))
        highWordsEqual = highWordsEqual and constantTimeEq(_n3.toUInt(), pMinusNWordThree)
        result = result or (highWordsEqual and constantTimeGreater(_n2.toUInt(), pMinusNWordTwo))
        highWordsEqual = highWordsEqual and constantTimeEq(_n2.toUInt(), pMinusNWordTwo)
        result = result or (highWordsEqual and constantTimeGreater(_n1.toUInt(), pMinusNWordOne))
        highWordsEqual = highWordsEqual and constantTimeEq(_n1.toUInt(), pMinusNWordOne)
        result = result or (highWordsEqual and constantTimeGreaterOrEq(_n0.toUInt(), pMinusNWordZero))
        return result != 0u
    }

    override fun hashCode(): Int {
        var result = 1
        result = 31 * result + _n0
        result = 31 * result + _n1
        result = 31 * result + _n2
        result = 31 * result + _n3
        result = 31 * result + _n4
        result = 31 * result + _n5
        result = 31 * result + _n6
        result = 31 * result + _n7
        result = 31 * result + _n8
        result = 31 * result + _n9
        return result
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
        if (other !is FieldVal) {
            return super.equals(other)
        }
        val bits = _n0 xor other._n0 or (_n1 xor other._n1) or (_n2 xor other._n2) or
            (_n3 xor other._n3) or (_n4 xor other._n4) or (_n5 xor other._n5) or
            (_n6 xor other._n6) or (_n7 xor other._n7) or (_n8 xor other._n8) or
            (_n9 xor other._n9)
        return bits == 0
    }

    override fun toString(): String {
        return Hex.encode(normalize().bytes())
    }

    companion object {
        private const val twoBitsMask = 0x3
        private const val fourBitsMask = 0xf
        private const val sixBitsMask = 0x3f
        private const val eightBitsMask = 0xff

        // fieldWords is the number of words used to internally represent the
        // 256-bit value.
        private const val fieldWords = 10

        // fieldBase is the exponent used to form the numeric base of each word.
        // 2^(fieldBase*i) where i is the word position.
        private const val fieldBase = 26

        // fieldOverflowBits is the minimum number of "overflow" bits for each
        // word in the field value.
        private const val fieldOverflowBits = 32 - fieldBase

        // fieldBaseMask is the mask for the bits in each word needed to
        // represent the numeric base of each word (except the most significant
        // word).
        private const val fieldBaseMask = (1 shl fieldBase) - 1

        // fieldMSBBits is the number of bits in the most significant word used
        // to represent the value.
        private const val fieldMSBBits = 256 - fieldBase * (fieldWords - 1)

        // fieldMSBMask is the mask for the bits in the most significant word
        // needed to represent the value.
        private const val fieldMSBMask = (1 shl fieldMSBBits) - 1

        // These fields provide convenient access to each of the words of the
        // secp256k1 prime in the internal field representation to improve code
        // readability.
        private const val fieldPrimeWordZero = 0x03fffc2f
        private const val fieldPrimeWordOne = 0x03ffffbf
        private const val fieldPrimeWordTwo = 0x03ffffff
        private const val fieldPrimeWordThree = 0x03ffffff
        private const val fieldPrimeWordFour = 0x03ffffff
        private const val fieldPrimeWordFive = 0x03ffffff
        private const val fieldPrimeWordSix = 0x03ffffff
        private const val fieldPrimeWordSeven = 0x03ffffff
        private const val fieldPrimeWordEight = 0x03ffffff
        private const val fieldPrimeWordNine = 0x003fffff

        private const val pMinusNWordZero = 0x03c9baeeu
        private const val pMinusNWordOne = 0x03685c8bu
        private const val pMinusNWordTwo = 0x01fc4402u
        private const val pMinusNWordThree = 0x006542ddu
        private const val pMinusNWordFour = 0x01455123u
        private const val pMinusNWordFive = 0x00000000u
        private const val pMinusNWordSix = 0x00000000u
        private const val pMinusNWordSeven = 0x00000000u
        private const val pMinusNWordEight = 0x00000000u
        private const val pMinusNWordNine = 0x00000000u

        // fieldQBytes is the value Q = (P+1)/4 for the secp256k1 prime P. This
        // value is used to efficiently compute the square root of values in the
        // field via exponentiation. The value of Q in hex is:
        //
        //   Q = 3fffffffffffffffffffffffffffffffffffffffffffffffffffffffbfffff0c
        private val fieldQBytes = Hex.decode("3fffffffffffffffffffffffffffffffffffffffffffffffffffffffbfffff0c")

        val Zero = FieldVal(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

        // fieldOne is simply the integer 1 in field representation.  It is
        // used to avoid needing to create it multiple times during the internal
        // arithmetic.
        val One = FieldVal(1, 0, 0, 0, 0, 0, 0, 0, 0, 0)

        // SetInt sets the field value to the passed integer.  This is a convenience
        // function since it is fairly common to perform some arithmetic with small
        // native integers.
        //
        // The field value is returned to support chaining.  This enables syntax such
        // as f = new(fieldVal).SetInt(2).mul(f2) so that f = 2 * f2.
        fun setInt(ui: Int): FieldVal {
            return FieldVal(ui, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        }

        // SetHex decodes the passed big-endian hex string into the internal field value
        // representation.  Only the first 32-bytes are used.
        //
        // The field value is returned to support chaining.  This enables syntax like:
        // f = new(fieldVal).SetHex("0abc").Add(1) so that f = 0x0abc + 1
        fun fromHex(hexString: String): FieldVal {
            return if (hexString.length % 2 != 0) {
                setByteSlice(Hex.decodeOrThrow("0$hexString"))
            } else {
                setByteSlice(Hex.decodeOrThrow(hexString))
            }
        }

        // SetByteSlice interprets the provided slice as a 256-bit big-endian unsigned
        // integer (meaning it is truncated to the first 32 bytes), packs it into the
        // internal field value representation, and returns the updated field value.
        //
        // Note that since passing a slice with more than 32 bytes is truncated, it is
        // possible that the truncated value is less than the field prime.  It is up to
        // the caller to decide whether it needs to provide numbers of the appropriate
        // size or if it is acceptable to use this function with the described
        // truncation behavior.
        //
        // The field value is returned to support chaining.  This enables syntax like:
        // f = new(fieldVal).SetByteSlice(byteSlice)
        fun setByteSlice(bytes: ByteArray): FieldVal {
            val b32 = ByteArray(32)
            System.arraycopy(bytes, 0, b32, max(0, 32 - bytes.size), min(32, bytes.size))
            return setBytes(b32)
        }

        // SetBytes packs the passed 32-byte big-endian value into the internal field
        // value representation.
        //
        // The field value is returned to support chaining.  This enables syntax like:
        // f = new(fieldVal).SetBytes(byteArray).mul(f2) so that f = ba * f2.
        fun setBytes(b: ByteArray): FieldVal {
            // Pack the 256 total bits across the 10 (int) words with a max of
            // 26-bits per word.  This could be done with a couple of for loops,
            // but this unrolled version is significantly faster.  Benchmarks show
            // this is about 34 times faster than the variant which uses loops.
            val n0 = (b[31].toInt() and 0xff) or ((b[30].toInt() and 0xff) shl 8) or ((b[29].toInt() and 0xff) shl 16) or ((b[28].toInt() and 0xff) and twoBitsMask shl 24)
            val n1 = (b[28].toInt() and 0xff) ushr 2 or ((b[27].toInt() and 0xff) shl 6) or ((b[26].toInt() and 0xff) shl 14) or ((b[25].toInt() and 0xff) and fourBitsMask shl 22)
            val n2 = (b[25].toInt() and 0xff) ushr 4 or ((b[24].toInt() and 0xff) shl 4) or ((b[23].toInt() and 0xff) shl 12) or ((b[22].toInt() and 0xff) and sixBitsMask shl 20)
            val n3 = (b[22].toInt() and 0xff) ushr 6 or ((b[21].toInt() and 0xff) shl 2) or ((b[20].toInt() and 0xff) shl 10) or ((b[19].toInt() and 0xff) shl 18)
            val n4 = (b[18].toInt() and 0xff) or ((b[17].toInt() and 0xff) shl 8) or ((b[16].toInt() and 0xff) shl 16) or ((b[15].toInt() and 0xff) and twoBitsMask shl 24)
            val n5 = (b[15].toInt() and 0xff) ushr 2 or ((b[14].toInt() and 0xff) shl 6) or ((b[13].toInt() and 0xff) shl 14) or ((b[12].toInt() and 0xff) and fourBitsMask shl 22)
            val n6 = (b[12].toInt() and 0xff) ushr 4 or ((b[11].toInt() and 0xff) shl 4) or ((b[10].toInt() and 0xff) shl 12) or ((b[9].toInt() and 0xff) and sixBitsMask shl 20)
            val n7 = (b[9].toInt() and 0xff) ushr 6 or ((b[8].toInt() and 0xff) shl 2) or ((b[7].toInt() and 0xff) shl 10) or ((b[6].toInt() and 0xff) shl 18)
            val n8 = (b[5].toInt() and 0xff) or ((b[4].toInt() and 0xff) shl 8) or ((b[3].toInt() and 0xff) shl 16) or ((b[2].toInt() and 0xff) and twoBitsMask shl 24)
            val n9 = (b[2].toInt() and 0xff) ushr 2 or ((b[1].toInt() and 0xff) shl 6) or ((b[0].toInt() and 0xff) shl 14)
            return FieldVal(n0, n1, n2, n3, n4, n5, n6, n7, n8, n9)
        }
    }
}
