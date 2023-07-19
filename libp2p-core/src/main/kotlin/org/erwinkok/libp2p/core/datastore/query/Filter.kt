// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore.query

interface Filter {
    fun filter(entry: Entry): Boolean
}

enum class Op(val v: String) {
    Equal("=="),
    NotEqual("!="),
    GreaterThan(">"),
    GreaterThanOrEqual(">="),
    LessThan("<"),
    LessThanOrEqual("<="),
}
