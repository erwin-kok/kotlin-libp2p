// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.secp256k1

import org.erwinkok.util.Hex
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

internal class Secp256k1PrivateKeyTest {
    @Test
    fun privateKeys() {
        val bytes = Hex.decodeOrThrow("eaf02ca348c524e6392655ba4d29603cd1a7347d9d65cfe93ce1ebffdca22694")
        val privKey = Secp256k1PrivateKey.privKeyFromBytes(bytes)
        val pubKey = privKey.secp256k1PublicKey
        Secp256k1PublicKey.parsePubKey(pubKey.serializeUncompressed())
        assertArrayEquals(bytes, privKey.serialize())
    }
}
