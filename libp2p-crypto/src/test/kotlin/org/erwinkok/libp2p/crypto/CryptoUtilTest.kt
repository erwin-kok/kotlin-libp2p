// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto

import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class CryptoUtilTest {
    private val message = "Hello, World!".toByteArray()

    @Test
    fun ecdsaKeyGeneration() {
        for (i in 0..127) {
            val (priv, pub) = CryptoUtil.generateKeyPair(KeyType.ECDSA).expectNoErrors()
            assertEquals(priv.publicKey, pub)
            val signed = priv.sign(message).expectNoErrors()
            val verified = pub.verify(message, signed).expectNoErrors()
            assertTrue(verified)
            val wrong = pub.verify("Something else".toByteArray(), signed).expectNoErrors()
            assertFalse(wrong)
        }
    }

    @Test
    fun ed25519KeyGeneration() {
        for (i in 0..127) {
            val (priv, pub) = CryptoUtil.generateKeyPair(KeyType.ED25519).expectNoErrors()
            assertEquals(priv.publicKey, pub)
            val signed = priv.sign(message).expectNoErrors()
            val verified = pub.verify(message, signed).expectNoErrors()
            assertTrue(verified)
            val wrong = pub.verify("Something else".toByteArray(), signed).expectNoErrors()
            assertFalse(wrong)
        }
    }

    @Test
    fun secp256k1KeyGeneration() {
        for (i in 0..127) {
            val (priv, pub) = CryptoUtil.generateKeyPair(KeyType.SECP256K1).expectNoErrors()
            assertEquals(priv.publicKey, pub)
            val signed = priv.sign(message).expectNoErrors()
            val verified = pub.verify(message, signed).expectNoErrors()
            assertTrue(verified)
            val wrong = pub.verify("Something else".toByteArray(), signed).expectNoErrors()
            assertFalse(wrong)
        }
    }

    @Test
    fun rsaKeyGeneration() {
        for (i in 0..127) {
            val (priv, pub) = CryptoUtil.generateKeyPair(KeyType.RSA).expectNoErrors()
            assertEquals(priv.publicKey, pub)
            val signed = priv.sign(message).expectNoErrors()
            val verified = pub.verify(message, signed).expectNoErrors()
            assertTrue(verified)
            val wrong = pub.verify("Something else".toByteArray(), signed).expectNoErrors()
            assertFalse(wrong)
        }
    }

    @Test
    fun ecdsaKeyConvert() {
        for (i in 0..127) {
            val (priv, pub) = CryptoUtil.generateKeyPair(KeyType.ECDSA).expectNoErrors()
            assertEquals(priv.publicKey, pub)
            val pbPriv = CryptoUtil.convertPrivateKey(priv).expectNoErrors()
            val priv2 = CryptoUtil.convertPrivateKey(pbPriv).expectNoErrors()
            assertEquals(priv, priv2)

            val privBytes = CryptoUtil.marshalPrivateKey(priv).expectNoErrors()
            val priv3 = CryptoUtil.unmarshalPrivateKey(privBytes).expectNoErrors()
            assertEquals(priv, priv3)

            val pbPub = CryptoUtil.convertPublicKey(pub).expectNoErrors()
            val pub2 = CryptoUtil.convertPublicKey(pbPub).expectNoErrors()
            assertEquals(pub, pub2)

            val pubBytes = CryptoUtil.marshalPublicKey(pub).expectNoErrors()
            val pub3 = CryptoUtil.unmarshalPublicKey(pubBytes).expectNoErrors()
            assertEquals(pub, pub3)
        }
    }

    @Test
    fun ed25519KeyConvert() {
        for (i in 0..127) {
            val (priv, pub) = CryptoUtil.generateKeyPair(KeyType.ED25519).expectNoErrors()
            assertEquals(priv.publicKey, pub)
            val pbPriv = CryptoUtil.convertPrivateKey(priv).expectNoErrors()
            val priv2 = CryptoUtil.convertPrivateKey(pbPriv).expectNoErrors()
            assertEquals(priv, priv2)

            val privBytes = CryptoUtil.marshalPrivateKey(priv).expectNoErrors()
            val priv3 = CryptoUtil.unmarshalPrivateKey(privBytes).expectNoErrors()
            assertEquals(priv, priv3)

            val pbPub = CryptoUtil.convertPublicKey(pub).expectNoErrors()
            val pub2 = CryptoUtil.convertPublicKey(pbPub).expectNoErrors()
            assertEquals(pub, pub2)

            val pubBytes = CryptoUtil.marshalPublicKey(pub).expectNoErrors()
            val pub3 = CryptoUtil.unmarshalPublicKey(pubBytes).expectNoErrors()
            assertEquals(pub, pub3)
        }
    }

    @Test
    fun secp256k1KeyConvert() {
        for (i in 0..127) {
            val (priv, pub) = CryptoUtil.generateKeyPair(KeyType.SECP256K1).expectNoErrors()
            assertEquals(priv.publicKey, pub)
            val pbPriv = CryptoUtil.convertPrivateKey(priv).expectNoErrors()
            val priv2 = CryptoUtil.convertPrivateKey(pbPriv).expectNoErrors()
            assertEquals(priv, priv2)

            val privBytes = CryptoUtil.marshalPrivateKey(priv).expectNoErrors()
            val priv3 = CryptoUtil.unmarshalPrivateKey(privBytes).expectNoErrors()
            assertEquals(priv, priv3)

            val pbPub = CryptoUtil.convertPublicKey(pub).expectNoErrors()
            val pub2 = CryptoUtil.convertPublicKey(pbPub).expectNoErrors()
            assertEquals(pub, pub2)

            val pubBytes = CryptoUtil.marshalPublicKey(pub).expectNoErrors()
            val pub3 = CryptoUtil.unmarshalPublicKey(pubBytes).expectNoErrors()
            assertEquals(pub, pub3)
        }
    }

    @Test
    fun rsaKeyConvert() {
        for (i in 0..127) {
            val (priv, pub) = CryptoUtil.generateKeyPair(KeyType.RSA).expectNoErrors()
            assertEquals(priv.publicKey, pub)
            val pbPriv = CryptoUtil.convertPrivateKey(priv).expectNoErrors()
            val priv2 = CryptoUtil.convertPrivateKey(pbPriv).expectNoErrors()
            assertEquals(priv, priv2)

            val privBytes = CryptoUtil.marshalPrivateKey(priv).expectNoErrors()
            val priv3 = CryptoUtil.unmarshalPrivateKey(privBytes).expectNoErrors()
            assertEquals(priv, priv3)

            val pbPub = CryptoUtil.convertPublicKey(pub).expectNoErrors()
            val pub2 = CryptoUtil.convertPublicKey(pbPub).expectNoErrors()
            assertEquals(pub, pub2)

            val pubBytes = CryptoUtil.marshalPublicKey(pub).expectNoErrors()
            val pub3 = CryptoUtil.unmarshalPublicKey(pubBytes).expectNoErrors()
            assertEquals(pub, pub3)
        }
    }
}
