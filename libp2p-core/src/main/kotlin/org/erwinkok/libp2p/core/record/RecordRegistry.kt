// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.record

import org.erwinkok.result.Err
import org.erwinkok.result.Result
import org.erwinkok.result.map
import org.erwinkok.util.Hex
import kotlin.reflect.KClass

object RecordRegistry {
    val payloadTypeRegistry = mutableMapOf<Int, RecordType<out Record>>()
    val recordTypeRegistry = mutableMapOf<KClass<out Record>, RecordType<out Record>>()

    inline fun <reified T : Record> registerType(type: RecordType<T>) {
        payloadTypeRegistry[type.codec.contentHashCode()] = type
        recordTypeRegistry[T::class] = type
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Record> recordTypeOf(): RecordType<T>? {
        return recordTypeRegistry[T::class] as? RecordType<T>
    }

    fun recordTypeOf(kclass: KClass<out Record>): RecordType<out Record>? {
        return recordTypeRegistry[kclass]
    }

    inline fun <reified T : Record> unmarshalRecordPayload(payloadType: ByteArray, payloadBytes: ByteArray): Result<T> {
        val hash = payloadType.contentHashCode()
        val type = payloadTypeRegistry[hash] ?: return Err("payload type '0x${Hex.encode(payloadType)}' is not registered")
        return type.unmarshalRecord(payloadBytes)
            .map { it as T }
    }
}
