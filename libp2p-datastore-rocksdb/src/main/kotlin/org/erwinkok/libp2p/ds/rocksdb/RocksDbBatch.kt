// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.ds.rocksdb

import org.erwinkok.libp2p.core.datastore.Batch
import org.erwinkok.libp2p.core.datastore.Key
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.rocksdb.RocksDB
import org.rocksdb.RocksDBException
import org.rocksdb.WriteBatch
import org.rocksdb.WriteOptions

class RocksDbBatch(private val db: RocksDB) : Batch {
    private val batch = WriteBatch()

    override suspend fun put(key: Key, value: ByteArray): Result<Unit> {
        return try {
            batch.put(key.bytes, value)
            Ok(Unit)
        } catch (e: RocksDBException) {
            Err("Error putting value for key $key: ${errorMessage(e)}")
        }
    }

    override suspend fun delete(key: Key): Result<Unit> {
        return try {
            batch.delete(key.bytes)
            Ok(Unit)
        } catch (e: RocksDBException) {
            Err("Error deleting key $key: ${errorMessage(e)}")
        }
    }

    override suspend fun commit(): Result<Unit> {
        return try {
            db.write(WriteOptions(), batch)
            batch.close()
            Ok(Unit)
        } catch (e: RocksDBException) {
            Err("Error comtting batch: ${errorMessage(e)}")
        }
    }
}
