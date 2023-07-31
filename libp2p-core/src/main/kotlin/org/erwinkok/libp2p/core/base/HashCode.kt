// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.base

fun hashCodeOf(vararg values: Any?) =
    values.fold(0) { acc, value ->
        (acc * 31) + value.hashCode()
    }
