// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore.query

data class FilterKeyPrefix(val prefix: String) : Filter {
    override fun filter(entry: Entry): Boolean {
        return entry.key.toString().startsWith(prefix)
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is FilterKeyPrefix) {
            return super.equals(other)
        }
        if (prefix != other.prefix) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        return prefix.hashCode()
    }

    override fun toString(): String {
        return "PREFIX(\"$prefix\")"
    }
}
