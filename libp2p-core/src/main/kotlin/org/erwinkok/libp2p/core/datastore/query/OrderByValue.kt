// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore.query

import org.erwinkok.libp2p.core.util.Bytes

class OrderByValue : Order {
    override fun compare(a: Entry, b: Entry): Int {
        return Bytes.compare(a.value, b.value)
    }

    override fun toString(): String {
        return "VALUE"
    }
}
