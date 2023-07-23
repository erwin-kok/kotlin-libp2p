// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
@file:OptIn(ExperimentalSerializationApi::class)

package org.erwinkok.libp2p.core.peerstore

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.serializer
import mu.KotlinLogging
import org.erwinkok.libp2p.core.datastore.BatchingDatastore
import org.erwinkok.libp2p.core.datastore.Key
import org.erwinkok.libp2p.core.datastore.Key.Companion.key
import org.erwinkok.libp2p.core.datastore.query.Query
import org.erwinkok.libp2p.core.datastore.query.queryFilterError
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.multiformat.multibase.bases.Base32
import org.erwinkok.result.Err
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.erwinkok.result.map
import org.erwinkok.result.onFailure
import org.erwinkok.result.onSuccess

private val logger = KotlinLogging.logger {}

class MetadataStore(val datastore: BatchingDatastore) {
    suspend inline fun <reified T> get(peerId: PeerId, key: String): Result<T> {
        val dbKey = peerToKey(peerId, key)
        return try {
            datastore.get(dbKey)
                .map { Cbor.decodeFromByteArray(serializer(), it) }
        } catch (e: SerializationException) {
            Err("Could not serialize value: ${errorMessage(e)}")
        }
    }

    suspend inline fun <reified T> put(peerId: PeerId, key: String, value: T): Result<Unit> {
        return try {
            val bytes = Cbor.encodeToByteArray(serializer(), value)
            val dbKey = peerToKey(peerId, key)
            datastore.put(dbKey, bytes).map { }
        } catch (e: SerializationException) {
            Err("Could not serialize value: ${errorMessage(e)}")
        }
    }

    suspend fun removePeer(peerId: PeerId) {
        // Remove metadata for peer (includes protocol data)
        datastore.batch()
            .onSuccess { batch ->
                datastore.query(Query(prefix = peerToPrefix(peerId), keysOnly = true))
                    .onSuccess {
                        it
                            .queryFilterError { logger.error { "Error retrieving key: ${errorMessage(it)}" } }
                            .map { result -> result.key }
                            .onCompletion { batch.commit() }
                            .collect { key -> batch.delete(key) }
                    }
            }
            .onFailure {
                logger.error { "Could not remove peer: ${errorMessage(it)}" }
            }
    }

    fun peerToKey(peerId: PeerId, suffix: String): Key {
        return key("${peerToPrefix(peerId)}/$suffix")
    }

    private fun peerToPrefix(peerId: PeerId): String {
        val peerIdb32 = Base32.encodeStdLowerNoPad(peerId.idBytes())
        return "$MetadataBase/$peerIdb32"
    }

    companion object {
        const val MetadataBase = "/peers/metadata"
    }
}
