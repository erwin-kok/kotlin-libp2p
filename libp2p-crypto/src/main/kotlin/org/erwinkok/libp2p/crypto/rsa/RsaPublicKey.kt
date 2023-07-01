// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.rsa

import org.erwinkok.libp2p.crypto.PublicKey
import org.erwinkok.libp2p.crypto.pb.Crypto
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import java.security.Signature
import java.security.interfaces.RSAPublicKey

class RsaPublicKey(val pubKey: RSAPublicKey) : PublicKey(Crypto.KeyType.RSA) {
    override fun raw(): Result<ByteArray> {
        return Rsa.marshalPublicKey(this)
    }

    override fun verify(data: ByteArray, signature: ByteArray): Result<Boolean> {
        val publicSignature = Signature.getInstance("SHA256withRSA")
        publicSignature.initVerify(this.pubKey)
        publicSignature.update(data)
        return Ok(publicSignature.verify(signature))
    }

    override fun hashCode(): Int {
        return pubKey.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is RsaPublicKey) {
            return super.equals(other)
        }
        return pubKey == other.pubKey
    }
}
