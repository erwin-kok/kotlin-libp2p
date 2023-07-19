// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore

import org.erwinkok.libp2p.core.util.Path
import org.erwinkok.libp2p.core.util.StringUtils

class Key private constructor(private var value: String, clean: Boolean = true) {
    init {
        if (clean) {
            clean()
        }
        if (value.isEmpty() || !value.startsWith('/')) {
            throw Exception("Invalid Key")
        }
    }

    val type: String
        get() = namespaceType(baseNamespace())

    val name: String
        get() = namespaceValue(this.baseNamespace())

    val bytes get() = value.toByteArray(Charsets.UTF_8)

    fun clean() {
        value = if (value.isBlank()) {
            "/"
        } else if (value.startsWith('/')) {
            Path.clean(value)
        } else {
            Path.clean("/$value")
        }
    }

    // Check if the given key is sorted lower than ourself.
    fun less(key: Key): Boolean {
        val list1 = list()
        val list2 = key.list()

        for (i in list1.indices) {
            if (list2.size < i + 1) {
                return false
            }

            val c1 = list1[i]
            val c2 = list2[i]

            if (c1 < c2) {
                return true
            } else if (c1 > c2) {
                return false
            }
        }

        return list1.size < list2.size
    }

    fun reverse(): Key {
        return withNamespaces(list().reversed())
    }

    fun namespaces(): List<String> {
        return list()
    }

    fun baseNamespace(): String {
        val ns = namespaces()
        return ns[ns.size - 1]
    }

    fun list(): List<String> {
        val split = value.split("/")
        return split.subList(1, split.size)
    }

    fun instance(s: String): Key {
        return key("$this:$s")
    }

    fun path(): Key {
        return key(parent().value + "/" + namespaceType(baseNamespace()))
    }

    fun parent(): Key {
        val list = list()
        if (list.size == 1) {
            return rawKey("/")
        }
        return key(list.subList(0, list.size - 1).joinToString("/"))
    }

    fun child(key: Key): Key {
        if (this.value == "/") {
            return key
        } else if (key.value == "/") {
            return this
        }
        return rawKey(this.value + key.value)
    }

    fun childString(s: String): Key {
        return key("$value/$s")
    }

    fun isAncestorOf(other: Key): Boolean {
        if (other.value.length <= value.length) {
            // We're not long enough to be a child.
            return false
        }
        if (value == "/") {
            // We're the root and the other key is longer.
            return true
        }
        if (other.value == this.value) {
            return false
        }
        return other.value.startsWith(this.value)
    }

    fun isDecendantOf(other: Key): Boolean {
        return other.isAncestorOf(this)
    }

    fun isTopLevel(): Boolean {
        return this.list().size == 1
    }

    fun concat(vararg keys: Key): Key {
        return withNamespaces(namespaces() + keys.flatMap { it.namespaces() })
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is Key) {
            return super.equals(other)
        }
        if (value != other.value) {
            return false
        }
        return true
    }

    override fun toString(): String {
        return value
    }

    private fun namespaceType(ns: String): String {
        val parts = ns.split(':')
        if (parts.size < 2) {
            return ""
        }
        return parts.subList(0, parts.size - 1).joinToString(":")
    }

    private fun namespaceValue(ns: String): String {
        val parts = ns.split(':')
        return parts[parts.size - 1]
    }

    companion object {
        fun key(s: String): Key {
            val k = Key(s)
            k.clean()
            return k
        }

        fun rawKey(s: String): Key {
            if (s.isBlank()) {
                return Key("/")
            }
            require(s[0] == '/' && (s.length <= 1 || s[s.length - 1] != '/'))
            return Key(s)
        }

        fun withNamespaces(vararg list: String): Key {
            return withNamespaces(list.toList())
        }

        fun withNamespaces(list: List<String>): Key {
            return key(list.joinToString("/"))
        }

        fun random(): Key {
            return key("/" + StringUtils.randomString(16))
        }
    }
}
