// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.peerstore

import org.erwinkok.libp2p.core.datastore.Batch
import org.erwinkok.libp2p.core.datastore.BatchingDatastore
import org.erwinkok.libp2p.core.datastore.Key
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.erwinkok.result.getOrElse
import org.erwinkok.result.map
import org.erwinkok.result.onFailure
import org.erwinkok.result.onSuccess

class CyclicBatch private constructor(private val ds: BatchingDatastore, private var batch: Batch?, private val threshold: Int) : Batch {
    private var pending = 0

    override suspend fun put(key: Key, value: ByteArray): Result<Unit> {
        val b = cycle().getOrElse { return Err(it) }
        pending++
        return b.put(key, value)
    }

    override suspend fun delete(key: Key): Result<Unit> {
        val b = cycle().getOrElse { return Err(it) }
        pending++
        return b.delete(key)
    }

    override suspend fun commit(): Result<Unit> {
        val b = batch ?: return Err("Cyclic batch is closed")
        b.commit().onFailure { return Err(it) }
        pending = 0
        batch = null
        return Ok(Unit)
    }

    private suspend fun cycle(): Result<Batch> {
        val b = batch ?: return Err("Cyclic batch is closed")
        if (pending < threshold) {
            return Ok(b)
        }
        b.commit()
            .onFailure {
                return Err("failed while committing cyclic batch: ${errorMessage(it)}")
            }
        return ds.batch()
            .onSuccess { batch = it }
    }

    companion object {
        fun create(ds: BatchingDatastore, threshold: Int): Result<CyclicBatch> {
            return ds.batch()
                .map { CyclicBatch(ds, it, threshold) }
        }
    }
}
