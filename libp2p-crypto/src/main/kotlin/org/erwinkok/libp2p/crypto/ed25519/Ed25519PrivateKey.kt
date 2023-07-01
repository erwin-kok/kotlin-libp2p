// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ed25519

import org.erwinkok.libp2p.crypto.PrivateKey
import org.erwinkok.libp2p.crypto.PublicKey
import org.erwinkok.libp2p.crypto.pb.Crypto
import org.erwinkok.result.Ok
import org.erwinkok.result.Result

class Ed25519PrivateKey(data: ByteArray) : PrivateKey(Crypto.KeyType.Ed25519) {
    private val data = ByteArray(Ed25519.PRIVATE_KEY_SIZE)

    override val publicKey: PublicKey

    init {
        System.arraycopy(data, 0, this.data, 0, Ed25519.PRIVATE_KEY_SIZE)
        publicKey = Ed25519PublicKey(data.copyOfRange(Ed25519.PRIVATE_KEY_SIZE - Ed25519.PUBLIC_KEY_SIZE, data.size))
    }

    override fun raw(): Result<ByteArray> {
        return Ok(data)
    }

    override fun sign(data: ByteArray): Result<ByteArray> {
        return Ed25519.sign(this.data, data)
    }

    override fun hashCode(): Int {
        return data.contentHashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is Ed25519PrivateKey) {
            return super.equals(other)
        }
        return data.contentEquals(other.data)
    }
}
