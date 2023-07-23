// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore

import kotlinx.coroutines.flow.Flow
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.datastore.Datastore.Companion.ErrNotFound
import org.erwinkok.libp2p.core.datastore.query.Query
import org.erwinkok.libp2p.core.datastore.query.QueryResult
import org.erwinkok.result.Err
import org.erwinkok.result.Error
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.map
import org.erwinkok.result.mapBoth
import java.time.Instant
import kotlin.time.Duration

interface Datastore : AwaitableClosable, Read, Write {
    suspend fun sync(prefix: Key): Result<Unit>

    companion object {
        val ErrBatchUnsupported = Error("this datastore does not support batching")
        val ErrNotFound = Error("datastore: key not found")

        suspend fun diskUsage(ds: Datastore): Result<Long> {
            return if (ds is PersistentFeature) {
                ds.diskUsage()
            } else {
                Ok(0L)
            }
        }
    }
}

interface Read {
    suspend fun get(key: Key): Result<ByteArray>
    suspend fun has(key: Key): Result<Boolean> {
        return get(key)
            .mapBoth(
                {
                    Ok(true)
                },
                {
                    if (it == ErrNotFound) {
                        Ok(false)
                    } else {
                        Err(it)
                    }
                },
            )
    }

    suspend fun getSize(key: Key): Result<Int> {
        return get(key)
            .map { it.size }
    }

    fun query(query: Query): Result<Flow<QueryResult>>
}

interface Write {
    suspend fun put(key: Key, value: ByteArray): Result<Unit>
    suspend fun delete(key: Key): Result<Unit>
}

interface BatchingFeature {
    fun batch(): Result<Batch>
}

interface CheckedFeature {
    suspend fun check(): Result<Unit>
}

interface ScrubbedFeature {
    suspend fun scrub(): Result<Unit>
}

interface GcFeature {
    suspend fun collectGarbage(): Result<Unit>
}

interface PersistentFeature {
    // DiskUsage returns the space used by a datastore, in bytes.
    suspend fun diskUsage(): Result<Long>
}

interface TransactionFeature {
    suspend fun newTransaction(readOnly: Boolean): Result<Transaction>
}

// TTL encapsulates the methods that deal with entries with time-to-live.
interface TtlFeature {
    suspend fun putWithTTL(key: Key, value: ByteArray, ttl: Duration): Result<Unit>
    suspend fun setTTL(key: Key, ttl: Duration): Result<Unit>
    suspend fun getExpiration(key: Key): Result<Instant>
}

interface Transaction : Read, Write {
    suspend fun commit(): Result<Unit>
    suspend fun discard()
}

interface Batch : Write {
    suspend fun commit(): Result<Unit>
}

interface BatchingDatastore : Datastore, BatchingFeature

interface CheckedDatastore : Datastore, CheckedFeature

interface ScrubbedDatastore : Datastore, ScrubbedFeature

interface GcDatastore : Datastore, GcFeature

interface PersistentDatastore : Datastore, PersistentFeature

interface TTLDatastore : Datastore, TtlFeature

interface TransactionDatastore : Datastore, TransactionFeature

interface ShimDatastore : Datastore {
    suspend fun children(): Result<List<Datastore>>
}
