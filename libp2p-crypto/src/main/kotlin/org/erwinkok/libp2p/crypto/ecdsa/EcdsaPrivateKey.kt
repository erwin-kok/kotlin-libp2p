// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ecdsa

import org.erwinkok.libp2p.crypto.CryptoUtil
import org.erwinkok.libp2p.crypto.PrivateKey
import org.erwinkok.libp2p.crypto.PublicKey
import org.erwinkok.libp2p.crypto.pb.Crypto
import org.erwinkok.result.Err
import org.erwinkok.result.Result
import org.erwinkok.result.getOrElse
import java.math.BigInteger
import java.security.SecureRandom

class EcdsaPrivateKey(ecdsaPublicKey: EcdsaPublicKey, d: BigInteger) : PrivateKey(Crypto.KeyType.ECDSA) {
    var ecdsaPublicKey: EcdsaPublicKey = ecdsaPublicKey
        private set
    var d: BigInteger = d
        private set
    override val publicKey: PublicKey
        get() = ecdsaPublicKey

    override fun raw(): Result<ByteArray> {
        return Ecdsa.marshalPrivateKey(this)
    }

    override fun sign(data: ByteArray): Result<ByteArray> {
        val hash = CryptoUtil.sha256Digest.digest(data)
        val rs = Ecdsa.sign(this, hash, SecureRandom())
            .getOrElse { return Err(it) }
        val ecdsaSignature = Asn1Signature(rs.r, rs.s)
        return ecdsaSignature.marshal()
    }

    override fun hashCode(): Int {
        return ecdsaPublicKey.hashCode() xor d.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is EcdsaPrivateKey) {
            return super.equals(other)
        }
        return ecdsaPublicKey == other.ecdsaPublicKey &&
            d == other.d
    }
}
