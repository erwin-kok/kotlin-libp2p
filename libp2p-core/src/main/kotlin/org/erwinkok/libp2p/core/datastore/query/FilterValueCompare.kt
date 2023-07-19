// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore.query

import org.erwinkok.libp2p.core.util.Bytes

data class FilterValueCompare(
    val op: Op,
    val value: ByteArray,
) : Filter {
    override fun filter(entry: Entry): Boolean {
        val cmp = Bytes.compare(entry.value, value)
        return when (op) {
            Op.Equal -> cmp == 0
            Op.NotEqual -> cmp != 0
            Op.LessThan -> cmp < 0
            Op.LessThanOrEqual -> cmp <= 0
            Op.GreaterThan -> cmp > 0
            Op.GreaterThanOrEqual -> cmp >= 0
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is FilterValueCompare) {
            return super.equals(other)
        }
        if (op != other.op) {
            return false
        }
        if (!value.contentEquals(other.value)) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        return op.hashCode() xor value.contentHashCode()
    }

    override fun toString(): String {
        return "VALUE ${op.v} \"$value\""
    }
}
