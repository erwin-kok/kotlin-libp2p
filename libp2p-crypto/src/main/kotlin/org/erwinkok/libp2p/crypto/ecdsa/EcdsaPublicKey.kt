// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ecdsa

import org.erwinkok.libp2p.crypto.CryptoUtil
import org.erwinkok.libp2p.crypto.PublicKey
import org.erwinkok.libp2p.crypto.pb.Crypto
import org.erwinkok.result.Err
import org.erwinkok.result.Result
import org.erwinkok.result.getOrElse

class EcdsaPublicKey(curve: Curve, cp: CurvePoint) : PublicKey(Crypto.KeyType.ECDSA) {
    var curve: Curve = curve
        private set
    var cp: CurvePoint = cp
        private set

    override fun raw(): Result<ByteArray> {
        return Ecdsa.marshalPublicKey(this)
    }

    override fun verify(data: ByteArray, signature: ByteArray): Result<Boolean> {
        val asn1Signature = Asn1Signature.unmarshal(signature)
            .getOrElse { return Err(it) }
        val hash = CryptoUtil.digestSha256(data)
        return Ecdsa.verify(this, hash, EcdsaSignature(asn1Signature.r, asn1Signature.s))
    }

    override fun hashCode(): Int {
        return curve.hashCode() xor cp.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is EcdsaPublicKey) {
            return super.equals(other)
        }
        return curve == other.curve &&
            cp == other.cp
    }
}
