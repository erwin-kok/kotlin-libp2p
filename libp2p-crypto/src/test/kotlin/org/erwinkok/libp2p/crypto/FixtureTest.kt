// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto

import org.erwinkok.libp2p.crypto.pb.Crypto
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class FixtureTest {
    @Test
    fun testRSAFixtures() {
        testFixture(Crypto.KeyType.RSA)
    }

    @Test
    fun testSecp256k1Fixtures() {
        testFixture(Crypto.KeyType.Secp256k1)
    }

    @Test
    fun testEcdsaFixtures() {
        testFixture(Crypto.KeyType.ECDSA)
    }

    private fun testFixture(nr: Crypto.KeyType) {
        val base = "crypto/" + nr.number
        val classLoader = this.javaClass.classLoader
        val pubBytes = classLoader.getResourceAsStream("$base.pub")!!.readAllBytes()
        val privBytes = classLoader.getResourceAsStream("$base.priv")!!.readAllBytes()
        val sigBytes = classLoader.getResourceAsStream("$base.sig")!!.readAllBytes()
        val pub = CryptoUtil.unmarshalPublicKey(pubBytes).expectNoErrors()
        assertArrayEquals(pubBytes, pub.bytes().expectNoErrors())
        val priv = CryptoUtil.unmarshalPrivateKey(privBytes).expectNoErrors()
        assertArrayEquals(privBytes, priv.bytes().expectNoErrors())
        assertTrue(pub.verify(MESSAGE, sigBytes).expectNoErrors(), "failed to validate signature with public key")
    }

    companion object {
        private val MESSAGE = "Libp2p is the _best_!".toByteArray()
    }
}
