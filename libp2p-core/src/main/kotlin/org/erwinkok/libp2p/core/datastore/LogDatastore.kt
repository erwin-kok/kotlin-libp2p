// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import org.erwinkok.libp2p.core.datastore.Datastore.Companion.ErrBatchUnsupported
import org.erwinkok.libp2p.core.datastore.query.Query
import org.erwinkok.libp2p.core.datastore.query.QueryResult
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.getOrElse

private val logger = KotlinLogging.logger {}

class LogDatastore(
    scope: CoroutineScope,
    private val name: String,
    private val ds: Datastore,
) : BatchingDatastore, GcDatastore, PersistentDatastore, ScrubbedDatastore, CheckedDatastore, ShimDatastore {
    class LogBatch(private val name: String, private val batch: Batch) : Batch {
        override suspend fun commit(): Result<Unit> {
            logger.info { "$name: BatchCommit" }
            return batch.commit()
        }

        override suspend fun put(key: Key, value: ByteArray): Result<Unit> {
            logger.info { "$name: BatchPut $key" }
            return batch.put(key, value)
        }

        override suspend fun delete(key: Key): Result<Unit> {
            logger.info { "$name: BatchDelete $key" }
            return batch.delete(key)
        }
    }

    private val _context = Job(scope.coroutineContext[Job])

    override val jobContext: Job
        get() = _context

    override suspend fun sync(prefix: Key): Result<Unit> {
        logger.info { "$name: sync $prefix" }
        return ds.sync(prefix)
    }

    override suspend fun get(key: Key): Result<ByteArray> {
        logger.info { "$name: get $key" }
        return ds.get(key)
    }

    override suspend fun put(key: Key, value: ByteArray): Result<Unit> {
        logger.info { "$name: put $key" }
        return ds.put(key, value)
    }

    override suspend fun delete(key: Key): Result<Unit> {
        logger.info { "$name: delete $key" }
        return ds.delete(key)
    }

    override suspend fun has(key: Key): Result<Boolean> {
        logger.info { "$name: has $key" }
        return ds.has(key)
    }

    override suspend fun getSize(key: Key): Result<Int> {
        logger.info { "$name: getSize $key" }
        return ds.getSize(key)
    }

    override fun query(query: Query): Result<Flow<QueryResult>> {
        logger.info { "$name: Query: " }
        logger.info { "$name: q.Prefix: ${query.prefix}" }
        logger.info { "$name: q.KeysOnly: ${query.keysOnly}" }
        logger.info { "$name: q.Filters: ${query.filters?.size}" }
        logger.info { "$name: q.Orders: ${query.orders?.size}" }
        logger.info { "$name: q.Offset: ${query.offset}" }
        return ds.query(query)
    }

    override fun batch(): Result<Batch> {
        logger.info { "$name: batch" }
        if (ds is BatchingFeature) {
            val batch = ds.batch()
                .getOrElse { return Err(it) }
            return Ok(LogBatch(name, batch))
        } else {
            return Err(ErrBatchUnsupported)
        }
    }

    override suspend fun check(): Result<Unit> {
        return if (ds is CheckedFeature) {
            ds.check()
        } else {
            Ok(Unit)
        }
    }

    override suspend fun scrub(): Result<Unit> {
        return if (ds is ScrubbedFeature) {
            ds.scrub()
        } else {
            Ok(Unit)
        }
    }

    override suspend fun collectGarbage(): Result<Unit> {
        return if (ds is GcFeature) {
            ds.collectGarbage()
        } else {
            Ok(Unit)
        }
    }

    override suspend fun diskUsage(): Result<Long> {
        logger.info { "$name: diskUsage" }
        return Datastore.diskUsage(this)
    }

    override suspend fun children(): Result<List<Datastore>> {
        return Ok(listOf(ds))
    }

    override fun close() {
        logger.info { "$name: close" }
        ds.close()
        _context.complete()
    }
}
