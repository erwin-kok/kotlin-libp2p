// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore

import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.getOrElse
import java.util.concurrent.ConcurrentHashMap

class BasicBatch(private val target: Datastore) : Batch {
    data class Op(val delete: Boolean? = null, val value: ByteArray? = null)

    private val ops = ConcurrentHashMap<Key, Op>()

    override suspend fun commit(): Result<Unit> {
        for ((k, op) in ops) {
            if (op.delete != null && op.delete) {
                target.delete(k)
                    .getOrElse { return Err(it) }
            } else if (op.value != null) {
                target.put(k, op.value)
                    .getOrElse { return Err(it) }
            }
        }
        return Ok(Unit)
    }

    override suspend fun put(key: Key, value: ByteArray): Result<Unit> {
        ops[key] = Op(value = value)
        return Ok(Unit)
    }

    override suspend fun delete(key: Key): Result<Unit> {
        ops[key] = Op(delete = true)
        return Ok(Unit)
    }
}
