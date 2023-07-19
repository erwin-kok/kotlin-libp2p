// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore.namespace

import kotlinx.coroutines.CoroutineScope
import org.erwinkok.libp2p.core.datastore.Datastore
import org.erwinkok.libp2p.core.datastore.Key
import org.erwinkok.libp2p.core.datastore.keytransform.KeyTransformDatastore
import org.erwinkok.libp2p.core.datastore.keytransform.PrefixTransform

object Namespace {
    fun prefixTransform(prefix: Key): PrefixTransform {
        return PrefixTransform(prefix)
    }

    fun wrap(scope: CoroutineScope, child: Datastore, prefix: Key): KeyTransformDatastore {
        return KeyTransformDatastore.wrap(scope, child, prefixTransform(prefix))
    }
}
