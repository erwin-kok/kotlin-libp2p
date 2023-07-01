// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto

import org.erwinkok.libp2p.crypto.pb.Crypto
import org.erwinkok.result.Result

abstract class Key protected constructor(val keyType: Crypto.KeyType) {
    abstract fun bytes(): Result<ByteArray>
    abstract fun hash(): Result<ByteArray>
    abstract fun raw(): Result<ByteArray>
}
