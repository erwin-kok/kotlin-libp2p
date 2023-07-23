// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore.testfs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.erwinkok.libp2p.core.datastore.BasicBatch
import org.erwinkok.libp2p.core.datastore.Batch
import org.erwinkok.libp2p.core.datastore.BatchingFeature
import org.erwinkok.libp2p.core.datastore.Datastore
import org.erwinkok.libp2p.core.datastore.Datastore.Companion.ErrNotFound
import org.erwinkok.libp2p.core.datastore.Key
import org.erwinkok.libp2p.core.datastore.Key.Companion.key
import org.erwinkok.libp2p.core.datastore.PersistentFeature
import org.erwinkok.libp2p.core.datastore.query.Entry
import org.erwinkok.libp2p.core.datastore.query.Query
import org.erwinkok.libp2p.core.datastore.query.QueryResult
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.erwinkok.result.mapBoth
import java.io.File
import java.io.IOException

class FsDatastore private constructor(
    scope: CoroutineScope,
    private val path: File,
) : Datastore, BatchingFeature, PersistentFeature {
    private val _context = Job(scope.coroutineContext[Job])

    override val jobContext: Job
        get() = _context

    override suspend fun sync(prefix: Key): Result<Unit> {
        return Ok(Unit)
    }

    override suspend fun get(key: Key): Result<ByteArray> {
        val file = keyFilename(key)
        if (!file.isFile) {
            return Err(ErrNotFound)
        }
        return Ok(file.readBytes())
    }

    override fun query(query: Query): Result<Flow<QueryResult>> {
        val keys = path.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(path) }
            .map { it.path.removeSuffix(objectKeySuffix) }
            .map { key(it) }
        return Ok(
            query.applyQueryResult(
                flow {
                    for (key in keys) {
                        if (query.keysOnly == null || !query.keysOnly) {
                            get(key).mapBoth(
                                { emit(QueryResult(Entry(key, it, size = it.size))) },
                                { emit(QueryResult(error = it)) },
                            )
                        } else {
                            emit(QueryResult(Entry(key)))
                        }
                    }
                },
            ),
        )
    }

    override suspend fun put(key: Key, value: ByteArray): Result<Unit> {
        val file = keyFilename(key)
        file.parentFile.mkdirs()
        try {
            file.createNewFile()
        } catch (e: IOException) {
            return Err("Could not create $key: ${errorMessage(e)}")
        }
        file.writeBytes(value)
        return Ok(Unit)
    }

    override suspend fun delete(key: Key): Result<Unit> {
        val file = keyFilename(key)
        if (!file.isFile) {
            return Err(ErrNotFound)
        }
        file.delete()
        return Ok(Unit)
    }

    override fun batch(): Result<Batch> {
        return Ok(BasicBatch(this))
    }

    override suspend fun diskUsage(): Result<Long> {
        return Ok(path.walkTopDown().filter { it.isFile }.map { it.length() }.sum())
    }

    override fun close() {
        _context.complete()
    }

    private fun keyFilename(key: Key): File {
        return File(path, key.toString()).resolve(objectKeySuffix)
    }

    companion object {
        const val objectKeySuffix = ".dsobject"

        fun newFsDatastore(scope: CoroutineScope, file: File): Result<FsDatastore> {
            return if (file.isDirectory) {
                Ok(FsDatastore(scope, file))
            } else {
                Err("$file is not a directory")
            }
        }

        fun newFsDatastore(scope: CoroutineScope, path: String): Result<FsDatastore> {
            return newFsDatastore(scope, File(path))
        }
    }
}
