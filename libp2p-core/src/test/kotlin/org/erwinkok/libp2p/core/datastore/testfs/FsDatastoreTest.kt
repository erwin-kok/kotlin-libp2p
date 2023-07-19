// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore.testfs

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.test.runTest
import org.erwinkok.libp2p.core.datastore.Key
import org.erwinkok.libp2p.core.datastore.Key.Companion.key
import org.erwinkok.libp2p.core.datastore.query.Query
import org.erwinkok.libp2p.core.datastore.query.QueryResult
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

internal class FsDatastoreTest {

    @Test
    fun testBasic() = runTest {
        val ds = FsDatastore.newFsDatastore(this, Files.createTempDirectory("test-ds-").toFile()).expectNoErrors()
        val keys = listOf(
            key("foo"),
            key("foo/bar"),
            key("foo/bar/baz"),
            key("foo/barb"),
            key("foo/bar/bazb"),
            key("foo/bar/baz/barb"),
        )
        for (k in keys) {
            ds.put(k, k.bytes).expectNoErrors()
        }
        for (k in keys) {
            val actual = ds.get(k).expectNoErrors()
            assertArrayEquals(k.bytes, actual)
        }
        val result = ds.query(Query(prefix = "/foo/bar/")).expectNoErrors()
        checkResults(
            setOf(
                key("/foo/bar/baz"),
                key("/foo/bar/bazb"),
                key("/foo/bar/baz/barb"),
            ),
            result,
        )
        ds.close()
    }

    @Test
    fun testDiskUsage() = runTest {
        val ds = FsDatastore.newFsDatastore(this, Files.createTempDirectory("test-ds-").toFile()).expectNoErrors()
        val keys = listOf(
            key("foo"),
            key("foo/bar"),
            key("foo/bar/baz"),
            key("foo/barb"),
            key("foo/bar/bazb"),
            key("foo/bar/baz/barb"),
        )
        var totalBytes = 0L
        for (k in keys) {
            totalBytes += k.bytes.size
            ds.put(k, k.bytes).expectNoErrors()
        }
        assertEquals(totalBytes, ds.diskUsage().expectNoErrors())
        ds.close()
    }

    private suspend fun checkResults(expected: Set<Key>, actual: Flow<QueryResult>) {
        val keys = actual.map {
            val entry = it.entry
            require(entry != null && it.error == null)
            entry.key
        }.toSet()
        assertEquals(expected.size, keys.size)
        assertTrue(keys.containsAll(expected))
    }
}
