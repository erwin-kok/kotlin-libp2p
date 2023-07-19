// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.erwinkok.libp2p.core.datastore.Datastore.Companion.ErrNotFound
import org.erwinkok.libp2p.core.datastore.query.Entry
import org.erwinkok.libp2p.core.datastore.query.Query
import org.erwinkok.libp2p.core.datastore.query.QueryResult
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import java.util.concurrent.ConcurrentHashMap

class MapDatastore(
    scope: CoroutineScope,
) : BatchingDatastore {
    private val _context = Job(scope.coroutineContext[Job])
    private val map = ConcurrentHashMap<Key, ByteArray>()

    override val jobContext: Job
        get() = _context

    override suspend fun sync(prefix: Key): Result<Unit> {
        return Ok(Unit)
    }

    override suspend fun get(key: Key): Result<ByteArray> {
        val v = map[key] ?: return Err(ErrNotFound)
        return Ok(v)
    }

    override suspend fun put(key: Key, value: ByteArray): Result<Unit> {
        map[key] = value
        return Ok(Unit)
    }

    override suspend fun has(key: Key): Result<Boolean> {
        return Ok(map.containsKey(key))
    }

    override suspend fun getSize(key: Key): Result<Int> {
        val v = map[key] ?: return Err(ErrNotFound)
        return Ok(v.size)
    }

    override suspend fun delete(key: Key): Result<Unit> {
        map.remove(key)
        return Ok(Unit)
    }

    override fun query(query: Query): Result<Flow<QueryResult>> {
        return Ok(
            query.applyQueryResult(
                flow {
                    for ((k, v) in map) {
                        if (query.keysOnly == null || !query.keysOnly) {
                            emit(QueryResult(Entry(k, v, size = v.size)))
                        } else {
                            emit(QueryResult(Entry(k)))
                        }
                    }
                },
            ),
        )
    }

    override suspend fun batch(): Result<Batch> {
        return Ok(BasicBatch(this))
    }

    override fun close() {
        _context.complete()
    }
}
