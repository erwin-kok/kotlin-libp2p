// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.secp256k1

import org.erwinkok.libp2p.crypto.CryptoUtil
import org.erwinkok.libp2p.crypto.PrivateKey
import org.erwinkok.libp2p.crypto.PublicKey
import org.erwinkok.libp2p.crypto.ecdsa.Ecdsa
import org.erwinkok.libp2p.crypto.math.BigInt
import org.erwinkok.libp2p.crypto.pb.Crypto
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import java.security.SecureRandom

// PrivateKey provides facilities for working with secp256k1 private keys within
// this package and includes functionality such as serializing and parsing them
// as well as computing their associated public key.
class Secp256k1PrivateKey(var key: ModNScalar) : PrivateKey(Crypto.KeyType.Secp256k1) {
    private val _secp256k1PublicKey: Secp256k1PublicKey

    init {
        val result = Secp256k1Curve.scalarBaseMultNonConst(key).toAffine()
        _secp256k1PublicKey = Secp256k1PublicKey(result)
    }

    var secp256k1PublicKey: Secp256k1PublicKey = _secp256k1PublicKey
        private set

    // Serialize returns the private key as a 256-bit big-endian binary-encoded
    // number, padded to a length of 32 bytes.
    fun serialize(): ByteArray {
        val privKeyBytes = ByteArray(32)
        key.putBytes(privKeyBytes, 0)
        return privKeyBytes
    }

    override fun hashCode(): Int {
        return key.hashCode()
    }

    override val publicKey: PublicKey
        get() = _secp256k1PublicKey

    override fun sign(data: ByteArray): Result<ByteArray> {
        val hash = CryptoUtil.sha256Digest.digest(data)
        val sig = Secp256k1Signature.signRFC6979(this, hash)._1
        return Ok(sig.serialize())
    }

    override fun raw(): Result<ByteArray> {
        return Ok(serialize())
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is Secp256k1PrivateKey) {
            return super.equals(other)
        }
        return key == other.key
    }

    override fun toString(): String {
        return key.toString()
    }

    companion object {
        // PrivKeyBytesLen defines the length in bytes of a serialized private key.
        const val PrivKeyBytesLen = 32

        // PrivKeyFromBytes returns a private based on the provided byte slice which is
        // interpreted as an unsigned 256-bit big-endian integer in the range [0, N-1],
        // where N is the order of the curve.
        //
        // Note that this means passing a slice with more than 32 bytes is truncated and
        // that truncated value is reduced modulo N.  It is up to the caller to either
        // provide a value in the appropriate range or choose to accept the described
        // behavior.
        //
        // Typically callers should simply make use of GeneratePrivateKey when creating
        // private keys which properly handles generation of appropriate values.
        fun privKeyFromBytes(privKeyBytes: ByteArray): Secp256k1PrivateKey {
            return Secp256k1PrivateKey(ModNScalar.setByteSlice(privKeyBytes))
        }

        // GeneratePrivateKey returns a private key that is suitable for use with
        // secp256k1.
        fun generatePrivateKey(): Secp256k1PrivateKey {
            val key = Ecdsa.generatePrivateKey(KoblitzCurve.secp256k1, SecureRandom())
            return privKeyFromBytes(BigInt.toBytes(key.d))
        }
    }
}
