// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.secp256k1

import org.erwinkok.libp2p.crypto.CryptoUtil
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.experimental.inv

internal class Secp256k1Test {
    @Test
    fun basicSignAndVerify() {
        val (priv, pub) = Secp256k1.generateKeyPair().expectNoErrors()
        val data = "hello! and welcome to some awesome crypto primitives".toByteArray()
        val sig = priv.sign(data).expectNoErrors()
        val ok = pub.verify(data, sig).expectNoErrors()
        assertTrue(ok)

        // change data
        data[0] = data[0].inv()
        val notOk = pub.verify(data, sig).expectNoErrors()
        assertFalse(notOk)
    }

    @Test
    fun signZero() {
        val (priv, pub) = Secp256k1.generateKeyPair().expectNoErrors()
        val data = byteArrayOf()
        val sig = priv.sign(data).expectNoErrors()
        val ok = pub.verify(data, sig).expectNoErrors()
        assertTrue(ok)
    }

    @Test
    fun marshalLoop() {
        val (priv, pub) = Secp256k1.generateKeyPair().expectNoErrors()
        val privB = CryptoUtil.marshalPrivateKey(priv).expectNoErrors()
        val privNew = CryptoUtil.unmarshalPrivateKey(privB).expectNoErrors()
        assertEquals(priv, privNew)
        val pubB = CryptoUtil.marshalPublicKey(pub).expectNoErrors()
        val pubNew = CryptoUtil.unmarshalPublicKey(pubB).expectNoErrors()
        assertEquals(pub, pubNew)
    }

    @Test
    fun testKeys() {
        val encoder = Base64.getEncoder()
        val decoder = Base64.getDecoder()
        val priv = "CAISIGoAAEuKE89XnN1J/nFiaSOe/XouP2/fHjSGZrim/J23"
        val privKey = CryptoUtil.unmarshalPrivateKey(decoder.decode(priv)).expectNoErrors()
        val priv2 = encoder.encodeToString(privKey.bytes().expectNoErrors())
        assertEquals(priv, priv2)
        val pub = "CAISIQL4LoXrFdzvj7VTJMEg12Gl4kISxJH5BI23i2HkbY0Euw=="
        val pubKey = CryptoUtil.unmarshalPublicKey(decoder.decode(pub)).expectNoErrors()
        val pub2 = encoder.encodeToString(pubKey.bytes().expectNoErrors())
        assertEquals(pub, pub2)
    }
}
