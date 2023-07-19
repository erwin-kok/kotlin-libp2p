// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore.query

import org.erwinkok.libp2p.core.datastore.Key
import java.time.Instant

data class Entry(
    val key: Key,
    var value: ByteArray? = null,
    var expiration: Instant? = null,
    var size: Int? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is Entry) {
            return super.equals(other)
        }
        if (key != other.key) {
            return false
        }
        if (!value.contentEquals(other.value)) {
            return false
        }
        if (expiration != other.expiration) {
            return false
        }
        if (size != other.size) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        return key.hashCode() xor
            value.contentHashCode() xor
            expiration.hashCode() xor
            size.hashCode()
    }
}
