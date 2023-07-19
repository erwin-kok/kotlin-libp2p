// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
@file:Suppress("UNUSED_PARAMETER")

package org.erwinkok.libp2p.testing.testsuites.datastore

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.erwinkok.libp2p.core.datastore.BatchingDatastore
import org.erwinkok.libp2p.core.datastore.Datastore
import org.erwinkok.libp2p.core.datastore.Key
import org.erwinkok.libp2p.core.datastore.Key.Companion.key
import org.erwinkok.libp2p.core.datastore.query.Entry
import org.erwinkok.libp2p.core.datastore.query.Filter
import org.erwinkok.libp2p.core.datastore.query.FilterKeyCompare
import org.erwinkok.libp2p.core.datastore.query.FilterKeyPrefix
import org.erwinkok.libp2p.core.datastore.query.FilterValueCompare
import org.erwinkok.libp2p.core.datastore.query.Op
import org.erwinkok.libp2p.core.datastore.query.Order
import org.erwinkok.libp2p.core.datastore.query.OrderByFunction
import org.erwinkok.libp2p.core.datastore.query.OrderByKey
import org.erwinkok.libp2p.core.datastore.query.OrderByKeyDescending
import org.erwinkok.libp2p.core.datastore.query.OrderByValue
import org.erwinkok.libp2p.core.datastore.query.Query
import org.erwinkok.libp2p.core.datastore.query.QueryResult
import org.erwinkok.libp2p.core.datastore.query.queryFilterError
import org.erwinkok.libp2p.core.util.Bytes
import org.erwinkok.multiformat.multibase.bases.Base32
import org.erwinkok.result.coAssertErrorResult
import org.erwinkok.result.errorMessage
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.function.Executable
import java.util.stream.Stream
import kotlin.random.Random

class DatastoreTestSuite(private val dsName: String) {
    private class SubTestExecutable<T : Datastore>(
        private val create: (CoroutineScope) -> T,
        private val exec: suspend (CoroutineScope, T) -> Unit,
        private val cleanup: suspend (CoroutineScope, T) -> Unit,
    ) : Executable {
        override fun execute() = runTest {
            val element = create(this)
            element.use {
                exec(this, element)
                cleanup(this, element)
            }
        }
    }

    private val ElemCount = 20

    private val basicSubtests = listOf(
        ::subtestBasicPutGet,
        ::subtestNotFounds,
        ::subtestPrefix,
        ::subtestOrder,
        ::subtestLimit,
        ::subtestFilter,
        ::subtestManyKeysAndQuery,
        ::subtestReturnSizes,
        ::subtestBasicSync,
        ::subtestCombinations,
    )

    private val batchSubtests = listOf(
        ::runBatchTest,
        ::runBatchDeleteTest,
        ::runBatchPutAndDeleteTest,
    )

    fun testBasic(create: (CoroutineScope) -> Datastore): Stream<DynamicTest> {
        val tests = mutableListOf<DynamicTest>()
        for (test in basicSubtests) {
            tests.add(
                DynamicTest.dynamicTest(
                    "Test: ${test.name} ($dsName)",
                    SubTestExecutable(create, test, ::cleanDs),
                ),
            )
        }
        return tests.stream()
    }

    fun testBatch(create: (CoroutineScope) -> BatchingDatastore): Stream<DynamicTest> {
        val tests = mutableListOf<DynamicTest>()
        for (test in batchSubtests) {
            tests.add(
                DynamicTest.dynamicTest(
                    "Test: ${test.name} ($dsName)",
                    SubTestExecutable(create, test, ::cleanDs),
                ),
            )
        }
        return tests.stream()
    }

    private suspend fun subtestBasicPutGet(scope: CoroutineScope, ds: Datastore) {
        val key = key("foo")
        val value = "Hello Datastore!".toByteArray()

        ds.put(key, value).expectNoErrors()

        val has = ds.has(key).expectNoErrors()
        assertTrue(has)

        val size = ds.getSize(key).expectNoErrors()
        assertEquals(value.size, size)

        val out = ds.get(key).expectNoErrors()
        assertArrayEquals(value, out)

        val has2 = ds.has(key).expectNoErrors()
        assertTrue(has2)

        val size2 = ds.getSize(key).expectNoErrors()
        assertEquals(value.size, size2)

        ds.delete(key).expectNoErrors()

        val has3 = ds.has(key).expectNoErrors()
        assertFalse(has3)

        coAssertErrorResult("datastore: key not found") { ds.getSize(key) }
    }

    private suspend fun subtestNotFounds(scope: CoroutineScope, ds: Datastore) {
        val key = key("nortreal")

        coAssertErrorResult("datastore: key not found") { ds.get(key) }

        val has = ds.has(key).expectNoErrors()
        assertFalse(has)

        coAssertErrorResult("datastore: key not found") { ds.getSize(key) }

        ds.delete(key).expectNoErrors()
    }

    private suspend fun subtestPrefix(scope: CoroutineScope, ds: Datastore) {
        val test: suspend (String) -> Unit = {
            subtestQuery(ds, Query(prefix = it), ElemCount)
        }

        test("")
        test("/")
        test("/./")
        test("/.././/")
        test("/prefix/../")

        test("/prefix")
        test("/prefix/")
        test("/prefix/sub/")

        test("/0/")
        test("/bad/")
    }

    private suspend fun subtestReturnSizes(scope: CoroutineScope, ds: Datastore) {
        subtestQuery(ds, Query(returnsSizes = true), 100)
    }

    private suspend fun subtestOrder(scope: CoroutineScope, ds: Datastore) {
        val test: suspend (List<Order>) -> Unit = {
            subtestQuery(ds, Query(orders = it), ElemCount)
        }
        test(listOf(OrderByKey()))
        test(listOf(OrderByKeyDescending()))
        test(listOf(OrderByValue()))
        test(listOf(OrderByValue(), OrderByKey()))
        test(
            listOf(
                object : Order {
                    override fun compare(a: Entry, b: Entry): Int {
                        return Bytes.compare(a.value, b.value)
                    }
                },
            ),
        )
    }

    private suspend fun subtestLimit(scope: CoroutineScope, ds: Datastore) {
        val test: suspend (Int, Int) -> Unit = { offset, limit ->
            subtestQuery(ds, Query(orders = listOf(OrderByKey()), offset = offset, limit = limit, keysOnly = true), ElemCount)
        }
        test(0, ElemCount / 10)
        test(0, 0)
        test(ElemCount / 10, 0)
        test(ElemCount / 10, ElemCount / 10)
        test(ElemCount / 10, ElemCount / 5)
        test(ElemCount / 2, ElemCount / 5)
        test(ElemCount - 1, ElemCount / 5)
        test(ElemCount * 2, ElemCount / 5)
        test(ElemCount * 2, 0)
        test(ElemCount - 1, 0)
        test(ElemCount - 5, 0)
    }

    private suspend fun subtestFilter(scope: CoroutineScope, ds: Datastore) {
        val test: suspend (List<Filter>) -> Unit = {
            subtestQuery(ds, Query(filters = it), ElemCount)
        }
        test(listOf(FilterKeyCompare(Op.Equal, key("/0key0"))))
        test(listOf(FilterKeyCompare(Op.LessThan, key("/2"))))
        test(listOf(FilterKeyCompare(Op.Equal, key("/0key0"))))
        test(listOf(FilterKeyPrefix("/0key0")))
        test(listOf(FilterValueCompare(Op.LessThan, Random.nextBytes(64))))
        test(
            listOf(object : Filter {
                override fun filter(entry: Entry): Boolean {
                    return (entry.key.toString().length % 2) == 0
                }
            }),
        )
    }

    private suspend fun subtestManyKeysAndQuery(scope: CoroutineScope, ds: Datastore) {
        subtestQuery(ds, Query(keysOnly = true), ElemCount)
    }

    private suspend fun subtestBasicSync(scope: CoroutineScope, ds: Datastore) {
        ds.sync(key("prefix")).expectNoErrors()
        ds.put(key("/prefix"), "foo".toByteArray())
        ds.sync(key("/prefix")).expectNoErrors()
        ds.put(key("/prefix/sub"), "bar".toByteArray())
        ds.sync(key("/prefix")).expectNoErrors()
        ds.sync(key("/prefix/sub")).expectNoErrors()
        ds.sync(key("")).expectNoErrors()
    }

    private suspend fun subtestCombinations(scope: CoroutineScope, ds: Datastore) {
        val offsets = listOf(
            0,
            ElemCount / 10,
            ElemCount - 5,
            ElemCount,
        )
        val limits = listOf(
            0,
            1,
            ElemCount / 10,
            ElemCount,
        )
        val filters = listOf(
            listOf(FilterKeyCompare(Op.Equal, key("/0key0"))),
            listOf(FilterKeyCompare(Op.LessThan, key("/2"))),
        )
        val prefixes = listOf(
            "",
            "/prefix",
            "/0", // keys exist under this prefix but they shouldn't match.
        )
        val orders = listOf(
            listOf(OrderByKey()),
            listOf(OrderByKeyDescending()),
            listOf(OrderByValue(), OrderByKey()),
            listOf(
                OrderByFunction { a, b ->
                    Bytes.compare(a.value, b.value)
                },
            ),
        )
        val lengths = listOf(
            0,
            1,
            ElemCount,
        )
        for (offset in offsets) {
            for (limit in limits) {
                for (filter in filters) {
                    for (prefix in prefixes) {
                        for (order in orders) {
                            for (length in lengths) {
                                val query = Query(
                                    offset = offset,
                                    limit = limit,
                                    filters = filter,
                                    orders = order,
                                    prefix = prefix,
                                )
                                subtestQuery(ds, query, length)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun runBatchTest(scope: CoroutineScope, ds: BatchingDatastore) {
        val batch = ds.batch().expectNoErrors()

        val blocks = mutableListOf<ByteArray>()
        val keys = mutableListOf<Key>()
        repeat(20) {
            val block = Random.nextBytes(256 * 1024)
            blocks.add(block)
            val key = key(Base32.encodeStdLowerNoPad(block.copyOfRange(0, 8)))
            keys.add(key)
            batch.put(key, block).expectNoErrors()
        }
        for (k in keys) {
            val has = ds.has(k).expectNoErrors()
            assertFalse(has)
            coAssertErrorResult("datastore: key not found") { ds.get(k) }
        }
        batch.commit().expectNoErrors()
        for (i in keys.indices) {
            val out = ds.get(keys[i]).expectNoErrors()
            assertArrayEquals(blocks[i], out)
        }
    }

    private suspend fun runBatchDeleteTest(scope: CoroutineScope, ds: BatchingDatastore) {
        val blocks = mutableListOf<ByteArray>()
        val keys = mutableListOf<Key>()
        repeat(20) {
            val block = Random.nextBytes(256 * 1024)
            blocks.add(block)
            val key = key(Base32.encodeStdLowerNoPad(block.copyOfRange(0, 8)))
            keys.add(key)
            ds.put(key, block).expectNoErrors()
        }

        val batch = ds.batch().expectNoErrors()

        for (k in keys) {
            batch.delete(k)
        }

        for (i in keys.indices) {
            val has = ds.has(keys[i]).expectNoErrors()
            assertTrue(has)
            val out = ds.get(keys[i]).expectNoErrors()
            assertArrayEquals(blocks[i], out)
        }

        batch.commit()

        for (k in keys) {
            val has = ds.has(k).expectNoErrors()
            assertFalse(has)
            coAssertErrorResult("datastore: key not found") { ds.get(k) }
        }
    }

    private suspend fun runBatchPutAndDeleteTest(scope: CoroutineScope, ds: BatchingDatastore) {
        val batch = ds.batch().expectNoErrors()
        val ka = key("/a")
        val kb = key("/b")
        batch.put(ka, byteArrayOf(1)).expectNoErrors()
        batch.put(kb, byteArrayOf(2)).expectNoErrors()
        batch.delete(ka).expectNoErrors()
        batch.delete(kb).expectNoErrors()
        batch.put(kb, byteArrayOf(3)).expectNoErrors()

        batch.commit().expectNoErrors()

        coAssertErrorResult("datastore: key not found") { ds.get(ka) }

        val out = ds.get(kb).expectNoErrors()
        assertArrayEquals(byteArrayOf(3), out)
    }

    private suspend fun subtestQuery(ds: Datastore, query: Query, count: Int) {
        val input = mutableListOf<Entry>()
        for (i in 0 until count) {
            val key = key("${i}key$i")
            val value = Random.nextBytes(64)
            input.add(Entry(key = key, value = value, size = value.size))
        }
        for (i in 0 until count) {
            val key = key("/prefix/${i}key$i")
            val value = Random.nextBytes(64)
            input.add(Entry(key = key, value = value, size = value.size))
        }
        for (i in 0 until count) {
            val key = key("/prefix/sub/${i}key$i")
            val value = Random.nextBytes(64)
            input.add(Entry(key = key, value = value, size = value.size))
        }
        for (i in 0 until count) {
            val key = key("/capital/${i}KEY$i")
            val value = Random.nextBytes(64)
            input.add(Entry(key = key, value = value, size = value.size))
        }
        for (e in input) {
            ds.put(e.key, e.value!!).expectNoErrors()
        }
        for (e in input) {
            val out = ds.get(e.key).expectNoErrors()
            assertArrayEquals(e.value!!, out)
        }

        var actual = ds.query(query).expectNoErrors().map { it.entry!! }.toList()
        var expected = query.applyQueryResult(input.map { QueryResult(it) }.asFlow()).toList().mapNotNull { it.entry }
        assertEquals(expected.size, actual.size)

        if (query.orders == null) {
            actual = Order.sort(listOf(OrderByKey()), actual)
            expected = Order.sort(listOf(OrderByKey()), expected)
        }
        for (i in actual.indices) {
            assertEquals(expected[i].key, actual[i].key)
            val keysOnly = query.keysOnly ?: false
            if (!keysOnly) {
                assertArrayEquals(expected[i].value, actual[i].value)
            }
            val returnsSizes = query.returnsSizes ?: false
            if (returnsSizes) {
                assertTrue(actual[i].size!! > 0)
            }
        }
        input.forEach { ds.delete(it.key) }
    }

    private suspend fun cleanDs(scope: CoroutineScope, ds: Datastore) {
        val results = ds.query(Query(keysOnly = true)).expectNoErrors()
        results
            .queryFilterError { error(errorMessage(it)) }
            .collect {
                ds.delete(it.key)
            }
    }
}
