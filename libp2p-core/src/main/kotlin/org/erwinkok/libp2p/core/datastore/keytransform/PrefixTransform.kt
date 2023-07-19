// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore.keytransform

import org.erwinkok.libp2p.core.datastore.Key

class PrefixTransform(val prefix: Key) : KeyTransform {
    override fun convertKey(key: Key): Key {
        return prefix.child(key)
    }

    override fun invertKey(key: Key): Key {
        if (prefix.toString() == "/") {
            return key
        }
        require(prefix.isAncestorOf(key))
        return Key.rawKey(key.toString().removePrefix(prefix.toString()))
    }
}
