// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ecdsa

import org.erwinkok.libp2p.crypto.CryptoUtil
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.experimental.xor

internal class EcdsaUtilTest {
    @Test
    fun testEcdsaBasicSignAndVerify() {
        val (privateKey, publicKey) = Ecdsa.generateKeyPair().expectNoErrors()
        val data = "hello! and welcome to some awesome crypto primitives".toByteArray()
        val sig = privateKey.sign(data).expectNoErrors()
        assertTrue(publicKey.verify(data, sig).expectNoErrors(), "signature didn't match")
        data[0] = data[0] xor data[0]
        assertFalse(publicKey.verify(data, sig).expectNoErrors(), "signature matched and shouldn't")
    }

    @Test
    fun testEcdsaSignZero() {
        val (privateKey, publicKey) = Ecdsa.generateKeyPair().expectNoErrors()
        val data = ByteArray(0)
        val sig = privateKey.sign(data).expectNoErrors()
        assertTrue(publicKey.verify(data, sig).expectNoErrors(), "signature didn't match")
    }

    @Test
    fun testEcdsaMarshalLoop() {
        val (privateKey, publicKey) = Ecdsa.generateKeyPair().expectNoErrors()
        val privBytes = CryptoUtil.marshalPrivateKey(privateKey).expectNoErrors()
        val privKey2 = CryptoUtil.unmarshalPrivateKey(privBytes).expectNoErrors()
        assertEquals(privateKey, privKey2)
        val pubBytes = CryptoUtil.marshalPublicKey(publicKey).expectNoErrors()
        val pubKey2 = CryptoUtil.unmarshalPublicKey(pubBytes).expectNoErrors()
        assertEquals(publicKey, pubKey2)
    }

    @Test
    fun testKeys() {
        val encoder = Base64.getEncoder()
        val decoder = Base64.getDecoder()
        val priv = "CAMSeTB3AgEBBCDiWitc7Opgk/pAXQ5eji8IHc/SS9FnnVUBdvw6x8l32aAKBggqhkjOPQMBB6FEA0IABLEtC1Fe9SLKOTgeQfXJl8CntVgQFGj4mpqSIvxFrWzXkAVbVbxpUGzaZmJZSXhyI741fYqsyDrsSdjDzKoeuUY="
        val privKey = CryptoUtil.unmarshalPrivateKey(decoder.decode(priv)).expectNoErrors()
        val priv2 = encoder.encodeToString(privKey.bytes().expectNoErrors())
        assertEquals(priv, priv2)
        val pub = "CAMSWzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABLEtC1Fe9SLKOTgeQfXJl8CntVgQFGj4mpqSIvxFrWzXkAVbVbxpUGzaZmJZSXhyI741fYqsyDrsSdjDzKoeuUY="
        val pubKey = CryptoUtil.unmarshalPublicKey(decoder.decode(pub)).expectNoErrors()
        val pub2 = encoder.encodeToString(pubKey.bytes().expectNoErrors())
        assertEquals(pub, pub2)
    }
}
