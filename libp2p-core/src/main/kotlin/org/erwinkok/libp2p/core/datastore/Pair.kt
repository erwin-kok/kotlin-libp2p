// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore

data class Pair(
    val key: Key,
    val value: ByteArray,
) {
    override fun hashCode(): Int {
        val result = key.hashCode()
        return 31 * result + value.contentHashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is Pair) {
            return super.equals(other)
        }
        if (key != other.key) {
            return false
        }
        if (!value.contentEquals(other.value)) {
            return false
        }
        return true
    }
}
