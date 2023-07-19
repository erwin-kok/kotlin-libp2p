// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore.keytransform

import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.erwinkok.libp2p.core.datastore.Datastore
import org.erwinkok.libp2p.core.datastore.Key
import org.erwinkok.libp2p.core.datastore.Key.Companion.key
import org.erwinkok.libp2p.core.datastore.MapDatastore
import org.erwinkok.libp2p.core.datastore.query.Query
import org.erwinkok.libp2p.testing.testsuites.datastore.DatastoreTestSuite
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream

internal class KeyTransformDatastoreTest {
    private val pair = object : KeyTransform {
        override fun convertKey(key: Key): Key {
            return key("/abc").child(key)
        }

        override fun invertKey(key: Key): Key {
            val list = key.list()
            require(list[0] == "abc")
            return Key.withNamespaces(list.subList(1, list.size))
        }
    }

    @TestFactory
    fun testSuitePair(): Stream<DynamicTest> {
        return DatastoreTestSuite("KeyTransformDatastore").testBasic { scope -> KeyTransformDatastore(scope, MapDatastore(scope), pair) }
    }

    @TestFactory
    fun testSuiteBatchPair(): Stream<DynamicTest> {
        return DatastoreTestSuite("KeyTransformDatastore").testBatch { scope -> KeyTransformDatastore(scope, MapDatastore(scope), pair) }
    }

    @TestFactory
    fun testSuitePrefixTransform(): Stream<DynamicTest> {
        return DatastoreTestSuite("KeyTransformDatastore").testBasic { scope -> KeyTransformDatastore(scope, MapDatastore(scope), PrefixTransform(key("/foo"))) }
    }

    @TestFactory
    fun testSuiteBatchPrefixTransform(): Stream<DynamicTest> {
        return DatastoreTestSuite("KeyTransformDatastore").testBatch { scope -> KeyTransformDatastore(scope, MapDatastore(scope), PrefixTransform(key("/foo"))) }
    }

    @Test
    fun testBasic() = runTest {
        val mds = MapDatastore(this)
        val ds = KeyTransformDatastore(this, mds, pair)
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
            val v2 = mds.get(key("abc").child(k)).expectNoErrors()
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
            assertEquals(pair.invertKey(ka), kb)
            assertEquals(ka, pair.convertKey(kb))
        }

        ds.close()
    }
}
