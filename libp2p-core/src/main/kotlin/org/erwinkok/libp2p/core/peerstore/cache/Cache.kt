// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.peerstore.cache

interface Cache<K, V> {
    fun get(key: K): V?
    fun add(key: K, v: V)
    fun remove(key: K)
    fun contains(key: K): Boolean
    fun peek(key: K): V?
    fun keys(): List<K>
}
