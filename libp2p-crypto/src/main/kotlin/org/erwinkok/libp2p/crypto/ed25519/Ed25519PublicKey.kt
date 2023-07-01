// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ed25519

import org.erwinkok.libp2p.crypto.PublicKey
import org.erwinkok.libp2p.crypto.pb.Crypto
import org.erwinkok.result.Ok
import org.erwinkok.result.Result

class Ed25519PublicKey(data: ByteArray) : PublicKey(Crypto.KeyType.Ed25519) {
    private val data = ByteArray(Ed25519.PUBLIC_KEY_SIZE)

    init {
        System.arraycopy(data, 0, this.data, 0, Ed25519.PUBLIC_KEY_SIZE)
    }

    override fun raw(): Result<ByteArray> {
        return Ok(data)
    }

    override fun verify(data: ByteArray, signature: ByteArray): Result<Boolean> {
        return Ed25519.verify(this.data, data, signature)
    }

    override fun hashCode(): Int {
        return data.contentHashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is Ed25519PublicKey) {
            return super.equals(other)
        }
        return data.contentEquals(other.data)
    }
}
