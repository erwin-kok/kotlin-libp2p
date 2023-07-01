// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ed25519

import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import org.erwinkok.libp2p.crypto.CryptoUtil
import org.erwinkok.result.assertErrorResult
import org.erwinkok.result.expectNoErrors
import org.erwinkok.util.Hex
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.Scanner
import java.util.zip.GZIPInputStream
import kotlin.experimental.xor

internal class Ed25519UtilTest {
    @Test
    @Suppress("UNCHECKED_CAST")
    fun vectors() {
        val classLoader = this.javaClass.classLoader
        val resource = classLoader.getResourceAsStream("crypto/ed25519vectors.json.gz")
        assertNotNull(resource)
        val input = ByteArrayInputStream(resource?.readAllBytes())
        val gis = GZIPInputStream(input)
        val testVectors = Parser.default().parse(gis) as JsonArray<JsonObject>
        for (testVector in testVectors) {
            val publicKey = Hex.decodeOrThrow(testVector["A"] as String)
            val signatureR = Hex.decodeOrThrow(testVector["R"] as String)
            val signatureS = Hex.decodeOrThrow(testVector["S"] as String)
            val message = testVector["M"] as String
            var expectedToVerify = true
            if (testVector.containsKey("Flags") && testVector["Flags"] != null) {
                val flags = testVector["Flags"] as JsonArray<String>
                for (flag in flags) {
                    if (flag == "LowOrderResidue") {
                        // We use the simplified verification formula that doesn't multiply
                        // by the cofactor, so any low order residue will cause the
                        // signature not to verify.
                        //
                        // This is allowed, but not required, by RFC 8032.
                        expectedToVerify = false
                    } else if (flag == "NonCanonicalR") {
                        // Our point decoding allows non-canonical encodings (in violation
                        // of RFC 8032) but R is not decoded: instead, R is recomputed and
                        // compared bytewise against the canonical encoding.
                        expectedToVerify = false
                    }
                }
            }
            val signature = ByteArray(signatureR.size + signatureS.size)
            System.arraycopy(signatureR, 0, signature, 0, signatureR.size)
            System.arraycopy(signatureS, 0, signature, signatureR.size, signatureS.size)
            val didVerify = Ed25519.verify(publicKey, message.toByteArray(), signature).expectNoErrors()
            assertEquals(expectedToVerify, didVerify)
        }
    }

    @Test
    fun golden() {
        val classLoader = Ed25519UtilTest::class.java.classLoader
        val resource = classLoader.getResourceAsStream("crypto/sign.input.gz")
        assertNotNull(resource)
        val input = ByteArrayInputStream(resource?.readAllBytes())
        val gis = GZIPInputStream(input)
        val scanner = Scanner(gis)
        var lineNo = 0
        while (scanner.hasNextLine()) {
            lineNo++
            val line = scanner.nextLine()
            val parts = line.split(":").toTypedArray()
            require(parts.size >= 4) { "bad number of parts on line $lineNo" }
            val privBytes = Hex.decodeOrThrow(parts[0])
            val pubKey = Hex.decodeOrThrow(parts[1])
            val msg = Hex.decodeOrThrow(parts[2])
            var sig = Hex.decodeOrThrow(parts[3])
            // The signatures in the test vectors also include the message
            // at the end, but we just want R and S.
            sig = sig.copyOf(Ed25519.SIGNATURE_SIZE)
            assertEquals(Ed25519.PUBLIC_KEY_SIZE, pubKey.size)
            val priv = ByteArray(Ed25519.PRIVATE_KEY_SIZE)
            System.arraycopy(privBytes, 0, priv, 0, privBytes.size)
            System.arraycopy(pubKey, 0, priv, 32, pubKey.size)
            val sig2 = Ed25519.sign(priv, msg).expectNoErrors()
            assertArrayEquals(sig, sig2)
            assertTrue(Ed25519.verify(pubKey, msg, sig2).expectNoErrors())
            val priv2 = Ed25519.generatePrivateKey(priv.copyOf(32)).expectNoErrors()
            assertArrayEquals(priv, priv2)
            val pubKey2 = Ed25519.publicKey(priv2)
            assertArrayEquals(pubKey, pubKey2)
            val seed2 = Ed25519.seed(priv2)
            assertArrayEquals(priv.copyOf(32), seed2)
        }
    }

    @Test
    fun malleability() {
        // https://tools.ietf.org/html/rfc8032#section-5.1.7 adds an additional test
        // that s be in [0, order). This prevents someone from adding a multiple of
        // order to s and obtaining a second valid signature for the same message.
        val msg = Hex.decodeOrThrow("54657374")
        val sig = Hex.decodeOrThrow("7c38e026f29e14aabd059a0f2db8b0cd783040609a8be684db12f82a27774ab067654bce3832c2d76f8f6f5dafc08d9339d4eef676573336a5c51eb6f946b31d")
        val publicKey = Hex.decodeOrThrow("7d4d0e7f6153a69b6242b522abbee685fda4420f8834b108c3bdae369ef549fa")
        assertErrorResult("invalid scalar encoding") { Ed25519.verify(publicKey, msg, sig) }
    }

    @Test
    fun testEd25519BasicSignAndVerify() {
        val (privateKey, publicKey) = Ed25519.generateKeyPair().expectNoErrors()
        val data = "hello! and welcome to some awesome crypto primitives".toByteArray()
        val sig = privateKey.sign(data).expectNoErrors()
        assertTrue(publicKey.verify(data, sig).expectNoErrors(), "signature didn't match")
        data[0] = data[0] xor data[0]
        assertFalse(publicKey.verify(data, sig).expectNoErrors(), "signature matched and shouldn't")
    }

    @Test
    fun testEd25519SignZero() {
        val (privateKey, publicKey) = Ed25519.generateKeyPair().expectNoErrors()
        val data = ByteArray(0)
        val sig = privateKey.sign(data).expectNoErrors()
        assertTrue(publicKey.verify(data, sig).expectNoErrors(), "signature didn't match")
    }

    @Test
    fun testEd25519MarshalLoop() {
        val (privateKey, publicKey) = Ed25519.generateKeyPair().expectNoErrors()
        val privB = privateKey.raw().expectNoErrors()
        val privNew = Ed25519.unmarshalPrivateKey(privB).expectNoErrors()
        assertEquals(privateKey, privNew)
        val pubB = publicKey.raw().expectNoErrors()
        val pubNew = Ed25519.unmarshalPublicKey(pubB).expectNoErrors()
        assertEquals(publicKey, pubNew)
    }

    @Test
    fun testKeys() {
        val encoder = Base64.getEncoder()
        val decoder = Base64.getDecoder()
        val priv = "CAESQCyQRsKxHXaYKb7msr/RuqIGPU3Pclr9zvGvJhFyoF7eGhr8zPfh7TyGX32X7DrolTySt7NfVqyT7dXdpELQhV4="
        val privKey = CryptoUtil.unmarshalPrivateKey(decoder.decode(priv)).expectNoErrors()
        val priv2 = encoder.encodeToString(privKey.bytes().expectNoErrors())
        assertEquals(priv, priv2)
        val pub = "CAESIBoa/Mz34e08hl99l+w66JU8krezX1ask+3V3aRC0IVe"
        val pubKey = CryptoUtil.unmarshalPublicKey(decoder.decode(pub)).expectNoErrors()
        val pub2 = encoder.encodeToString(pubKey.bytes().expectNoErrors())
        assertEquals(pub, pub2)
    }
}
