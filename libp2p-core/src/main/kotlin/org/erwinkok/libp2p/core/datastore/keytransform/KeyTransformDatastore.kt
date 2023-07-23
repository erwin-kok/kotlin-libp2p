// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore.keytransform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.erwinkok.libp2p.core.datastore.Batch
import org.erwinkok.libp2p.core.datastore.BatchingDatastore
import org.erwinkok.libp2p.core.datastore.BatchingFeature
import org.erwinkok.libp2p.core.datastore.CheckedDatastore
import org.erwinkok.libp2p.core.datastore.CheckedFeature
import org.erwinkok.libp2p.core.datastore.Datastore
import org.erwinkok.libp2p.core.datastore.Datastore.Companion.ErrBatchUnsupported
import org.erwinkok.libp2p.core.datastore.GcDatastore
import org.erwinkok.libp2p.core.datastore.GcFeature
import org.erwinkok.libp2p.core.datastore.Key
import org.erwinkok.libp2p.core.datastore.Key.Companion.key
import org.erwinkok.libp2p.core.datastore.PersistentDatastore
import org.erwinkok.libp2p.core.datastore.ScrubbedDatastore
import org.erwinkok.libp2p.core.datastore.ScrubbedFeature
import org.erwinkok.libp2p.core.datastore.ShimDatastore
import org.erwinkok.libp2p.core.datastore.query.Entry
import org.erwinkok.libp2p.core.datastore.query.Query
import org.erwinkok.libp2p.core.datastore.query.QueryResult
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.getOrElse
import org.erwinkok.result.map

class KeyTransformDatastore(
    scope: CoroutineScope,
    private val ds: Datastore,
    private val ks: KeyTransform,
) : BatchingDatastore, BatchingFeature, ShimDatastore, PersistentDatastore, CheckedDatastore, ScrubbedDatastore, GcDatastore, KeyTransform by ks {
    private val _context = Job(scope.coroutineContext[Job])

    override val jobContext: Job
        get() = _context

    override suspend fun sync(prefix: Key): Result<Unit> {
        return ds.sync(ks.convertKey(prefix))
    }

    override suspend fun get(key: Key): Result<ByteArray> {
        return ds.get(ks.convertKey(key))
    }

    override suspend fun has(key: Key): Result<Boolean> {
        return ds.has(ks.convertKey(key))
    }

    override suspend fun getSize(key: Key): Result<Int> {
        return ds.getSize(ks.convertKey(key))
    }

    override suspend fun put(key: Key, value: ByteArray): Result<Unit> {
        return ds.put(ks.convertKey(key), value)
    }

    override suspend fun delete(key: Key): Result<Unit> {
        return ds.delete(ks.convertKey(key))
    }

    override suspend fun diskUsage(): Result<Long> {
        return Datastore.diskUsage(ds)
    }

    override suspend fun collectGarbage(): Result<Unit> {
        if (ds is GcFeature) {
            return ds.collectGarbage()
        }
        return Ok(Unit)
    }

    override suspend fun scrub(): Result<Unit> {
        if (ds is ScrubbedFeature) {
            return ds.scrub()
        }
        return Ok(Unit)
    }

    override suspend fun check(): Result<Unit> {
        if (ds is CheckedFeature) {
            return ds.check()
        }
        return Ok(Unit)
    }

    override fun batch(): Result<Batch> {
        if (ds !is BatchingFeature) {
            return Err(ErrBatchUnsupported)
        }
        return ds.batch()
            .map { childBatch ->
                TransformBatch(childBatch) {
                    ks.convertKey(it)
                }
            }
    }

    override fun query(query: Query): Result<Flow<QueryResult>> {
        val prefix = if (query.prefix != null) ks.convertKey(key(query.prefix)).toString() else null
        val child = Query(
            prefix = prefix,
            filters = null,
            orders = null,
            limit = null,
            offset = null,
            keysOnly = query.keysOnly,
            returnExpirations = query.returnExpirations,
            returnsSizes = query.returnsSizes,
        )
        val childResult = ds.query(child)
            .getOrElse { return Err(it) }
        val naive = Query(
            prefix = null,
            filters = query.filters,
            orders = query.orders,
            limit = query.limit,
            offset = query.offset,
            keysOnly = null,
            returnExpirations = null,
            returnsSizes = null,
        )
        return Ok(
            naive.applyQueryResult(
                flow {
                    childResult.collect {
                        if (it.error != null) {
                            emit(QueryResult(error = it.error))
                        } else if (it.entry != null) {
                            emit(
                                QueryResult(
                                    entry = Entry(
                                        ks.invertKey(it.entry.key),
                                        it.entry.value,
                                        it.entry.expiration,
                                        it.entry.size,
                                    ),
                                ),
                            )
                        }
                    }
                },
            ),
        )
    }

    override suspend fun children(): Result<List<Datastore>> {
        return Ok(listOf(ds))
    }

    override fun close() {
        ds.close()
        _context.complete()
    }

    companion object {
        fun wrap(scope: CoroutineScope, child: Datastore, transform: KeyTransform): KeyTransformDatastore {
            return KeyTransformDatastore(scope, child, transform)
        }
    }
}
