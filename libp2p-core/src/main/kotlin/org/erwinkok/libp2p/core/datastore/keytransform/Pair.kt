// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore.keytransform

import org.erwinkok.libp2p.core.datastore.Key

class Pair(private val convert: KeyMapping, private val invert: KeyMapping) : KeyTransform {
    override fun convertKey(key: Key): Key {
        return convert(key)
    }

    override fun invertKey(key: Key): Key {
        return invert(key)
    }
}
