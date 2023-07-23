// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.peerstore.cache

class NoopCache<K, V> : Cache<K, V> {
    override fun get(key: K): V? = null
    override fun add(key: K, v: V) = Unit
    override fun remove(key: K) = Unit
    override fun contains(key: K) = false
    override fun peek(key: K): V? = null
    override fun keys(): List<K> = listOf()
}
