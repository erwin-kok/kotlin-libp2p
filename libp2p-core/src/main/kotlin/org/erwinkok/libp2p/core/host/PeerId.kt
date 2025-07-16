// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.host

import org.erwinkok.libp2p.crypto.CryptoUtil
import org.erwinkok.libp2p.crypto.PrivateKey
import org.erwinkok.libp2p.crypto.PublicKey
import org.erwinkok.multiformat.cid.Cid
import org.erwinkok.multiformat.multibase.bases.Base58
import org.erwinkok.multiformat.multicodec.Multicodec
import org.erwinkok.multiformat.multihash.Multihash
import org.erwinkok.result.Err
import org.erwinkok.result.Error
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.flatMap
import org.erwinkok.result.getOrElse
import org.erwinkok.result.getOrThrow
import org.erwinkok.result.map

class PeerId private constructor(val multihash: Multihash) {
    fun idBytes(): ByteArray {
        return multihash.bytes()
    }

    fun id(): String {
        return String(idBytes())
    }

    fun shortString(): String {
        val pid = toString()
        return if (pid.length <= 10) {
            "<PeerId $pid>"
        } else {
            val start = pid.substring(0, 2)
            val end = pid.substring(pid.length - 6)
            "<PeerId $start*$end>"
        }
    }

    fun matchesPrivateKey(privateKey: PrivateKey): Boolean {
        return matchesPublicKey(privateKey.publicKey)
    }

    fun matchesPublicKey(publicKey: PublicKey): Boolean {
        val oid = fromPublicKey(publicKey)
            .getOrElse { return false }
        return this == oid
    }

    fun extractPublicKey(): Result<PublicKey> {
        if (multihash.type != Multicodec.IDENTITY) {
            return Err(ErrNoPublicKey)
        }
        return CryptoUtil.unmarshalPublicKey(multihash.digest)
    }

    fun validate(): Boolean {
        return true
    }

    fun encode(): String {
        return Base58.encodeToStringBtc(idBytes())
    }

    fun toCid(): Result<Cid> {
        return Cid
            .builder()
            .withVersion(1)
            .withMulticodec(Multicodec.LIBP2P_KEY)
            .withMultihash(multihash)
            .build()
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is PeerId) {
            return super.equals(other)
        }
        return multihash == other.multihash
    }

    override fun hashCode(): Int {
        return multihash.hashCode()
    }

    override fun toString(): String {
        return Base58.encodeToStringBtc(idBytes())
    }

    companion object {
        val ErrNoPublicKey = Error("public key is not embedded in PeerId")

        // AdvancedEnableInlining enables automatically inlining keys shorter than
        // 42 bytes into the peer ID (using the "identity" multihash function).
        //
        // WARNING: This flag will likely be set to false in the future and eventually
        // be removed in favor of using a hash function specified by the key itself.
        // See: https://github.com/libp2p/specs/issues/138
        //
        // DO NOT change this flag unless you know what you're doing.
        //
        // This currently defaults to true for backwards compatibility but will likely
        // be set to false by default when an upgrade path is determined.
        private const val advancedEnableInlining = true
        private const val maxInlineKeyLength = 42

        val Null = Multihash.fromTypeAndDigest(Multicodec.IDENTITY, byteArrayOf(0))
            .map { PeerId(it) }
            .getOrThrow()

        fun fromPublicKey(publicKey: PublicKey): Result<PeerId> {
            val bytes = CryptoUtil.marshalPublicKey(publicKey)
                .getOrElse { return Err(it) }
            val algorithm = if (advancedEnableInlining && bytes.size <= maxInlineKeyLength) {
                Multicodec.IDENTITY
            } else {
                Multicodec.SHA2_256
            }
            return Multihash.sum(algorithm, bytes, -1)
                .map { PeerId(it) }
        }

        fun fromPrivateKey(privateKey: PrivateKey): Result<PeerId> {
            return fromPublicKey(privateKey.publicKey)
        }

        fun fromString(s: String): Result<PeerId> {
            return Multihash.fromBase58(s)
                .map { PeerId(it) }
        }

        fun fromBytes(b: ByteArray): Result<PeerId> {
            return Multihash.fromBytes(b)
                .flatMap { fromMultihash(it) }
        }

        fun fromMultihash(mh: Multihash): Result<PeerId> {
            return Ok(PeerId(mh))
        }

        fun fromCid(cid: Cid): Result<PeerId> {
            if (cid.multicodec != Multicodec.LIBP2P_KEY) {
                return Err("can't convert CID of type ${cid.multicodec.typeName} to a PeerId")
            }
            return Ok(PeerId(cid.multihash))
        }

        fun decode(s: String): Result<PeerId> {
            return if (s.startsWith("Qm") || s.startsWith("1")) {
                // base58 encoded sha256 or identity multihash
                Multihash.fromBase58(s)
                    .map { PeerId(it) }
            } else {
                Cid.fromString(s)
                    .flatMap { fromCid(it) }
            }
        }
    }
}
