// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
@file:OptIn(ExperimentalSerializationApi::class)

package org.erwinkok.libp2p.core.peerstore

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.serializer
import mu.KotlinLogging
import org.erwinkok.libp2p.core.datastore.BatchingDatastore
import org.erwinkok.libp2p.core.datastore.Datastore
import org.erwinkok.libp2p.core.datastore.Key
import org.erwinkok.libp2p.core.datastore.Key.Companion.key
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.host.builder.PeerstoreConfig
import org.erwinkok.multiformat.multibase.bases.Base32
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.Err
import org.erwinkok.result.Error
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.flatMap
import org.erwinkok.result.getOrElse
import org.erwinkok.result.map
import org.erwinkok.result.mapIfError

private val logger = KotlinLogging.logger {}

class ProtocolsStore private constructor(
    private val datastore: BatchingDatastore,
    private val maxProtocols: Int,
) {
    private val protocolMutex = Array(256) { Mutex() }

    suspend fun getProtocols(peerId: PeerId): Result<Set<ProtocolId>> {
        mutex(peerId).withLock {
            return protocolMap(peerId)
        }
    }

    suspend fun addProtocols(peerId: PeerId, protocols: Set<ProtocolId>): Result<Unit> {
        mutex(peerId).withLock {
            val protocolMap = protocolMap(peerId)
                .getOrElse { return Err(it) }
            if ((protocolMap.size + protocols.size) > maxProtocols) {
                return Err(ErrTooManyProtocols)
            }
            protocolMap.addAll(protocols)
            return put(peerId, protocolMap)
        }
    }

    suspend fun setProtocols(peerId: PeerId, protocols: Set<ProtocolId>): Result<Unit> {
        if (protocols.size > maxProtocols) {
            return Err(ErrTooManyProtocols)
        }
        mutex(peerId).withLock {
            return put(peerId, protocols)
        }
    }

    suspend fun removeProtocols(peerId: PeerId, protocols: Set<ProtocolId>): Result<Unit> {
        mutex(peerId).withLock {
            val protocolMap = protocolMap(peerId)
                .getOrElse { return Err(it) }
            if (protocolMap.isEmpty()) {
                return Ok(Unit)
            }
            protocolMap.removeAll(protocols)
            return put(peerId, protocolMap)
        }
    }

    suspend fun supportsProtocols(peerId: PeerId, protocols: Set<ProtocolId>): Result<Set<ProtocolId>> {
        mutex(peerId).withLock {
            return protocolMap(peerId)
                .map { it.intersect(protocols) }
        }
    }

    suspend fun firstSupportedProtocol(peerId: PeerId, protocols: Set<ProtocolId>): Result<ProtocolId> {
        mutex(peerId).withLock {
            return protocolMap(peerId)
                .map { protocolMap -> protocolMap.firstOrNull { protocols.contains(it) } }
                .flatMap {
                    return if (it == null) {
                        logger.warn { "Peer $peerId does not support any of the requested protocols [${protocols.joinToString(", ")}]" }
                        Ok(ProtocolId.Null)
                    } else {
                        Ok(it)
                    }
                }
        }
    }

    suspend fun removePeer(peerId: PeerId) {
        mutex(peerId).withLock {
            datastore.delete(peerToKey(peerId))
        }
    }

    private fun mutex(peerId: PeerId): Mutex {
        val index = peerId.hashCode().toUByte().toInt()
        return protocolMutex[index]
    }

    private suspend fun protocolMap(peerId: PeerId): Result<MutableSet<ProtocolId>> {
        return get(peerId)
            .map { it.toMutableSet() }
            .mapIfError(Datastore.ErrNotFound) {
                mutableSetOf()
            }
    }

    private suspend fun get(peerId: PeerId): Result<Set<ProtocolId>> {
        val dbKey = peerToKey(peerId)
        return datastore.get(dbKey)
            .map { Cbor.decodeFromByteArray<Set<String>>(serializer(), it) }
            .map { set -> set.map { ProtocolId.from(it) }.toSet() }
    }

    private suspend fun put(peerId: PeerId, value: Set<ProtocolId>): Result<Unit> {
        val stringList = value.map { it.id }
        val bytes = Cbor.encodeToByteArray(serializer(), stringList)
        val dbKey = peerToKey(peerId)
        return datastore.put(dbKey, bytes).map { }
    }

    private fun peerToKey(peerId: PeerId): Key {
        val peerIdb32 = Base32.encodeStdLowerNoPad(peerId.idBytes())
        return key("$ProtocolsBase/$peerIdb32")
    }

    companion object {
        const val MaxProtocols = 1024
        private val ErrTooManyProtocols = Error("too many protocols")

        private const val ProtocolsBase = "/peers/protocols"

        fun create(datastore: BatchingDatastore, peerstoreConfig: PeerstoreConfig? = null): Result<ProtocolsStore> {
            val maxProtocols = peerstoreConfig?.maxProtocols ?: MaxProtocols
            return Ok(ProtocolsStore(datastore, maxProtocols))
        }
    }
}
