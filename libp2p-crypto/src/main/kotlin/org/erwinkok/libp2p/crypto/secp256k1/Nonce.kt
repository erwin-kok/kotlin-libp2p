// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.secp256k1

import kotlin.math.max
import kotlin.math.min

object Nonce {
    private const val privKeyLen = 32
    private const val hashLen = 32
    private const val extraLen = 32
    private const val versionLen = 16

    // The size of a SHA256 checksum in bytes.
    private const val sha256Size = 32

    // The blocksize of SHA256 in bytes.
    private const val Sha256BlockSize = 64

    // singleZero is used during RFC6979 nonce generation.  It is provided
    // here to avoid the need to create it multiple times.
    private val singleZero = byteArrayOf(0)

    // singleOne is used during RFC6979 nonce generation.  It is provided
    // here to avoid the need to create it multiple times.
    private val singleOne = byteArrayOf(1)

    // zeroInitializer is used during RFC6979 nonce generation.  It is provided
    // here to avoid the need to create it multiple times.
    private val zeroInitializer = ByteArray(Sha256BlockSize)

    // oneInitializer is used during RFC6979 nonce generation.  It is provided
    // here to avoid the need to create it multiple times.
    private val oneInitializer = ByteArray(sha256Size) { 1 }

    // NonceRFC6979 generates a nonce deterministically according to RFC 6979 using
    // HMAC-SHA256 for the hashing function.  It takes a 32-byte hash as an input
    // and returns a 32-byte nonce to be used for deterministic signing.  The extra
    // and version arguments are optional, but allow additional data to be added to
    // the input of the HMAC.  When provided, the extra data must be 32-bytes and
    // version must be 16 bytes or they will be ignored.
    //
    // Finally, the extraIterations parameter provides a method to produce a stream
    // of deterministic nonces to ensure the signing code is able to produce a nonce
    // that results in a valid signature in the extremely unlikely event the
    // original nonce produced results in an invalid signature (e.g. R == 0).
    // Signing code should start with 0 and increment it if necessary.
    fun nonceRFC6979(privKey: ByteArray, hash: ByteArray, extra: ByteArray, version: ByteArray, extraIterations: Int): ModNScalar {
        // Input to HMAC is the 32-byte private key and the 32-byte hash.  In
        // addition, it may include the optional 32-byte extra data and 16-byte
        // version.  Create a fixed-size array to avoid extra allocs and slice it
        // properly.
        val key = ByteArray(privKeyLen + hashLen + extraLen + versionLen)

        System.arraycopy(privKey, 0, key, max(0, privKeyLen - privKey.size), min(privKey.size, privKeyLen))
        System.arraycopy(hash, 0, key, privKeyLen + max(0, hashLen - hash.size), min(hash.size, hashLen))

        var keyLen = privKeyLen + hashLen

        if (extra.size == extraLen) {
            System.arraycopy(extra, 0, key, privKeyLen + hashLen, extraLen)
            keyLen += extraLen
            if (version.size == versionLen) {
                System.arraycopy(version, 0, key, privKeyLen + hashLen + extraLen, versionLen)
                keyLen += versionLen
            }
        } else if (version.size == versionLen) {
            keyLen += privKeyLen
            System.arraycopy(version, 0, key, privKeyLen + hashLen + privKeyLen, versionLen)
            keyLen += versionLen
        }

        // Step B.
        //
        // V = 0x01 0x01 0x01 ... 0x01 such that the length of V, in bits, is
        // equal to 8*ceil(hashLen/8).
        //
        // Note that since the hash length is a multiple of 8 for the chosen hash
        // function in this optimized implementation, the result is just the hash
        // length, so avoid the extra calculations.  Also, since it isn't modified,
        // start with a global value.
        var v = oneInitializer

        // Step C (Go zeroes all allocated memory).
        //
        // K = 0x00 0x00 0x00 ... 0x00 such that the length of K, in bits, is
        // equal to 8*ceil(hashLen/8).
        //
        // As above, since the hash length is a multiple of 8 for the chosen hash
        // function in this optimized implementation, the result is just the hash
        // length, so avoid the extra calculations.
        var k = ByteArray(hashLen)

        // Step D.
        //
        // K = HMAC_K(V || 0x00 || int2octets(x) || bits2octets(h1))
        //
        // Note that key is the "int2octets(x) || bits2octets(h1)" portion along
        // with potential additional data as described by section 3.6 of the RFC.
        val hasher = HmacSha256(k)
        hasher.write(oneInitializer)
        hasher.write(singleZero)
        hasher.write(key, 0, keyLen)
        k = hasher.sum()

        // Step E.
        //
        // V = HMAC_K(V)
        hasher.resetKey(k)
        hasher.write(v)
        v = hasher.sum()

        // Step F.
        //
        // K = HMAC_K(V || 0x01 || int2octets(x) || bits2octets(h1))
        //
        // Note that key is the "int2octets(x) || bits2octets(h1)" portion along
        // with potential additional data as described by section 3.6 of the RFC.
        hasher.reset()
        hasher.write(v)
        hasher.write(singleOne)
        hasher.write(key, 0, keyLen)
        k = hasher.sum()

        // Step G.
        //
        // V = HMAC_K(V)
        hasher.resetKey(k)
        hasher.write(v)
        v = hasher.sum()

        var generated = 0
        while (true) {
            // Step H1 and H2.
            //
            // Set T to the empty sequence.  The length of T (in bits) is denoted
            // tlen; thus, at that point, tlen = 0.
            //
            // While tlen < qlen, do the following:
            //   V = HMAC_K(V)
            //   T = T || V
            //
            // Note that because the hash function output is the same length as the
            // private key in this optimized implementation, there is no need to
            // loop or create an intermediate T.
            hasher.reset()
            hasher.write(v)
            v = hasher.sum()

            // Step H3.
            //
            // k = bits2int(T)
            // If k is within the range [1,q-1], return it.
            //
            // Otherwise, compute:
            // K = HMAC_K(V || 0x00)
            // V = HMAC_K(V)

            val secret = ModNScalar.setBytesUnchecked(v)
            val overflow = secret.overflows
            secret.reduce256(overflow)
            if (overflow == 0u && !secret.isZero) {
                generated++
                if (generated > extraIterations) {
                    return secret
                }
            }
            // K = HMAC_K(V || 0x00)
            hasher.reset()
            hasher.write(v)
            hasher.write(singleZero)
            k = hasher.sum()

            // V = HMAC_K(V)
            hasher.resetKey(k)
            hasher.write(v)
            v = hasher.sum()
        }
    }
}
