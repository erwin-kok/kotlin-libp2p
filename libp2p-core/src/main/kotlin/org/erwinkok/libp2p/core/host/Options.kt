// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.host

typealias Option<T> = (T) -> Unit

class Options<T> {
    val list = mutableListOf<Option<T>>()

    fun apply(o: T) {
        list.forEach { it(o) }
    }
}
