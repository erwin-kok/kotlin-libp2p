// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.peerstore.cache

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Cache as CaffeineCache

class CaffeineCache<K, V>(cacheSize: Long) : Cache<K, V> {
    private val cache: CaffeineCache<K, V>

    init {
        cache = Caffeine.newBuilder()
            .maximumSize(cacheSize)
            .build()
    }

    override fun get(key: K): V? {
        return cache.getIfPresent(key)
    }

    override fun add(key: K, v: V) {
        cache.put(key, v)
    }

    override fun remove(key: K) {
        cache.invalidate(key)
    }

    override fun contains(key: K): Boolean {
        return cache.getIfPresent(key) != null
    }

    override fun peek(key: K): V? {
        return cache.getIfPresent(key)
    }

    override fun keys(): List<K> {
        return cache.asMap().keys.toList()
    }
}
