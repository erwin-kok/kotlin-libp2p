// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.rsa

import org.erwinkok.libp2p.crypto.PrivateKey
import org.erwinkok.libp2p.crypto.PublicKey
import org.erwinkok.libp2p.crypto.pb.Crypto
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import java.security.Signature
import java.security.interfaces.RSAPrivateCrtKey

class RsaPrivateKey(val rsaPublicKey: RsaPublicKey, val privateKey: RSAPrivateCrtKey) : PrivateKey(Crypto.KeyType.RSA) {
    override val publicKey: PublicKey
        get() = rsaPublicKey

    override fun raw(): Result<ByteArray> {
        return Rsa.marshalPrivateKey(this)
    }

    override fun sign(data: ByteArray): Result<ByteArray> {
        val privateSignature = Signature.getInstance("SHA256withRSA")
        privateSignature.initSign(this.privateKey)
        privateSignature.update(data)
        return Ok(privateSignature.sign())
    }

    override fun hashCode(): Int {
        var result = rsaPublicKey.hashCode()
        result = 31 * result + privateKey.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is RsaPrivateKey) {
            return super.equals(other)
        }
        return rsaPublicKey == other.rsaPublicKey &&
            privateKey == other.privateKey
    }
}
