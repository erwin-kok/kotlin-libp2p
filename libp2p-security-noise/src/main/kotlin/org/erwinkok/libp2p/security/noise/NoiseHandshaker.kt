// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.security.noise

import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import com.southernstorm.noise.protocol.DHState
import com.southernstorm.noise.protocol.HandshakeState
import com.southernstorm.noise.protocol.Noise
import io.github.oshai.kotlinlogging.KotlinLogging
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.host.PeerId.Companion.fromPublicKey
import org.erwinkok.libp2p.core.host.RemoteIdentity
import org.erwinkok.libp2p.core.network.Connection
import org.erwinkok.libp2p.core.network.Direction
import org.erwinkok.libp2p.crypto.CryptoUtil
import org.erwinkok.libp2p.security.noise.pb.Noise.NoiseHandshakePayload
import org.erwinkok.multiformat.multibase.bases.Base64
import org.erwinkok.result.Err
import org.erwinkok.result.Error
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.erwinkok.result.flatMap
import org.erwinkok.result.getOrElse
import org.erwinkok.result.toErrorIf
import java.net.SocketException
import java.security.NoSuchAlgorithmException
import javax.crypto.BadPaddingException
import javax.crypto.ShortBufferException
import kotlin.math.min

private val logger = KotlinLogging.logger {}

class NoiseHandshaker internal constructor(
    private val connection: Connection,
    private val localIdentity: LocalIdentity,
    private val requestedPeerId: PeerId?,
    private val direction: Direction,
) {
    private val localState = createLocalState()

    suspend fun runHandshake(): Result<HandshakeInfo> {
        return try {
            return if (direction == Direction.DirOutbound) {
                val handshakeState = HandshakeState(PROTOCOL_NAME, HandshakeState.INITIATOR)
                handshakeState.localKeyPair.copyFrom(localState)
                handshakeState.start()
                outboundHandshake(handshakeState)
            } else {
                val handshakeState = HandshakeState(PROTOCOL_NAME, HandshakeState.RESPONDER)
                handshakeState.localKeyPair.copyFrom(localState)
                handshakeState.start()
                inboundHandshake(handshakeState)
            }
        } catch (e: NoSuchAlgorithmException) {
            Err("Could not find algorithm: ${errorMessage(e)}")
        }
    }

    private suspend fun outboundHandshake(handshakeState: HandshakeState): Result<HandshakeInfo> {
        return sendHandshakeMessage(handshakeState, null)
            .flatMap { receiveHandshakeMessage(handshakeState) }
            .flatMap { handleRemoteHandshakePayload(handshakeState, it) }
            .flatMap { remoteIdentity ->
                generateHandshakePayload()
                    .flatMap { sendHandshakeMessage(handshakeState, it) }
                    .flatMap { splitHandshake(handshakeState, remoteIdentity) }
            }
    }

    private suspend fun inboundHandshake(handshakeState: HandshakeState): Result<HandshakeInfo> {
        return receiveHandshakeMessage(handshakeState)
            .toErrorIf({ it.isNotEmpty() }, { Error("First inbound noise handshake message should be empty") })
            .flatMap { generateHandshakePayload() }
            .flatMap { sendHandshakeMessage(handshakeState, it) }
            .flatMap { receiveHandshakeMessage(handshakeState) }
            .flatMap { handleRemoteHandshakePayload(handshakeState, it) }
            .flatMap { splitHandshake(handshakeState, it) }
    }

    private fun createLocalState(): DHState {
        val localState = Noise.createDH("25519")
        val localPrivateKey = ByteArray(localState.publicKeyLength)
        Noise.random(localPrivateKey)
        localState.setPrivateKey(localPrivateKey, 0)
        return localState
    }

    private fun generateHandshakePayload(): Result<ByteArray> {
        val localPublicKey = CryptoUtil.marshalPublicKey(localIdentity.publicKey)
            .getOrElse { return Err(it) }
        val toSign = noiseSignaturePhrase(localState)
        val signedPayload = localIdentity.privateKey.sign(toSign)
            .getOrElse { return Err(it) }
        return Ok(
            NoiseHandshakePayload
                .newBuilder()
                .setIdentityKey(ByteString.copyFrom(localPublicKey))
                .setIdentitySig(ByteString.copyFrom(signedPayload))
                .build()
                .toByteArray(),
        )
    }

    private fun handleRemoteHandshakePayload(handshakeState: HandshakeState, payload: ByteArray): Result<RemoteIdentity> {
        try {
            val noiseHandshakePayload = NoiseHandshakePayload.parseFrom(payload)
            val publicKey = CryptoUtil.unmarshalPublicKey(noiseHandshakePayload.identityKey.toByteArray())
                .getOrElse {
                    val msg = "Could not unmarshal public key: ${errorMessage(it)}"
                    logger.error { msg }
                    return Err(msg)
                }
            val publicKeyBytes = publicKey.bytes()
                .getOrElse { return Err(it) }
            val signature = noiseHandshakePayload.identitySig.toByteArray()
            logger.debug { "PublicKey: ${Base64.encodeToStringStd(publicKeyBytes)}" }
            logger.debug { "Signature: ${Base64.encodeToStringStd(signature)}" }
            val verified = publicKey.verify(noiseSignaturePhrase(handshakeState.remotePublicKey), signature)
                .getOrElse { return Err(it) }
            logger.debug { "Verified: $verified" }
            if (!verified) {
                return Err("Could not verify Noise signature.")
            }
            val remotePeerId = fromPublicKey(publicKey)
                .getOrElse {
                    val msg = "Could not decode remote PeerId from public key: ${errorMessage(it)}"
                    logger.error { msg }
                    return Err(msg)
                }
            if (direction == Direction.DirOutbound) {
                val reqPeerId = requestedPeerId ?: return Err("PeerId must be non-null for outbound connections")
                if (remotePeerId != reqPeerId) {
                    val msg = "PeerId mismatch: expected $reqPeerId, but remote key matches $remotePeerId"
                    logger.error { msg }
                    return Err(msg)
                }
            } else if (direction == Direction.DirInbound && requestedPeerId != null && remotePeerId != requestedPeerId) {
                val msg = "PeerId mismatch: expected $requestedPeerId, but remote key matches $remotePeerId"
                logger.error { msg }
                return Err(msg)
            }
            logger.info { "Remote peerId: $remotePeerId" }
            return RemoteIdentity.fromPublicKey(publicKey)
        } catch (e: InvalidProtocolBufferException) {
            return Err("Could not parse payload: ${errorMessage(e)}")
        }
    }

    private suspend fun sendHandshakeMessage(handshakeState: HandshakeState, payload: ByteArray?): Result<Unit> {
        if (handshakeState.action != HandshakeState.WRITE_MESSAGE) {
            return Err("Noise handshake error. Expected to be in WRITE_MESSAGE state.")
        }
        return try {
            val length = payload?.size ?: 0
            val outputBuffer = ByteArray(MAX_NOISE_MASSAGE_SIZE)
            val outputLength = handshakeState.writeMessage(outputBuffer, 0, payload, 0, length)
            connection.output.writeShort(outputLength.toShort())
            connection.output.writeFully(outputBuffer, 0, outputLength)
            connection.output.flush()
            Ok(Unit)
        } catch (e: ShortBufferException) {
            Err("Could not write Noise message: ${errorMessage(e)}")
        } catch (e: SocketException) {
            Err("Could not write Noise message: ${errorMessage(e)}")
        }
    }

    private suspend fun receiveHandshakeMessage(handshakeState: HandshakeState): Result<ByteArray> {
        if (handshakeState.action != HandshakeState.READ_MESSAGE) {
            return Err("Noise handshake error. Expected to be in READ_MESSAGE state.")
        }
        return try {
            val length = connection.input.readShort()
            val bytes = ByteArray(length.toInt())
            connection.input.readFully(bytes, 0, length.toInt())
            val payload = ByteArray(MAX_NOISE_MASSAGE_SIZE)
            val payloadLength = handshakeState.readMessage(bytes, 0, bytes.size, payload, 0)
            val result = payload.copyOf(min(payloadLength, payload.size))
            Ok(result)
        } catch (e: ShortBufferException) {
            Err("Could not read Noise message: ${errorMessage(e)}")
        } catch (e: BadPaddingException) {
            Err("Could not read Noise message: ${errorMessage(e)}")
        } catch (e: SocketException) {
            Err("Could not read Noise message: ${errorMessage(e)}")
        }
    }

    private fun splitHandshake(handshakeState: HandshakeState, remoteIdentity: RemoteIdentity): Result<HandshakeInfo> {
        if (handshakeState.action != HandshakeState.SPLIT) {
            return Err("Noise handshake error. Expected to be in READ_MESSAGE state.")
        }
        val cipherStatePair = handshakeState.split()
        val handshakeInfo = HandshakeInfo(
            remoteIdentity,
            receiverCipherState = cipherStatePair.receiver,
            senderCipherState = cipherStatePair.sender,
        )
        return Ok(handshakeInfo)
    }

    private fun noiseSignaturePhrase(dhState: DHState): ByteArray {
        val key = ByteArray(dhState.publicKeyLength)
        dhState.getPublicKey(key, 0)
        return NOISE_STATIC_KEY.toByteArray() + key
    }

    companion object {
        private const val NOISE_STATIC_KEY = "noise-libp2p-static-key:"
        private const val PROTOCOL_NAME = "Noise_XX_25519_ChaChaPoly_SHA256"
        private const val MAX_NOISE_MASSAGE_SIZE = 8192
    }
}
