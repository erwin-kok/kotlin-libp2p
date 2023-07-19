// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore.query

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transformWhile
import org.erwinkok.libp2p.core.util.Path

data class Query(
    val prefix: String? = null,
    val filters: List<Filter>? = null,
    val orders: List<Order>? = null,
    val limit: Int? = null,
    val offset: Int? = null,
    val keysOnly: Boolean? = null,
    val returnExpirations: Boolean? = null,
    val returnsSizes: Boolean? = null,
) {
    fun apply(input: Flow<Entry>): Flow<Entry> {
        return flow {
            var output = input
            if (prefix != null && prefix != "") {
                // Clean the prefix as a key and append / so a prefix of /bar only finds /bar/baz, not /barbaz.
                val p = if (prefix.isEmpty()) {
                    "/"
                } else {
                    if (prefix[0] != '/') {
                        Path.clean("/$prefix")
                    } else {
                        Path.clean(prefix)
                    }
                }
                // If the prefix is empty, ignore it.
                if (p != "/") {
                    output = queryFilter(output, FilterKeyPrefix("$p/"))
                }
            }
            if (filters != null) {
                for (f in filters) {
                    output = queryFilter(output, f)
                }
            }
            if (!orders.isNullOrEmpty()) {
                output = queryOrder(output, orders)
            }
            if (offset != null && offset > 0) {
                output = queryOffset(output, offset)
            }
            if (limit != null && limit > 0) {
                output = queryLimit(output, limit)
                // output = output.take(limit)
            }
            emitAll(output)
        }
    }

    fun applyQueryResult(input: Flow<QueryResult>): Flow<QueryResult> {
        return flow {
            var output = input
            if (prefix != null && prefix != "") {
                // Clean the prefix as a key and append / so a prefix of /bar only finds /bar/baz, not /barbaz.
                val p = if (prefix.isEmpty()) {
                    "/"
                } else {
                    if (prefix[0] != '/') {
                        Path.clean("/$prefix")
                    } else {
                        Path.clean(prefix)
                    }
                }
                // If the prefix is empty, ignore it.
                if (p != "/") {
                    output = queryFilterQueryResult(output, FilterKeyPrefix("$p/"))
                }
            }
            if (filters != null) {
                for (f in filters) {
                    output = queryFilterQueryResult(output, f)
                }
            }
            if (!orders.isNullOrEmpty()) {
                output = queryOrderQueryResult(output, orders)
            }
            if (offset != null && offset > 0) {
                output = queryOffsetQueryResult(output, offset)
            }
            if (limit != null && limit > 0) {
                output = queryLimitQueryResult(output, limit)
                // output = output.take(limit)
            }
            emitAll(output)
        }
    }

    private fun queryFilter(input: Flow<Entry>, filter: Filter): Flow<Entry> {
        return flow {
            input.collect {
                if (filter.filter(it)) {
                    emit(it)
                }
            }
        }
    }

    private fun queryFilterQueryResult(input: Flow<QueryResult>, filter: Filter): Flow<QueryResult> {
        return flow {
            input.collect {
                if (it.error != null || it.entry == null) {
                    emit(it)
                } else {
                    if (filter.filter(it.entry)) {
                        emit(it)
                    }
                }
            }
        }
    }

    private suspend fun queryOrder(input: Flow<Entry>, orders: List<Order>): Flow<Entry> {
        val items = Order.sort(orders, input.toList())
        return flow {
            items.forEach {
                emit(it)
            }
        }
    }

    private suspend fun queryOrderQueryResult(input: Flow<QueryResult>, orders: List<Order>): Flow<QueryResult> {
        val items = Order.sortQueryResult(orders, input.toList())
        return flow {
            items.forEach {
                emit(it)
            }
        }
    }

    private fun queryOffset(input: Flow<Entry>, offset: Int): Flow<Entry> {
        return flow {
            var skipped = 0
            input.collect { value ->
                if (skipped >= offset) emit(value) else ++skipped
            }
        }
    }

    private fun queryOffsetQueryResult(input: Flow<QueryResult>, offset: Int): Flow<QueryResult> {
        return flow {
            var skipped = 0
            input.collect {
                if (it.error != null || it.entry == null) {
                    emit(it)
                } else {
                    if (skipped >= offset) emit(it) else ++skipped
                }
            }
        }
    }

    private fun queryLimit(input: Flow<Entry>, limit: Int): Flow<Entry> {
        var taken = limit - 1
        return input.transformWhile {
            emit(it)
            taken-- > 0
        }
    }

    private fun queryLimitQueryResult(input: Flow<QueryResult>, limit: Int): Flow<QueryResult> {
        var taken = limit - 1
        return input.transformWhile {
            emit(it)
            if (it.error != null || it.entry == null) {
                true
            } else {
                taken-- > 0
            }
        }
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("SELECT keys")
        if (keysOnly == null || !keysOnly) {
            sb.append(",vals")
        }
        if (returnExpirations != null && returnExpirations) {
            sb.append(",exps")
        }
        if (prefix != null && prefix != "") {
            sb.append(" FROM \"$prefix\"")
        }
        if (!filters.isNullOrEmpty()) {
            sb.append(" FILTER [")
            sb.append(filters.joinToString(", "))
            sb.append("]")
        }
        if (!orders.isNullOrEmpty()) {
            sb.append(" ORDER [")
            sb.append(orders.joinToString(", "))
            sb.append("]")
        }
        if (offset != null && offset > 0) {
            sb.append(" OFFSET $offset")
        }
        if (limit != null && limit > 0) {
            sb.append(" LIMIT $limit")
        }
        return sb.toString()
    }
}
