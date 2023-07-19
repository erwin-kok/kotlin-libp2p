// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore.query

class OrderByKey : Order {
    override fun compare(a: Entry, b: Entry): Int {
        return a.key.toString().compareTo(b.key.toString())
    }

    override fun toString(): String {
        return "KEY"
    }
}
