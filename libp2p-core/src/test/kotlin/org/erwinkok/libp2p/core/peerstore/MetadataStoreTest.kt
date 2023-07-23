// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
@file:OptIn(ExperimentalCoroutinesApi::class)

package org.erwinkok.libp2p.core.peerstore

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.erwinkok.libp2p.core.datastore.Batch
import org.erwinkok.libp2p.core.datastore.BatchingDatastore
import org.erwinkok.libp2p.core.datastore.Key
import org.erwinkok.libp2p.core.datastore.Key.Companion.key
import org.erwinkok.libp2p.core.datastore.MapDatastore
import org.erwinkok.libp2p.core.datastore.query.Entry
import org.erwinkok.libp2p.core.datastore.query.Query
import org.erwinkok.libp2p.core.datastore.query.QueryResult
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.multiformat.multibase.bases.Base32
import org.erwinkok.result.Ok
import org.erwinkok.result.coAssertErrorResult
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class MetadataStoreTest {
    @Serializable
    private data class Person(
        val name: String,
        val age: Int,
    )

    private val peerId = LocalIdentity.random().expectNoErrors().peerId
    private val peerIdB32 = Base32.encodeStdLowerNoPad(peerId.idBytes())
    private val dbKey = key("/peers/metadata/$peerIdB32/aKey")
    private val keySlot = slot<Key>()
    private val bytesSlot = slot<ByteArray>()
    private val querySlot = slot<Query>()
    private val datastoreMock = mockk<BatchingDatastore>()
    private val batchMock = mockk<Batch>()
    private val metadataStore = MetadataStore(datastoreMock)

    @BeforeEach
    fun setUp() {
        coEvery { datastoreMock.put(capture(keySlot), capture(bytesSlot)) } returns Ok(Unit)
        coEvery { datastoreMock.get(capture(keySlot)) } returns Ok(byteArrayOf(102, 97, 86, 97, 108, 117, 101))
        coEvery { datastoreMock.query(capture(querySlot)) } returns Ok(flowOf(QueryResult(Entry(key = dbKey))))
        coEvery { datastoreMock.batch() } returns Ok(batchMock)
        coEvery { batchMock.delete(capture(keySlot)) } returns Ok(Unit)
        coEvery { batchMock.commit() } returns Ok(Unit)
    }

    @Test
    fun putStoresItemInDatastore() = runTest {
        metadataStore.put(peerId, "aKey", "aValue")
        coVerify { datastoreMock.put(dbKey, any()) }
        assertEquals(dbKey, keySlot.captured)
        assertArrayEquals(byteArrayOf(102, 97, 86, 97, 108, 117, 101), bytesSlot.captured)
    }

    @Test
    fun getRetrievesItemFromDatastore() = runTest {
        val actual = metadataStore.get<String>(peerId, "aKey").expectNoErrors()
        coVerify { datastoreMock.get(dbKey) }
        assertEquals(dbKey, keySlot.captured)
        assertEquals("aValue", actual)
    }

    @Test
    fun removePeerDeletesItemInDatastore() = runTest {
        metadataStore.put(peerId, "aKey", "aValue")
        metadataStore.removePeer(peerId)
        coVerify { datastoreMock.query(any()) }
        coVerify { datastoreMock.batch() }
        coVerify { batchMock.delete(dbKey) }
        coVerify { batchMock.commit() }
        assertEquals(dbKey, keySlot.captured)
        assertEquals(Query(prefix = "/peers/metadata/$peerIdB32", keysOnly = true), querySlot.captured)
    }

    @Test
    fun putAndGetString() = runTest {
        val memoryDatastore = MapDatastore(this)
        val memoryMetadataStore = MetadataStore(memoryDatastore)
        memoryMetadataStore.put(peerId, "aKey", "This is a TEST!").expectNoErrors()
        val actual = memoryMetadataStore.get<String>(peerId, "aKey").expectNoErrors()
        assertEquals("This is a TEST!", actual)
        memoryDatastore.close()
    }

    @Test
    fun putAndGetStringList() = runTest {
        val memoryDatastore = MapDatastore(this)
        val memoryMetadataStore = MetadataStore(memoryDatastore)
        val expected = listOf("This", "is", "a", "value")
        memoryMetadataStore.put(peerId, "aKey", expected).expectNoErrors()
        val actual = memoryMetadataStore.get<List<String>>(peerId, "aKey").expectNoErrors()
        assertEquals(expected, actual)
        memoryDatastore.close()
    }

    @Test
    fun putAndGetPersonList() = runTest {
        val memoryDatastore = MapDatastore(this)
        val memoryMetadataStore = MetadataStore(memoryDatastore)
        val person1 = Person("Piet", 42)
        val person2 = Person("Marie", 23)
        val person3 = Person("Jantje", 3)
        val expected = listOf(person1, person2, person3)
        memoryMetadataStore.put(peerId, "aKey", expected).expectNoErrors()
        val actual = memoryMetadataStore.get<List<Person>>(peerId, "aKey").expectNoErrors()
        assertEquals(expected, actual)
        memoryDatastore.close()
    }

    @Test
    fun putUnserializableGivesError() = runTest {
        val memoryDatastore = MapDatastore(this)
        val memoryMetadataStore = MetadataStore(memoryDatastore)
        coAssertErrorResult(
            "Could not serialize value: Serializer for class 'org.erwinkok.libp2p.core.host.PeerId' is not found.\n" +
                "Please ensure that class is marked as '@Serializable' and that the serialization compiler plugin is applied.\n",
        ) { memoryMetadataStore.put(peerId, "aKey", peerId) }
        memoryDatastore.close()
    }

    @Test
    fun getUnserializableGivesError() = runTest {
        val memoryDatastore = MapDatastore(this)
        val memoryMetadataStore = MetadataStore(memoryDatastore)
        memoryMetadataStore.put(peerId, "TestKey", "DontCare")
        coAssertErrorResult(
            "Could not serialize value: Serializer for class 'org.erwinkok.libp2p.core.host.PeerId' is not found.\n" +
                "Please ensure that class is marked as '@Serializable' and that the serialization compiler plugin is applied.\n",
        ) { memoryMetadataStore.get<PeerId>(peerId, "TestKey") }
        memoryDatastore.close()
    }
}
