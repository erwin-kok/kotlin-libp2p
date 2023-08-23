// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.base

import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import java.io.Closeable

class ClosableLockedList<T : Closeable> : Closeable, Iterable<T> {
    val lock = ReentrantLock()
    val list = mutableListOf<T>()

    fun add(element: T) {
        lock.withLock {
            list.add(element)
        }
    }

    fun remove(element: T) {
        lock.withLock {
            list.remove(element)
        }
    }

    fun toList(): List<T> {
        return lock.withLock {
            list.toList()
        }
    }

    override fun iterator(): Iterator<T> {
        return list.iterator()
    }

    override fun close() {
        val toClose = mutableListOf<T>()
        lock.withLock {
            toClose.addAll(list)
            list.clear()
        }
        toClose.forEach { it.close() }
    }
}

fun <T : Closeable> closableLockedList(): ClosableLockedList<T> {
    return ClosableLockedList()
}

inline fun <R, T : Closeable> ClosableLockedList<T>.withLock(block: () -> R): R {
    lock.lock()
    try {
        return block()
    } finally {
        lock.unlock()
    }
}
