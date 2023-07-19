// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore.query

class OrderByFunction(val function: (Entry, Entry) -> Int) : Order {
    override fun compare(a: Entry, b: Entry): Int {
        return function(a, b)
    }

    override fun toString(): String {
        return "FN"
    }
}
