// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.security.plaintext

import com.google.protobuf.ByteString
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.core.buildPacket
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.core.writeFully
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.host.RemoteIdentity
import org.erwinkok.libp2p.core.network.Connection
import org.erwinkok.libp2p.core.network.readUnsignedVarInt
import org.erwinkok.libp2p.core.plaintext.pb.Plaintext
import org.erwinkok.libp2p.core.util.writeUnsignedVarInt
import org.erwinkok.libp2p.crypto.CryptoUtil
import org.erwinkok.libp2p.crypto.PublicKey
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.getOrElse

private val logger = KotlinLogging.logger {}

class PlainTextHandshaker internal constructor(
    private val connection: Connection,
    private val localIdentity: LocalIdentity?,
) {
    suspend fun runHandshake(): Result<RemoteIdentity> {
        if (localIdentity == null) {
            // If we were initialized without keys, behave as in plaintext/1.0.0 (do nothing)
            return Err("Initialized without keys, plaintext/1.0.0 not supported ")
        }
        val exchangeMessage = makeExchangeMessage(localIdentity.publicKey)
            .getOrElse { return Err(it) }
        sendExchangeMessage(exchangeMessage)
        val remoteMsg = readExchangeMessage()
            .getOrElse { return Err(it) }
        val remotePublicKey = CryptoUtil.convertPublicKey(remoteMsg.pubkey)
            .getOrElse { return Err(it) }
        val remotePeerId = PeerId.fromBytes(remoteMsg.id.toByteArray())
            .getOrElse { return Err(it) }
        if (!remotePeerId.matchesPublicKey(remotePublicKey)) {
            val calculatedPeerId = PeerId.fromPublicKey(remotePublicKey)
            return Err("Peer mismatch. PublicKey does not match PeerId. peerId=$remotePeerId, calculated=$calculatedPeerId")
        }
        logger.info { "Remote peerId: $remotePeerId" }
        return RemoteIdentity.fromPublicKey(remotePublicKey)
    }

    private suspend fun sendExchangeMessage(exchangeMessage: Plaintext.Exchange) {
        val packet = buildPacket {
            val outBytes = exchangeMessage.toByteArray()
            writeUnsignedVarInt(outBytes.size)
            writeFully(outBytes)
        }
        connection.output.writePacket(packet)
        connection.output.flush()
    }

    private suspend fun readExchangeMessage(): Result<Plaintext.Exchange> {
        try {
            val size = connection.input.readUnsignedVarInt()
                .getOrElse { return Err(it) }
            val packet = connection.input.readPacket(size.toInt())
                .readBytes()
            return Ok(Plaintext.Exchange.parseFrom(packet))
        } catch (e: Exception) {
            return Err("Could not parse proto buffer: ${e.message}")
        }
    }

    private fun makeExchangeMessage(pubKey: PublicKey): Result<Plaintext.Exchange> {
        try {
            val peerId = PeerId.fromPublicKey(pubKey)
                .getOrElse { return Err("Initialized without keys, plaintext/1.0.0 not supported ") }
            val cryptoPubKey = CryptoUtil.convertPublicKey(pubKey)
                .getOrElse { return Err(it) }
            return Ok(
                Plaintext.Exchange
                    .newBuilder()
                    .setId(ByteString.copyFrom(peerId.idBytes()))
                    .setPubkey(cryptoPubKey)
                    .build(),
            )
        } catch (e: Exception) {
            return Err("Could not build to proto buffer: ${e.message}")
        }
    }
}
