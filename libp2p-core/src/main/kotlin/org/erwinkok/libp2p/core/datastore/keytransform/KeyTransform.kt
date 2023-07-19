// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore.keytransform

import org.erwinkok.libp2p.core.datastore.Key

typealias KeyMapping = (Key) -> Key

interface KeyTransform {
    fun convertKey(key: Key): Key
    fun invertKey(key: Key): Key
}
