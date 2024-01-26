// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.yamux

data class YamuxStreamId(val initiator: Boolean, val id: Int) {
    override fun toString(): String {
        return String.format("stream%08x/%s", id, if (initiator) "initiator" else "responder")
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is YamuxStreamId) {
            return super.equals(other)
        }
        return (id == other.id) and
            (initiator == other.initiator)
    }

    override fun hashCode(): Int {
        return id.hashCode() xor initiator.hashCode()
    }
}
