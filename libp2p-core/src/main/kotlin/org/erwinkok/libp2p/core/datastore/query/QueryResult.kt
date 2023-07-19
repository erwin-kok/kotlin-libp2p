// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore.query

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import org.erwinkok.result.Error

class QueryResult(val entry: Entry? = null, val error: Error? = null)

inline fun Flow<QueryResult>.queryFilterError(crossinline action: suspend (value: Error) -> Unit): Flow<Entry> = transform { value ->
    if (value.error != null) {
        action(value.error)
    }
    val entry = value.entry ?: return@transform
    return@transform emit(entry)
}
