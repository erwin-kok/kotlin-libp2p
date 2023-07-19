// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.record

import org.erwinkok.result.Result

interface Record {
    fun marshalRecord(): Result<ByteArray>
}

interface RecordType<T : Record> {
    val domain: String
    val codec: ByteArray
    fun unmarshalRecord(data: ByteArray): Result<T>
}
