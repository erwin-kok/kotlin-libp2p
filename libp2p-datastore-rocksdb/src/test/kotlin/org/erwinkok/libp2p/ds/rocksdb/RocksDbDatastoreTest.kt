// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
@file:OptIn(ExperimentalPathApi::class)

package org.erwinkok.libp2p.ds.rocksdb

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.erwinkok.libp2p.core.datastore.Datastore
import org.erwinkok.libp2p.core.datastore.Key.Companion.key
import org.erwinkok.libp2p.core.datastore.PersistentDatastore
import org.erwinkok.libp2p.core.datastore.query.Query
import org.erwinkok.libp2p.core.datastore.query.QueryResult
import org.erwinkok.libp2p.testing.testsuites.datastore.DatastoreTestSuite
import org.erwinkok.result.coAssertErrorResult
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.pathString

internal class RocksDbDatastoreTest {
    private var tempDir: Path? = null

    @BeforeEach
    fun setup() {
        tempDir = Files.createTempDirectory("rocksdb-test-")
    }

    @AfterEach
    fun teardown() {
        tempDir?.deleteRecursively()
    }

    @TestFactory
    fun testSuiteBasic(): Stream<DynamicTest> {
        return DatastoreTestSuite("RocksDbDatastore").testBasic { scope -> RocksDbDatastore.create(scope, tempDir!!.pathString).expectNoErrors() }
    }

    @TestFactory
    fun testSuiteBatch(): Stream<DynamicTest> {
        return DatastoreTestSuite("RocksDbDatastore").testBatch { scope -> RocksDbDatastore.create(scope, tempDir!!.pathString).expectNoErrors() }
    }

    @Test
    fun testQuery() = runTest {
        val ds = RocksDbDatastore.create(this, tempDir!!.pathString).expectNoErrors()
        addTestCases(ds, testCases)
        val qr1 = ds.query(Query(prefix = "/a/")).expectNoErrors()
        expectMatches(
            listOf(
                "/a/b",
                "/a/b/c",
                "/a/b/d",
                "/a/c",
                "/a/d",
            ),
            qr1,
        )

        val qr2 = ds.query(Query(prefix = "/a/", offset = 2, limit = 2)).expectNoErrors()
        expectMatches(
            listOf(
                "/a/b/d",
                "/a/c",
            ),
            qr2,
        )

        ds.close()
    }

    @Test
    fun testHas() = runTest {
        val ds = RocksDbDatastore.create(this, tempDir!!.pathString).expectNoErrors()
        addTestCases(ds, testCases)
        assertTrue(ds.has(key("/a/b/c")).expectNoErrors())
        assertFalse(ds.has(key("/a/b/c/d")).expectNoErrors())
        ds.close()
    }

    @Test
    fun testGetSize() = runTest {
        val ds = RocksDbDatastore.create(this, tempDir!!.pathString).expectNoErrors()
        addTestCases(ds, testCases)
        assertEquals(testCases["/a/b/c"]!!.length, ds.getSize(key("/a/b/c")).expectNoErrors())
        coAssertErrorResult("datastore: key not found") { ds.getSize(key("/a/b/c/d")) }
        ds.close()
    }

    @Test
    fun testNotExistGet() = runTest {
        val ds = RocksDbDatastore.create(this, tempDir!!.pathString).expectNoErrors()
        addTestCases(ds, testCases)
        assertFalse(ds.has(key("/a/b/c/d")).expectNoErrors())
        coAssertErrorResult("datastore: key not found") { ds.get(key("/a/b/c/d")) }
        ds.close()
    }

    @Test
    fun testDelete() = runTest {
        val ds = RocksDbDatastore.create(this, tempDir!!.pathString).expectNoErrors()
        addTestCases(ds, testCases)
        assertTrue(ds.has(key("/a/b/c")).expectNoErrors())
        ds.delete(key("/a/b/c")).expectNoErrors()
        assertFalse(ds.has(key("/a/b/c")).expectNoErrors())
        ds.close()
    }

    @Test
    fun testGetEmpty() = runTest {
        val ds = RocksDbDatastore.create(this, tempDir!!.pathString).expectNoErrors()
        ds.put(key("/a"), byteArrayOf()).expectNoErrors()
        val v = ds.get(key("/a")).expectNoErrors()
        assertEquals(0, v.size)
        ds.close()
    }

    @Test
    fun testDiskUsage() = runTest {
        val ds = RocksDbDatastore.create(this, tempDir!!.pathString).expectNoErrors()
        addTestCases(ds, testCases)
        val pds = ds as? PersistentDatastore
        assertNotNull(pds)
        val size = pds!!.diskUsage().expectNoErrors()
        assertTrue(size > 0)
        ds.close()
    }

    private val testCases = mapOf(
        "/a" to "a",
        "/a/b" to "ab",
        "/a/b/c" to "abc",
        "/a/b/d" to "a/b/d",
        "/a/c" to "ac",
        "/a/d" to "ad",
        "/e" to "e",
        "/f" to "f",
        "/g" to "",
    )

    private suspend fun addTestCases(ds: Datastore, cases: Map<String, String>) {
        for ((k, v) in cases) {
            ds.put(key(k), v.toByteArray()).expectNoErrors()
        }
        for ((k, v) in cases) {
            val value = ds.get(key(k)).expectNoErrors()
            assertArrayEquals(v.toByteArray(), value)
        }
    }

    private suspend fun expectMatches(expect: List<String>, actualQr: Flow<QueryResult>) {
        val actual = actualQr.map { it.entry!!.key.toString() }.toList()
        assertEquals(expect.size, actual.size)
        assertTrue(actual.containsAll(expect))
    }
}
