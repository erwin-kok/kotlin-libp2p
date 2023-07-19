// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore.query

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.erwinkok.libp2p.core.datastore.Key
import org.erwinkok.libp2p.core.datastore.Key.Companion.key
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class QueryTest {
    private var sampleKeys = listOf(
        key("/ab/c"),
        key("/ab/cd"),
        key("/ab/ef"),
        key("/ab/fg"),
        key("/a"),
        key("/abce"),
        key("/abcf"),
        key("/ab"),
    )

    @Test
    fun testApplyLimit() = runTest {
        checkResults(
            Query(limit = 2),
            sampleKeys,
            listOf(
                key("/ab/c"),
                key("/ab/cd"),
            ),
        )
    }

    @Test
    fun testApplyOffsetLimit() = runTest {
        checkResults(
            Query(offset = 3, limit = 2),
            sampleKeys,
            listOf(
                key("/ab/fg"),
                key("/a"),
            ),
        )
    }

    @Test
    fun testApplyFilterEqual() = runTest {
        val f = FilterKeyCompare(Op.Equal, key("/ab"))
        checkResults(
            Query(filters = listOf(f)),
            sampleKeys,
            listOf(
                key("/ab"),
            ),
        )
    }

    @Test
    fun testApplyFilterGreaterThan() = runTest {
        val f = FilterKeyCompare(Op.GreaterThan, key("/ab"))
        checkResults(
            Query(filters = listOf(f)),
            sampleKeys,
            listOf(
                key("/ab/c"),
                key("/ab/cd"),
                key("/ab/ef"),
                key("/ab/fg"),
                key("/abce"),
                key("/abcf"),
            ),
        )
    }

    @Test
    fun testApplyFilterLessThanOrEqual() = runTest {
        val f = FilterKeyCompare(Op.LessThanOrEqual, key("/ab"))
        checkResults(
            Query(filters = listOf(f)),
            sampleKeys,
            listOf(
                key("/a"),
                key("/ab"),
            ),
        )
    }

    @Test
    fun testApplyFilterPrefix1() = runTest {
        val f = FilterKeyPrefix("/a")
        checkResults(
            Query(filters = listOf(f)),
            sampleKeys,
            listOf(
                key("/ab/c"),
                key("/ab/cd"),
                key("/ab/ef"),
                key("/ab/fg"),
                key("/a"),
                key("/abce"),
                key("/abcf"),
                key("/ab"),
            ),
        )
    }

    @Test
    fun testApplyFilterPrefix2() = runTest {
        val f = FilterKeyPrefix("/ab/")
        checkResults(
            Query(filters = listOf(f)),
            sampleKeys,
            listOf(
                key("/ab/c"),
                key("/ab/cd"),
                key("/ab/ef"),
                key("/ab/fg"),
            ),
        )
    }

    @Test
    fun testApplyPrefix() = runTest {
        checkResults(
            Query(prefix = "/ab"),
            sampleKeys,
            listOf(
                key("/ab/c"),
                key("/ab/cd"),
                key("/ab/ef"),
                key("/ab/fg"),
            ),
        )
    }

    @Test
    fun testApplyOrder() = runTest {
        checkResults(
            Query(orders = listOf(OrderByKey())),
            sampleKeys,
            listOf(
                key("/a"),
                key("/ab"),
                key("/ab/c"),
                key("/ab/cd"),
                key("/ab/ef"),
                key("/ab/fg"),
                key("/abce"),
                key("/abcf"),
            ),
        )
    }

    @Test
    fun testApplyOrderDescending() = runTest {
        checkResults(
            Query(orders = listOf(OrderByKeyDescending())),
            sampleKeys,
            listOf(
                key("/abcf"),
                key("/abce"),
                key("/ab/fg"),
                key("/ab/ef"),
                key("/ab/cd"),
                key("/ab/c"),
                key("/ab"),
                key("/a"),
            ),
        )
    }

    @Test
    fun testApplyCombined() = runTest {
        checkResults(
            Query(limit = 2, offset = 1, prefix = "/ab", orders = listOf(OrderByKey())),
            sampleKeys,
            listOf(
                key("/ab/cd"),
                key("/ab/ef"),
            ),
        )
    }

    @Test
    fun testLimit0() = runTest {
        checkResults(
            Query(limit = 0),
            sampleKeys,
            listOf(
                key("/ab/c"),
                key("/ab/cd"),
                key("/ab/ef"),
                key("/ab/fg"),
                key("/a"),
                key("/abce"),
                key("/abcf"),
                key("/ab"),
            ),
        )
    }

    @Test
    fun testLimit10() = runTest {
        checkResults(
            Query(limit = 10),
            sampleKeys,
            listOf(
                key("/ab/c"),
                key("/ab/cd"),
                key("/ab/ef"),
                key("/ab/fg"),
                key("/a"),
                key("/abce"),
                key("/abcf"),
                key("/ab"),
            ),
        )
    }

    @Test
    fun testLimit2() = runTest {
        checkResults(
            Query(limit = 2),
            sampleKeys,
            listOf(
                key("/ab/c"),
                key("/ab/cd"),
            ),
        )
    }

    @Test
    fun testOffset0() = runTest {
        checkResults(
            Query(offset = 0),
            sampleKeys,
            listOf(
                key("/ab/c"),
                key("/ab/cd"),
                key("/ab/ef"),
                key("/ab/fg"),
                key("/a"),
                key("/abce"),
                key("/abcf"),
                key("/ab"),
            ),
        )
    }

    @Test
    fun testOffset10() = runTest {
        checkResults(
            Query(offset = 10),
            sampleKeys,
            listOf(),
        )
    }

    @Test
    fun testOffset2() = runTest {
        checkResults(
            Query(offset = 2),
            sampleKeys,
            listOf(
                key("/ab/ef"),
                key("/ab/fg"),
                key("/a"),
                key("/abce"),
                key("/abcf"),
                key("/ab"),
            ),
        )
    }

    @Test
    fun testString() {
        assertEquals("SELECT keys,vals", Query().toString())
    }

    @Test
    fun testStringOffsetLimit() {
        assertEquals("SELECT keys,vals OFFSET 10 LIMIT 10", Query(offset = 10, limit = 10).toString())
    }

    @Test
    fun testStringOrder() {
        assertEquals("SELECT keys,vals ORDER [VALUE, KEY] OFFSET 10 LIMIT 10", Query(offset = 10, limit = 10, orders = listOf(OrderByValue(), OrderByKey())).toString())
    }

    @Test
    fun testStringFilter() {
        assertEquals(
            "SELECT keys,vals FILTER [KEY > \"/foo/bar\", KEY < \"/foo/bar\"] ORDER [VALUE, KEY] OFFSET 10 LIMIT 10",
            Query(
                offset = 10,
                limit = 10,
                orders = listOf(OrderByValue(), OrderByKey()),
                filters = listOf(FilterKeyCompare(Op.GreaterThan, key("/foo/bar")), FilterKeyCompare(Op.LessThan, key("/foo/bar"))),
            ).toString(),
        )
    }

    @Test
    fun testStringPrefix() {
        assertEquals(
            "SELECT keys,vals FROM \"/foo\" FILTER [KEY > \"/foo/bar\", KEY < \"/foo/bar\"] ORDER [VALUE, KEY] OFFSET 10 LIMIT 10",
            Query(
                prefix = "/foo",
                offset = 10,
                limit = 10,
                orders = listOf(OrderByValue(), OrderByKey()),
                filters = listOf(FilterKeyCompare(Op.GreaterThan, key("/foo/bar")), FilterKeyCompare(Op.LessThan, key("/foo/bar"))),
            ).toString(),
        )
    }

    @Test
    fun testStringExpirationsTrue() {
        assertEquals(
            "SELECT keys,vals,exps FROM \"/foo\" FILTER [KEY > \"/foo/bar\", KEY < \"/foo/bar\"] ORDER [VALUE, KEY] OFFSET 10 LIMIT 10",
            Query(
                prefix = "/foo",
                offset = 10,
                returnExpirations = true,
                limit = 10,
                orders = listOf(OrderByValue(), OrderByKey()),
                filters = listOf(FilterKeyCompare(Op.GreaterThan, key("/foo/bar")), FilterKeyCompare(Op.LessThan, key("/foo/bar"))),
            ).toString(),
        )
    }

    @Test
    fun testStringKeysOnly() {
        assertEquals(
            "SELECT keys,exps FROM \"/foo\" FILTER [KEY > \"/foo/bar\", KEY < \"/foo/bar\"] ORDER [VALUE, KEY] OFFSET 10 LIMIT 10",
            Query(
                prefix = "/foo",
                offset = 10,
                returnExpirations = true,
                limit = 10,
                keysOnly = true,
                orders = listOf(OrderByValue(), OrderByKey()),
                filters = listOf(FilterKeyCompare(Op.GreaterThan, key("/foo/bar")), FilterKeyCompare(Op.LessThan, key("/foo/bar"))),
            ).toString(),
        )
    }

    @Test
    fun testStringExpirationsFalse() {
        assertEquals(
            "SELECT keys FROM \"/foo\" FILTER [KEY > \"/foo/bar\", KEY < \"/foo/bar\"] ORDER [VALUE, KEY] OFFSET 10 LIMIT 10",
            Query(
                prefix = "/foo",
                offset = 10,
                returnExpirations = false,
                limit = 10,
                keysOnly = true,
                orders = listOf(OrderByValue(), OrderByKey()),
                filters = listOf(FilterKeyCompare(Op.GreaterThan, key("/foo/bar")), FilterKeyCompare(Op.LessThan, key("/foo/bar"))),
            ).toString(),
        )
    }

    private suspend fun checkResults(query: Query, input: List<Key>, expected: List<Key>) {
        val result = query.applyQueryResult(input.map { QueryResult(Entry(key = it)) }.asFlow()).map {
            val entry = it.entry
            require(entry != null && it.error == null)
            entry.key
        }
        assertEquals(expected, result.toList())
    }
}
