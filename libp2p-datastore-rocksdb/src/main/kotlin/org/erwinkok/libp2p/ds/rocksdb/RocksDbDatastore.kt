// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.ds.rocksdb

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mu.KotlinLogging
import org.erwinkok.libp2p.core.datastore.Batch
import org.erwinkok.libp2p.core.datastore.BatchingDatastore
import org.erwinkok.libp2p.core.datastore.Datastore
import org.erwinkok.libp2p.core.datastore.Key
import org.erwinkok.libp2p.core.datastore.Key.Companion.key
import org.erwinkok.libp2p.core.datastore.PersistentDatastore
import org.erwinkok.libp2p.core.datastore.query.Entry
import org.erwinkok.libp2p.core.datastore.query.Query
import org.erwinkok.libp2p.core.datastore.query.QueryResult
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.rocksdb.FlushOptions
import org.rocksdb.Options
import org.rocksdb.RocksDB
import org.rocksdb.RocksDBException
import java.io.File
import java.io.IOException

private val logger = KotlinLogging.logger {}

class RocksDbDatastore(
    scope: CoroutineScope,
    private val db: RocksDB,
    private val path: String,
) : BatchingDatastore, PersistentDatastore {
    private val _context = Job(scope.coroutineContext[Job])

    override val jobContext: Job get() = _context

    override suspend fun get(key: Key): Result<ByteArray> {
        return try {
            val value = db.get(key.bytes) ?: return Err(Datastore.ErrNotFound)
            Ok(value)
        } catch (e: RocksDBException) {
            Err("Error getting value for key $key: ${errorMessage(e)}")
        }
    }

    override suspend fun put(key: Key, value: ByteArray): Result<Unit> {
        return try {
            db.put(key.bytes, value)
            Ok(Unit)
        } catch (e: RocksDBException) {
            Err("Error putting value for key $key: ${errorMessage(e)}")
        }
    }

    override suspend fun delete(key: Key): Result<Unit> {
        return try {
            db.delete(key.bytes)
            Ok(Unit)
        } catch (e: RocksDBException) {
            Err("Error deleting key $key: ${errorMessage(e)}")
        }
    }

    override fun batch(): Result<Batch> {
        return Ok(RocksDbBatch(db))
    }

    override fun query(query: Query): Result<Flow<QueryResult>> {
        val iterator = db.newIterator()
        iterator.seekToFirst()
        return Ok(
            query.applyQueryResult(
                flow {
                    while (iterator.isValid) {
                        iterator.status()
                        val key = key(String(iterator.key()))
                        val keysOnly = query.keysOnly
                        if (keysOnly == null || !keysOnly) {
                            val value = iterator.value()
                            emit(QueryResult(Entry(key, value, size = value.size)))
                        } else {
                            emit(QueryResult(Entry(key)))
                        }
                        iterator.next()
                    }
                    iterator.close()
                },
            ),
        )
    }

    override suspend fun sync(prefix: Key): Result<Unit> {
        return try {
            db.flush(FlushOptions())
            db.syncWal()
            Ok(Unit)
        } catch (e: RocksDBException) {
            Err("Error flushing: ${errorMessage(e)}")
        }
    }

    override suspend fun diskUsage(): Result<Long> {
        return Ok(File(path).walkTopDown().filter { it.isFile }.map { it.length() }.sum())
    }

    override fun close() {
        db.syncWal()
        db.close()
        _context.complete()
    }

    companion object {
        init {
            RocksDB.loadLibrary()
        }

        fun create(scope: CoroutineScope, path: String, options: Options? = null): Result<BatchingDatastore> {
            val opts = options ?: defaultOptions()
            return try {
                val db = RocksDB.open(opts, path)
                Ok(RocksDbDatastore(scope, db, path))
            } catch (e: IOException) {
                Err("Could not open RocksDb datastore: ${errorMessage(e)}")
            } catch (e: RocksDBException) {
                Err("Could not open RocksDb datastore: ${errorMessage(e)}")
            }
        }

        private fun defaultOptions(): Options {
            val options = Options()
            options.setCreateIfMissing(true)
            options.setIncreaseParallelism(4)
            options.setUseFsync(false)
            return options
        }
    }
}
