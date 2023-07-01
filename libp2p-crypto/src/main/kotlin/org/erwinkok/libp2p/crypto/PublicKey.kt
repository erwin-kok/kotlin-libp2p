// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto

import org.erwinkok.libp2p.crypto.pb.Crypto
import org.erwinkok.result.Result
import org.erwinkok.result.map

abstract class PublicKey protected constructor(keyType: Crypto.KeyType) : Key(keyType) {
    abstract fun verify(data: ByteArray, signature: ByteArray): Result<Boolean>

    override fun bytes(): Result<ByteArray> {
        return CryptoUtil.marshalPublicKey(this)
    }

    override fun hash(): Result<ByteArray> {
        return bytes()
            .map { CryptoUtil.sha256Digest.digest(it) }
    }
}
