// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.record

import com.google.protobuf.ByteString
import org.erwinkok.libp2p.core.record.pb.DbEnvelope
import org.erwinkok.libp2p.crypto.CryptoUtil.generateKeyPair
import org.erwinkok.libp2p.crypto.KeyType
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.assertErrorResult
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class EnvelopeTest {
    @Test
    fun happyPath() {
        val record = SimpleRecord("hello world!")
        val (privateKey, publicKey) = generateKeyPair(KeyType.ED25519, 256).expectNoErrors()
        val payload = record.marshalRecord().expectNoErrors()
        val envelope = Envelope.seal(record, privateKey).expectNoErrors()
        assertEquals(publicKey, envelope.publicKeyArray, "envelope has unexpected public key")
        assertArrayEquals(envelope.payloadTypeArray, SimpleRecord.codec, "PayloadType does not match record Codec")
        val serialized = envelope.marshal().expectNoErrors()
        val deserialized = Envelope.consumeEnvelope(serialized, SimpleRecord.domain).expectNoErrors()
        assertArrayEquals(payload, deserialized.envelope.rawPayloadArray, "payload of envelope does not match input")
        assertEquals(deserialized.envelope, envelope, "round-trip serde results in unequal envelope structures")
        val typedRec = deserialized.record as SimpleRecord
        assertEquals("hello world!", typedRec.message, "unexpected alteration of record")
    }

    @Test
    fun consumeTypedEnvelope() {
        val record = SimpleRecord("hello world!")
        val (privateKey) = generateKeyPair(KeyType.ED25519, 256).expectNoErrors()
        val envelope = Envelope.seal(record, privateKey).expectNoErrors()
        val envelopeBytes = envelope.marshal().expectNoErrors()
        val (_, record2) = Envelope.consumeTypedEnvelope<SimpleRecord>(envelopeBytes).expectNoErrors()
        assertEquals("hello world!", record2.message, "unexpected alteration of record")
    }

    @Test
    fun makeEnvelopeFailsWithEmptyDomain() {
        val record = SimpleRecordNoDomain("hello world!")
        val (privateKey) = generateKeyPair(KeyType.ED25519, 256).expectNoErrors()
        assertErrorResult("envelope domain must not be empty") { Envelope.seal(record, privateKey) }
    }

    @Test
    fun makeEnvelopeFailsWithEmptyPayloadType() {
        val record = SimpleRecordNoCodec("hello world!")
        val (privateKey) = generateKeyPair(KeyType.ED25519, 256).expectNoErrors()
        assertErrorResult("payloadType must not be empty") { Envelope.seal(record, privateKey) }
    }

    @Test
    fun sealFailsIfRecordMarshalFails() {
        val (privateKey) = generateKeyPair(KeyType.ED25519, 256).expectNoErrors()
        val record = FailingRecordNoMarshal()
        assertErrorResult("error marshaling record: marshal failed") { Envelope.seal(record, privateKey) }
    }

    @Test
    fun consumeEnvelopeFailsIfEnvelopeUnmarshalFails() {
        assertErrorResult("failed when unmarshalling the envelope: Could not parse from proto buffer.") { Envelope.consumeEnvelope("not an Envelope protobuf".toByteArray(), "doesn't-matter") }
    }

    @Test
    fun consumeEnvelopeFailsIfRecordUnmarshalFails() {
        val (privateKey) = generateKeyPair(KeyType.ED25519, 256).expectNoErrors()
        val rec = FailingRecordMarshal()
        val envelope = Envelope.seal(rec, privateKey).expectNoErrors()
        val envBytes = envelope.marshal().expectNoErrors()
        assertErrorResult("unmarshal failed") { Envelope.consumeEnvelope(envBytes, FailingRecordMarshal.domain) }
    }

    @Test
    fun consumeTypedEnvelopeFailsIfRecordUnmarshalFails() {
        val (privateKey) = generateKeyPair(KeyType.ED25519, 256).expectNoErrors()
        val record = FailingRecordMarshal()
        val envelope = Envelope.seal(record, privateKey).expectNoErrors()
        val envBytes = envelope.marshal().expectNoErrors()
        assertErrorResult("unmarshal failed") { Envelope.consumeTypedEnvelope<FailingRecordMarshal>(envBytes) }
    }

    @Test
    fun envelopeValidateFailsForDifferentDomain() {
        val rec = SimpleRecord("hello world!")
        val (privateKey) = generateKeyPair(KeyType.ED25519, 256).expectNoErrors()
        val env = Envelope.seal(rec, privateKey).expectNoErrors()
        val serialized = env.marshal().expectNoErrors()
        assertErrorResult("failed to validate envelope: invalid signature or incorrect domain") { Envelope.consumeEnvelope(serialized, "wrong-domain") }
    }

    @Test
    fun envelopeValidateFailsIfPayloadTypeIsAltered() {
        val record = SimpleRecord("hello world!")
        val (privateKey) = generateKeyPair(KeyType.ED25519, 256).expectNoErrors()
        val envelope = Envelope.seal(record, privateKey).expectNoErrors()
        val serialized = alterMessageAndMarshal(envelope) {
            it.payloadType = ByteString.copyFromUtf8("foo")
        }
        assertErrorResult("failed to validate envelope: invalid signature or incorrect domain") { Envelope.consumeEnvelope(serialized, "libp2p-testing") }
    }

    @Test
    fun envelopeValidateFailsIfContentsAreAltered() {
        val record = SimpleRecord("hello world!")
        val (privateKey) = generateKeyPair(KeyType.ED25519, 256).expectNoErrors()
        val envelope = Envelope.seal(record, privateKey).expectNoErrors()
        val serialized = alterMessageAndMarshal(envelope) {
            it.payload = ByteString.copyFromUtf8("totally legit, trust me")
        }
        assertErrorResult("failed to validate envelope: invalid signature or incorrect domain") { Envelope.consumeEnvelope(serialized, "libp2p-testing") }
    }

    private fun alterMessageAndMarshal(envelope: Envelope, alterMsg: (DbEnvelope.Envelope.Builder) -> Unit): ByteArray {
        val serialized = envelope.marshal().expectNoErrors()
        val msg = DbEnvelope.Envelope.parseFrom(serialized).toBuilder()
        alterMsg(msg)
        return msg.build().toByteArray()
    }

    internal class SimpleRecord(var message: String? = null) : Record {
        override fun marshalRecord(): Result<ByteArray> {
            return Ok(message!!.toByteArray())
        }

        companion object SimpleRecordType : RecordType<SimpleRecord> {
            init {
                RecordRegistry.registerType(SimpleRecordType)
            }

            override val domain: String
                get() = "libp2p-testing"
            override val codec: ByteArray
                get() = "/libp2p/testdata".toByteArray()

            override fun unmarshalRecord(data: ByteArray): Result<SimpleRecord> {
                return Ok(SimpleRecord(String(data)))
            }
        }
    }

    internal class SimpleRecordNoDomain(val message: String) : Record {
        override fun marshalRecord(): Result<ByteArray> {
            return Ok(message.toByteArray())
        }

        companion object SimpleRecordNoDomainType : RecordType<SimpleRecordNoDomain> {
            init {
                RecordRegistry.registerType(SimpleRecordNoDomainType)
            }

            override val domain: String
                get() = ""
            override val codec: ByteArray
                get() = "/libp2p/testdata".toByteArray()

            override fun unmarshalRecord(data: ByteArray): Result<SimpleRecordNoDomain> {
                return Ok(SimpleRecordNoDomain(String(data)))
            }
        }
    }

    internal class SimpleRecordNoCodec(val message: String) : Record {
        override fun marshalRecord(): Result<ByteArray> {
            return Ok(message.toByteArray())
        }

        companion object SimpleRecordNoCodecType : RecordType<SimpleRecordNoCodec> {
            init {
                RecordRegistry.registerType(SimpleRecordNoCodecType)
            }

            override val domain: String
                get() = "libp2p-testing"
            override val codec: ByteArray
                get() = byteArrayOf()

            override fun unmarshalRecord(data: ByteArray): Result<SimpleRecordNoCodec> {
                return Ok(SimpleRecordNoCodec(String(data)))
            }
        }
    }

    internal class FailingRecordMarshal : Record {
        override fun marshalRecord(): Result<ByteArray> {
            return Ok(byteArrayOf())
        }

        companion object FailingRecordMarshalType : RecordType<FailingRecordMarshal> {
            init {
                RecordRegistry.registerType(FailingRecordMarshal)
            }

            override val domain: String
                get() = "testing"
            override val codec: ByteArray
                get() = "doesn't matter".toByteArray()

            override fun unmarshalRecord(data: ByteArray): Result<FailingRecordMarshal> {
                return Err("unmarshal failed")
            }
        }
    }

    internal class FailingRecordNoMarshal : Record {
        override fun marshalRecord(): Result<ByteArray> {
            return Err("marshal failed")
        }

        companion object FailingRecordNoMarshalType : RecordType<FailingRecordNoMarshal> {
            init {
                RecordRegistry.registerType(FailingRecordNoMarshalType)
            }

            override val domain: String
                get() = "testing"
            override val codec: ByteArray
                get() = "doesn't matter".toByteArray()

            override fun unmarshalRecord(data: ByteArray): Result<FailingRecordNoMarshal> {
                return Err("unmarshal failed")
            }
        }
    }
}
