// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.mplex

data class MplexStreamId(val initiator: Boolean, val id: Long) {
    override fun toString(): String {
        return String.format("stream%08x/%s", id, if (initiator) "initiator" else "responder")
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is MplexStreamId) {
            return super.equals(other)
        }
        return (id == other.id) and
            (initiator == other.initiator)
    }

    override fun hashCode(): Int {
        return id.hashCode() xor initiator.hashCode()
    }
}
