// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ed25519

import org.erwinkok.libp2p.crypto.KeyPair
import org.erwinkok.libp2p.crypto.PrivateKey
import org.erwinkok.libp2p.crypto.PublicKey
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.erwinkok.result.map
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays

object Ed25519 {
    // PublicKeySize is the size, in bytes, of public keys as used in this package.
    const val PUBLIC_KEY_SIZE = 32

    // PrivateKeySize is the size, in bytes, of private keys as used in this package.
    const val PRIVATE_KEY_SIZE = 64

    // SignatureSize is the size, in bytes, of signatures generated and verified by this package.
    const val SIGNATURE_SIZE = 64

    // SeedSize is the size, in bytes, of private key seeds. These are the private key representations used by RFC 8032.
    private const val SEED_SIZE = 32

    fun generateKeyPair(secureRandom: SecureRandom = SecureRandom()): Result<KeyPair> {
        val seed = ByteArray(SEED_SIZE)
        secureRandom.nextBytes(seed)
        return generatePrivateKey(seed)
            .map { privateKey ->
                val publicKey = ByteArray(PUBLIC_KEY_SIZE)
                System.arraycopy(privateKey, 32, publicKey, 0, publicKey.size)
                return Ok(KeyPair(Ed25519PrivateKey(privateKey), Ed25519PublicKey(publicKey)))
            }
    }

    fun generatePrivateKey(seed: ByteArray): Result<ByteArray> {
        val sha512 = MessageDigest.getInstance("SHA-512")
        val h = sha512.digest(seed)
        val s = Scalar.setBytesWithClamping(Arrays.copyOf(h, 32))
        val a = Point.scalarBaseMult(s)
        val publicKey = a.bytes()
        val privateKey = ByteArray(PRIVATE_KEY_SIZE)
        System.arraycopy(seed, 0, privateKey, 0, seed.size)
        System.arraycopy(publicKey, 0, privateKey, 32, publicKey.size)
        return Ok(privateKey)
    }

    fun unmarshalPrivateKey(data: ByteArray): Result<PrivateKey> {
        if (data.size == PRIVATE_KEY_SIZE + PUBLIC_KEY_SIZE) {
            val redundantPk = data.copyOfRange(PRIVATE_KEY_SIZE, data.size)
            val pk = data.copyOfRange(PRIVATE_KEY_SIZE - PUBLIC_KEY_SIZE, PRIVATE_KEY_SIZE)
            if (!redundantPk.contentEquals(pk)) {
                return Err("expected redundant ed25519 public key to be redundant")
            }
            return Ok(Ed25519PrivateKey(data.copyOf(PRIVATE_KEY_SIZE)))
        } else if (data.size != PRIVATE_KEY_SIZE) {
            return Err("expected ed25519 data size to be $PRIVATE_KEY_SIZE or $PRIVATE_KEY_SIZE$PUBLIC_KEY_SIZE, got ${data.size}")
        }
        return Ok(Ed25519PrivateKey(data))
    }

    fun unmarshalPublicKey(data: ByteArray): Result<PublicKey> {
        if (data.size != 32) {
            return Err("expect ed25519 public key data size to be 32")
        }
        return Ok(Ed25519PublicKey(data))
    }

    // Public returns the PublicKey corresponding to priv.
    fun publicKey(privateKey: ByteArray): ByteArray {
        val publicKey = ByteArray(PUBLIC_KEY_SIZE)
        System.arraycopy(privateKey, 32, publicKey, 0, publicKey.size)
        return publicKey
    }

    // Seed returns the private key seed corresponding to priv. It is provided for
    // interoperability with RFC 8032. RFC 8032's private keys correspond to seeds
    // in this package.
    fun seed(privateKey: ByteArray): ByteArray {
        val seed = ByteArray(SEED_SIZE)
        System.arraycopy(privateKey, 0, seed, 0, seed.size)
        return seed
    }

    fun sign(privateKey: ByteArray, message: ByteArray): Result<ByteArray> {
        if (privateKey.size != PRIVATE_KEY_SIZE) {
            return Err("ed25519: bad private key length: ${privateKey.size}")
        }
        val sha512 = MessageDigest.getInstance("SHA-512")
        val signature = ByteArray(SIGNATURE_SIZE)
        val seed = privateKey.copyOfRange(0, SEED_SIZE)
        val publicKey = privateKey.copyOfRange(SEED_SIZE, privateKey.size)
        val digest = sha512.digest(seed)
        val s = Scalar.setBytesWithClamping(Arrays.copyOf(digest, 32))
        val prefix = Arrays.copyOfRange(digest, 32, digest.size)
        sha512.update(prefix)
        sha512.update(message)
        val sr = Scalar.setUniformBytes(sha512.digest())
        val rBytes = Point.scalarBaseMult(sr).bytes()
        sha512.update(rBytes)
        sha512.update(publicKey)
        sha512.update(message)
        val sk = Scalar.setUniformBytes(sha512.digest())
        val sBytes = sk.multiplyAdd(s, sr).bytes()
        System.arraycopy(rBytes, 0, signature, 0, 32)
        System.arraycopy(sBytes, 0, signature, 32, 32)
        return Ok(signature)
    }

    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Result<Boolean> {
        try {
            if (publicKey.size != PUBLIC_KEY_SIZE) {
                return Err("ed25519: bad public key length: ${publicKey.size}")
            }
            if (signature.size != SIGNATURE_SIZE || signature[63].toInt() and 224 != 0) {
                return Ok(false)
            }
            val sha512 = MessageDigest.getInstance("SHA-512")
            val pointA = Point(publicKey)
            val r = signature.copyOfRange(0, 32)
            val s = signature.copyOfRange(32, SIGNATURE_SIZE)
            sha512.reset()
            sha512.update(r)
            sha512.update(publicKey)
            sha512.update(message)
            val h = sha512.digest()
            val sh = Scalar.setUniformBytes(h)
            val ss = Scalar.setCanonicalBytes(s)

            // [S]B = R + [k]A --> [k](-A) + [S]B = R
            val pR = Point.varTimeDoubleScalarBaseMult(sh, -pointA, ss)
            return Ok(pR.bytes().contentEquals(r))
        } catch (e: NumberFormatException) {
            return Err(errorMessage(e))
        }
    }
}
