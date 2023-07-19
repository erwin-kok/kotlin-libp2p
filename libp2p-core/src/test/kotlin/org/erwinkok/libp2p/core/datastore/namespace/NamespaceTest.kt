// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore.namespace

import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.erwinkok.libp2p.core.datastore.Datastore
import org.erwinkok.libp2p.core.datastore.Key
import org.erwinkok.libp2p.core.datastore.Key.Companion.key
import org.erwinkok.libp2p.core.datastore.MapDatastore
import org.erwinkok.libp2p.core.datastore.query.Entry
import org.erwinkok.libp2p.core.datastore.query.Query
import org.erwinkok.libp2p.testing.testsuites.datastore.DatastoreTestSuite
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream

internal class NamespaceTest {
    @TestFactory
    fun testSuitePair(): Stream<DynamicTest> {
        return DatastoreTestSuite("Namespace").testBasic { scope -> Namespace.wrap(scope, MapDatastore(scope), key("/foo")) }
    }

    @Test
    fun testBasicAbc() {
        testBasic("abc")
    }

    @Test
    fun testBasic() {
        testBasic("")
    }

    @Test
    fun testQuery() = runTest {
        val mds = MapDatastore(this)
        val ds = Namespace.wrap(this, mds, key("/foo"))
        val keys = listOf(
            key("foo/bar"),
            key("foo/bar/baz"),
            key("foo/baz/abc"),
        )
        for (k in keys) {
            mds.put(k, k.toString().toByteArray()).expectNoErrors()
        }
        val qres1 = ds.query(query = Query()).expectNoErrors()
        val expect = listOf(
            Entry(key = key("/bar"), size = "/foo/bar".length, value = "/foo/bar".toByteArray()),
            Entry(key = key("/bar/baz"), size = "/foo/bar/baz".length, value = "/foo/bar/baz".toByteArray()),
            Entry(key = key("/baz/abc"), size = "/foo/bar/abc".length, value = "/foo/baz/abc".toByteArray()),
        )
        qres1.collect { result ->
            assertTrue(expect.any { it.key == result.entry!!.key })
        }

        val qres2 = ds.query(query = Query(prefix = "bar")).expectNoErrors()
        qres2.collect { result ->
            assertEquals(key("/bar/baz"), result.entry!!.key)
        }

        ds.close()
    }

    private fun testBasic(prefix: String) = runTest {
        val mds = MapDatastore(this)
        val ds = Namespace.wrap(this, mds, key(prefix))
        val keys = listOf(
            key("foo"),
            key("foo/bar"),
            key("foo/bar/baz"),
            key("foo/barb"),
            key("foo/bar/bazb"),
            key("foo/bar/baz/barb"),
        )
        for (k in keys) {
            ds.put(k, k.toString().toByteArray()).expectNoErrors()
        }
        for (k in keys) {
            val v1 = ds.get(k).expectNoErrors()
            val v2 = mds.get(key(prefix).child(k)).expectNoErrors()
            assertArrayEquals(v1, v2)
        }
        val test: suspend (Datastore, Query) -> List<Key> = { d, q ->
            val result = d.query(q).expectNoErrors()
            result.mapNotNull {
                it.entry?.key
            }.toList()
        }
        val listA = test(mds, Query()).sortedBy { it.toString() }
        val listB = test(ds, Query()).sortedBy { it.toString() }
        assertEquals(listA.size, listB.size)

        for (i in listA.indices) {
            val ka = listA[i]
            val kb = listB[i]
            assertEquals(ds.invertKey(ka), kb)
            assertEquals(ka, ds.convertKey(kb))
        }

        ds.close()
    }
}
