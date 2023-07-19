// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.record

import org.erwinkok.libp2p.core.record.TestPayload.TestPayloadType.testPayloadType
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.assertErrorResult
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class RecordTest {
    @Test
    fun unmarshalPayload() {
        assertErrorResult("payload type is not registered") { RecordRegistry.unmarshalRecordPayload("unknown type".toByteArray(), byteArrayOf()) }
    }

    @Test
    fun callsUnmarshalRecordOnConcreteRecordType() {
        val testRecord = RecordRegistry.unmarshalRecordPayload<TestPayload>(testPayloadType, byteArrayOf()).expectNoErrors()
        assertTrue(testRecord.unmarshalPayloadCalled)
    }
}

class TestPayload(val unmarshalPayloadCalled: Boolean) : Record {
    override fun marshalRecord(): Result<ByteArray> {
        return Ok("hello".toByteArray())
    }

    companion object TestPayloadType : RecordType<TestPayload> {
        var testPayloadType = "/libp2p/test/record/payload-type".toByteArray()

        init {
            RecordRegistry.registerType(TestPayloadType)
        }

        override val domain: String
            get() = "testing"
        override val codec: ByteArray
            get() = testPayloadType

        override fun unmarshalRecord(data: ByteArray): Result<TestPayload> {
            return Ok(TestPayload(true))
        }
    }
}
