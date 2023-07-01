// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.host

import org.erwinkok.libp2p.crypto.CryptoUtil
import org.erwinkok.libp2p.crypto.KeyType
import org.erwinkok.libp2p.crypto.PrivateKey
import org.erwinkok.libp2p.crypto.PublicKey
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.getOrElse
import java.security.SecureRandom
import kotlin.random.Random

class LocalIdentity private constructor(
    val peerId: PeerId,
    val privateKey: PrivateKey,
    val publicKey: PublicKey,
) {
    override fun toString(): String {
        return peerId.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is LocalIdentity) {
            return super.equals(other)
        }
        return privateKey == other.privateKey &&
            publicKey == other.publicKey
    }

    override fun hashCode(): Int {
        return peerId.hashCode() xor privateKey.hashCode() xor publicKey.hashCode()
    }

    companion object {
        fun fromPrivateKey(privateKey: PrivateKey): Result<LocalIdentity> {
            val publicKey = privateKey.publicKey
            val peerId = PeerId.fromPublicKey(publicKey)
                .getOrElse { return Err(it) }
            return Ok(LocalIdentity(peerId, privateKey, publicKey))
        }

        fun random(keyType: KeyType = KeyType.ED25519, bits: Int = 2048): Result<LocalIdentity> {
            val seed = Random.nextBytes(10)
            val (privateKey, publicKey) = CryptoUtil.generateKeyPair(keyType, bits, SecureRandom(seed))
                .getOrElse { return Err(it) }
            val peerId = PeerId.fromPublicKey(publicKey)
                .getOrElse { return Err(it) }
            return Ok(LocalIdentity(peerId, privateKey, publicKey))
        }
    }
}
