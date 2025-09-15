// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.record

import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import org.erwinkok.libp2p.core.record.pb.DbEnvelope
import org.erwinkok.libp2p.core.record.pb.envelope
import org.erwinkok.libp2p.crypto.CryptoUtil
import org.erwinkok.libp2p.crypto.PrivateKey
import org.erwinkok.libp2p.crypto.PublicKey
import org.erwinkok.multiformat.util.writeUnsignedVarInt
import org.erwinkok.result.Err
import org.erwinkok.result.Error
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.erwinkok.result.flatMap
import org.erwinkok.result.getOrElse
import org.erwinkok.result.getOrThrow
import org.erwinkok.result.map
import org.erwinkok.result.onFailure
import org.erwinkok.result.toErrorIf
import java.io.ByteArrayOutputStream

data class EnvelopeRecordInfo<T : Record>(
    val envelope: Envelope,
    val record: T,
)

class Envelope(
    val publicKeyArray: PublicKey,
    val payloadTypeArray: ByteArray,
    val rawPayloadArray: ByteArray,
    val signatureArray: ByteArray,
) {
    fun marshal(): Result<ByteArray> {
        return CryptoUtil.convertPublicKey(publicKeyArray)
            .flatMap {
                try {
                    Ok(
                        envelope {
                            publicKey = it
                            payloadType = ByteString.copyFrom(payloadTypeArray)
                            payload = ByteString.copyFrom(rawPayloadArray)
                            signature = ByteString.copyFrom(signatureArray)
                        }.toByteArray(),
                    )
                } catch (_: Exception) {
                    Err("Could not build proto buffer envelope")
                }
            }
    }

    fun record(): Result<Record> {
        return RecordRegistry.unmarshalRecordPayload(payloadTypeArray, rawPayloadArray)
    }

    inline fun <reified T : Record> typedRecord(): Result<T> {
        val recordType = RecordRegistry.recordTypeOf<T>() ?: return Err("Record ${T::class.simpleName} has no type associated")
        return recordType.unmarshalRecord(rawPayloadArray)
    }

    private fun validate(domain: String): Result<Unit> {
        return makeUnsigned(domain, payloadTypeArray, rawPayloadArray)
            .flatMap { publicKeyArray.verify(it, signatureArray) }
            .toErrorIf({ verified -> !verified }, { ErrInvalidSignature })
            .map { }
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is Envelope) {
            return super.equals(other)
        }
        if (publicKeyArray != other.publicKeyArray) {
            return false
        }
        if (!payloadTypeArray.contentEquals(other.payloadTypeArray)) {
            return false
        }
        if (!signatureArray.contentEquals(other.signatureArray)) {
            return false
        }
        return rawPayloadArray.contentEquals(other.rawPayloadArray)
    }

    override fun hashCode(): Int {
        return publicKeyArray.hashCode() xor
            payloadTypeArray.contentHashCode() xor
            signatureArray.contentHashCode() xor
            rawPayloadArray.contentHashCode()
    }

    companion object {
        private val ErrEmptyDomain = Error("envelope domain must not be empty")
        private val ErrEmptyPayloadType = Error("payloadType must not be empty")
        private val ErrInvalidSignature = Error("invalid signature or incorrect domain")

        fun seal(record: Record, privateKey: PrivateKey): Result<Envelope> {
            val recordType = RecordRegistry.recordTypeOf(record::class) ?: return Err("Record ${record::class.simpleName} has no type associated")
            val domain = recordType.domain
            if (domain.isBlank()) {
                return Err(ErrEmptyDomain)
            }
            val payloadType = recordType.codec
            if (payloadType.isEmpty()) {
                return Err(ErrEmptyPayloadType)
            }
            val payload = record.marshalRecord()
                .getOrElse { return Err("error marshaling record: ${errorMessage(it)}") }
            return makeUnsigned(domain, payloadType, payload)
                .flatMap { unsigned -> privateKey.sign(unsigned) }
                .map { signature -> Envelope(privateKey.publicKey, payloadType, payload, signature) }
        }

        fun consumeEnvelope(data: ByteArray, domain: String): Result<EnvelopeRecordInfo<Record>> {
            val envelope = unmarshalEnvelope(data)
                .getOrElse { return Err("failed when unmarshalling the envelope: ${errorMessage(it)}") }
            envelope.validate(domain)
                .onFailure { return Err("failed to validate envelope: ${errorMessage(it)}") }
            return envelope.record()
                .map { record -> EnvelopeRecordInfo(envelope, record) }
        }

        internal inline fun <reified T : Record> consumeTypedEnvelope(data: ByteArray): Result<EnvelopeRecordInfo<T>> {
            val recordType = RecordRegistry.recordTypeOf<T>() ?: return Err("Record ${T::class.simpleName} has no type associated")
            val envelope = unmarshalEnvelope(data)
                .getOrElse { return Err("failed when unmarshalling the envelope: ${errorMessage(it)}") }
            envelope.validate(recordType.domain)
                .onFailure { return Err("failed to validate envelope: ${errorMessage(it)}") }
            return envelope.typedRecord<T>()
                .map { record -> EnvelopeRecordInfo(envelope, record) }
        }

        internal fun unmarshalEnvelope(data: ByteArray): Result<Envelope> {
            return try {
                val envelope = DbEnvelope.Envelope.parseFrom(data)
                val publicKey = try {
                    CryptoUtil.convertPublicKey(envelope.publicKey).getOrThrow()
                } catch (_: Exception) {
                    return Err("Could not convert public key")
                }
                Ok(Envelope(publicKey, envelope.payloadType.toByteArray(), envelope.payload.toByteArray(), envelope.signature.toByteArray()))
            } catch (_: InvalidProtocolBufferException) {
                Err("Could not parse from proto buffer.")
            }
        }

        private fun makeUnsigned(domain: String, payloadType: ByteArray, payload: ByteArray): Result<ByteArray> {
            val stream = ByteArrayOutputStream()

            val domainBytes = domain.toByteArray()
            stream.writeUnsignedVarInt(domainBytes.size)
            stream.writeBytes(domainBytes)

            stream.writeUnsignedVarInt(payloadType.size)
            stream.writeBytes(payloadType)

            stream.writeUnsignedVarInt(payload.size)
            stream.writeBytes(payload)

            return Ok(stream.toByteArray())
        }
    }
}
