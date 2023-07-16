// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.util

import io.ktor.utils.io.core.Closeable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.onFailure

inline fun <T : Closeable, R> T.closeOnError(block: (T) -> R): R {
    try {
        return block(this)
    } catch (e: Throwable) {
        close()
        throw e
    }
}

private val onUndeliveredCloseable: (Closeable) -> Unit = Closeable::close

@Suppress("FunctionName")
fun <E : Closeable> SafeChannel(capacity: Int): Channel<E> =
    Channel(capacity, onUndeliveredElement = onUndeliveredCloseable)

fun <E : Closeable> SendChannel<E>.safeTrySend(element: E) {
    trySend(element).onFailure { element.close() }
}

fun Channel<out Closeable>.fullClose(cause: Throwable?) {
    close(cause) // close channel to provide right cause
    cancel() // force call of onUndeliveredElement to release buffered elements
}
