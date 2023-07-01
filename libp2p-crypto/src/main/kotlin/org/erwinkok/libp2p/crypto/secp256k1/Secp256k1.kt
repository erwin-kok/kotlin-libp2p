// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.secp256k1

import org.erwinkok.libp2p.crypto.KeyPair
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result

object Secp256k1 {
    fun generateKeyPair(): Result<KeyPair> {
        val privateKey = generatePrivateKey()
        return Ok(KeyPair(privateKey, privateKey.publicKey))
    }

    fun generatePrivateKey(): Secp256k1PrivateKey {
        return Secp256k1PrivateKey.generatePrivateKey()
    }

    fun unmarshalPrivateKey(data: ByteArray): Result<Secp256k1PrivateKey> {
        if (data.size != Secp256k1PrivateKey.PrivKeyBytesLen) {
            return Err("expected secp256k1 data size to be ${Secp256k1PrivateKey.PrivKeyBytesLen}")
        }
        return Ok(Secp256k1PrivateKey.privKeyFromBytes(data))
    }

    fun unmarshalPublicKey(data: ByteArray): Result<Secp256k1PublicKey> {
        return Secp256k1PublicKey.parsePubKey(data)
    }
}
