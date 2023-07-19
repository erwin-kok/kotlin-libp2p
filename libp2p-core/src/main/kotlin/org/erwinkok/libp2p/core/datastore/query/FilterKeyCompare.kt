// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore.query

import org.erwinkok.libp2p.core.datastore.Key

data class FilterKeyCompare(
    val op: Op,
    val key: Key,
) : Filter {
    override fun filter(entry: Entry): Boolean {
        return when (op) {
            Op.Equal -> entry.key == key
            Op.NotEqual -> entry.key != key
            Op.LessThan -> entry.key.toString() < key.toString()
            Op.LessThanOrEqual -> entry.key.toString() <= key.toString()
            Op.GreaterThan -> entry.key.toString() > key.toString()
            Op.GreaterThanOrEqual -> entry.key.toString() >= key.toString()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is FilterKeyCompare) {
            return super.equals(other)
        }
        if (op != other.op) {
            return false
        }
        if (key != other.key) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        return op.hashCode() xor key.hashCode()
    }

    override fun toString(): String {
        return "KEY ${op.v} \"$key\""
    }
}
