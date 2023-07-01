// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.secp256k1

// nafScalar represents a positive integer up to a maximum value of 2^256 - 1
// encoded in non-adjacent form.
//
// NAF is a signed-digit representation where each digit can be +1, 0, or -1.
//
// In order to efficiently encode that information, this type uses two arrays, a
// "positive" array where set bits represent the +1 signed digits and a
// "negative" array where set bits represent the -1 signed digits.  0 is
// represented by neither array having a bit set in that position.
//
// The Pos and Neg methods return the aforementioned positive and negative
// arrays, respectively.
data class NafScalar(
    // pos houses the positive portion of the representation.  An additional
    // byte is required for the positive portion because the NAF encoding can be
    // up to 1 bit longer than the normal binary encoding of the value.
    //
    // neg houses the negative portion of the representation.  Even though the
    // additional byte is not required for the negative portion, since it can
    // never exceed the length of the normal binary encoding of the value,
    // keeping the same length for positive and negative portions simplifies
    // working with the representation and allows extra conditional branches to
    // be avoided.
    //
    // start and end specify the starting and ending index to use within the pos
    // and neg arrays, respectively.  This allows fixed size arrays to be used
    // versus needing to dynamically allocate space on the heap.
    //
    // NOTE: The fields are defined in the order that they are to minimize the
    // padding on 32-bit and 64-bit platforms.
    val pos: ByteArray,
    val neg: ByteArray
)
