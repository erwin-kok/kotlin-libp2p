// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore.keytransform

import org.erwinkok.libp2p.core.datastore.Batch
import org.erwinkok.libp2p.core.datastore.Key
import org.erwinkok.result.Result

class TransformBatch(private val childBatch: Batch, private val convertKey: (Key) -> Key) : Batch {
    override suspend fun commit(): Result<Unit> {
        return childBatch.commit()
    }

    override suspend fun put(key: Key, value: ByteArray): Result<Unit> {
        return childBatch.put(convertKey(key), value)
    }

    override suspend fun delete(key: Key): Result<Unit> {
        return childBatch.delete(convertKey(key))
    }
}
