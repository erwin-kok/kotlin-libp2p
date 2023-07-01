// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto

import org.erwinkok.libp2p.crypto.CryptoUtil.marshalPrivateKey
import org.erwinkok.libp2p.crypto.pb.Crypto
import org.erwinkok.result.Result
import org.erwinkok.result.map

abstract class PrivateKey protected constructor(keyType: Crypto.KeyType) : Key(keyType) {
    abstract val publicKey: PublicKey
    abstract fun sign(data: ByteArray): Result<ByteArray>
    override fun bytes(): Result<ByteArray> {
        return marshalPrivateKey(this)
    }

    override fun hash(): Result<ByteArray> {
        return bytes()
            .map { CryptoUtil.sha256Digest.digest(it) }
    }
}
