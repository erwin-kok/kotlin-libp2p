// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.host

import org.erwinkok.libp2p.crypto.PublicKey
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.getOrElse

class RemoteIdentity private constructor(
    val peerId: PeerId,
    val publicKey: PublicKey,
) {
    override fun toString(): String {
        return peerId.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is RemoteIdentity) {
            return super.equals(other)
        }
        return publicKey == other.publicKey
    }

    override fun hashCode(): Int {
        return publicKey.hashCode()
    }

    companion object {
        fun fromPublicKey(publicKey: PublicKey): Result<RemoteIdentity> {
            val peerId = PeerId.fromPublicKey(publicKey)
                .getOrElse { return Err(it) }
            return Ok(RemoteIdentity(peerId, publicKey))
        }
    }
}
