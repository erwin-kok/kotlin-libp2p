// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.rsa

import org.erwinkok.libp2p.crypto.CryptoUtil
import org.erwinkok.libp2p.crypto.math.BigInt
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigInteger
import java.util.Base64
import javax.crypto.Cipher
import kotlin.experimental.xor

internal class RsaUtilTest {
    @Test
    fun testKeyGeneration() {
        val priv = Rsa.generatePrivateKey(1024)
        assertEquals(1024, priv.rsaPublicKey.pubKey.modulus.bitLength())
        assertFalse(priv.privateKey.privateExponent > priv.rsaPublicKey.pubKey.modulus)
        val pub = priv.rsaPublicKey
        val m = BigInteger.valueOf(42)
        val c = encrypt(pub, m)
        val m2 = decrypt(priv, c)
        assertEquals(0, m.compareTo(m2))
        val m3 = decrypt(priv, c)
        assertEquals(0, m.compareTo(m3))
    }

    @Test
    fun testRsaBasicSignAndVerify() {
        val pair = Rsa.generateKeyPair(2048).expectNoErrors()
        val data = "hello! and welcome to some awesome crypto primitives".toByteArray()
        val sig: ByteArray = pair.privateKey.sign(data).expectNoErrors()
        assertTrue(pair.publicKey.verify(data, sig).expectNoErrors(), "signature didn't match")
        data[0] = data[0] xor data[0]
        assertFalse(pair.publicKey.verify(data, sig).expectNoErrors(), "signature matched and shouldn't")
    }

    @Test
    fun testRsaSignZero() {
        val pair = Rsa.generateKeyPair(2048).expectNoErrors()
        val data = ByteArray(0)
        val sig: ByteArray = pair.privateKey.sign(data).expectNoErrors()
        assertTrue(pair.publicKey.verify(data, sig).expectNoErrors(), "signature didn't match")
    }

    @Test
    fun testRsaSmallKey() {
        assertEquals("RSA key too small", assertThrows<NumberFormatException> { Rsa.generateKeyPair(384) }.message)
    }

    @Test
    fun testRsaMarshalLoop() {
        val pair = Rsa.generateKeyPair(2048).expectNoErrors()
        val privBytes: ByteArray = pair.privateKey.raw().expectNoErrors()
        val privNew = Rsa.unmarshalPrivateKey(privBytes).expectNoErrors()
        assertEquals(pair.privateKey, privNew)
        val pubBytes: ByteArray = pair.publicKey.raw().expectNoErrors()
        val pubNew = Rsa.unmarshalPublicKey(pubBytes).expectNoErrors()
        assertEquals(pair.publicKey, pubNew)
        assertEquals(pair.publicKey, pair.privateKey.publicKey)
    }

    @Test
    fun testKeys() {
        val encoder = Base64.getEncoder()
        val decoder = Base64.getDecoder()
        val priv =
            "CAASpwkwggSjAgEAAoIBAQCv5WKPozKLNtqwlMzc4od1j5+HYbVc5PAEGaYepYrwYoRcfWkbOzKfpjrnyUo79mnTdWpXkvlf/akiD9YDoaeo02BMymBMM/KxIYQ4/6nKhTc9GtjvonmjX03xKVLe+oIpC+EB+wmUcOeLKPmH1x35qXAXUj1MEefVbKuINjfrunSBxiYfnf67TR/Ia3R9mWM4gJdZV7bN9Miq4GkzaXXUclr9gdvy0tZDK1y6r7DaQlRpm95BpXlr8lzlvTU3LgyhthkL0t2Xx54yhwacar6H2qAru64bfK+U7mVgfin5F/Zlx+Coi6EH4qD0yrzJeugh3IUOM66ibFO+qqYpoq65AgMBAAECggEAFhlx2q4caZVIwKrRWmczsbeLyYyjJrq01S8LygnufOlDzAMNs5gqchiGihymMQZyoVi9NaeHoWHTYC4xK1+iGvoDvWIn2ysjsNGPNUIZ6RH3sLuwydrWAYq11jjk6pL6y4Fskb0ipP3SeY96WnDSmU2KgcOZY/dT82Kl4oU9XWZobDn45K4Oe1YgVWZheH0fmHDUHIAqgf5GAXq1xHn3KdZ5puV53Z6557+mBwdkOyGtouEKbCpHkFhngh7AHn+5quu8EWa7smXy+nqYNRZrWTbu+o2vTErYgnxJNzHTKBwptnkNJwVTx8cIWU5M/eO7CZL3nzRWMdqcwHSFOPcNIQKBgQDYCZy9zX4HJ/+pDzk3XVAvtTUnN1UY4oxeypYtiEYOnpuIE5WkIkeu+q2RvrCYVSQ2LR4ukasgVQq3AOBLc51IPqwSEDAjXNW9Z6/v5YFhp/wWl1BZsqUbj4QJqBBtfdklGzL/DrkaQ3MmHCy8sxPyyAnqU9eIOzaqX5GVM2OJTQKBgQDQbuJsh2J3AOi9kAKUtl91oAJbokgKu1FIPmj5WLG/Q9dWUnn3q/ZuR4KmZpfV+EexhrbaPnm0GnM/1p2LBersuO4TwchAMhBdw2RH6OLCDf+8db5IuF4NG9aQF9gt9tIRcUv0UK6vZkSSJ1GCGjwmH19IhOKqqBoIyVN25JQlHQKBgEmirRw8qJJD3e5/0969HZHFUAK353d98J52qs2GP2rIQPcWxdCWJpzLsNGWj5a8noUgx8LTv+JbWjWaRNky5Q803W2iuuWyxN+0MdGxBnKE0XXZyXdpXsGQH08zS7YmSRdOuAkbuZfsGZmJzO3clBYSfN60CSjUFgPoYzTZuTmZAoGBAMbIqetFpzPFyzJW8Q8xa5M72mPYPor1oQyccPM1krfOFMX//NCn0WvViZX3jGGF26JEz4kPQnTxMO82WKQpLac8q9pt2vTWimNSIQav5eua/EaZqLOkGha4cQaVpxgXKLt36S+F70Pa7hTRqNvC95CIJRB9o8uMbN/qWk6uq1dFAoGAby9e9V9vf86C5HLq2da1lzv0M8WQbcgCyfT7ycfxD9NtAeM7O2k/NAuHKbr3YxRoSeAxjUCvB2DCZXA4nb5CHfjflfNDj8B3AWaCoe9sx0iBKRuVtwMLfY5GXShyyhdUO+oziYpf6T04XdQWfrxY/8Tewon2U8ZAJT7Kk7BK0cY="
        val privKey = CryptoUtil.unmarshalPrivateKey(decoder.decode(priv)).expectNoErrors()
        val priv2 = encoder.encodeToString(privKey.bytes().expectNoErrors())
        assertEquals(priv, priv2)
        val pub = "CAASpgIwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCv5WKPozKLNtqwlMzc4od1j5+HYbVc5PAEGaYepYrwYoRcfWkbOzKfpjrnyUo79mnTdWpXkvlf/akiD9YDoaeo02BMymBMM/KxIYQ4/6nKhTc9GtjvonmjX03xKVLe+oIpC+EB+wmUcOeLKPmH1x35qXAXUj1MEefVbKuINjfrunSBxiYfnf67TR/Ia3R9mWM4gJdZV7bN9Miq4GkzaXXUclr9gdvy0tZDK1y6r7DaQlRpm95BpXlr8lzlvTU3LgyhthkL0t2Xx54yhwacar6H2qAru64bfK+U7mVgfin5F/Zlx+Coi6EH4qD0yrzJeugh3IUOM66ibFO+qqYpoq65AgMBAAE="
        val pubKey = CryptoUtil.unmarshalPublicKey(decoder.decode(pub)).expectNoErrors()
        val pub2 = encoder.encodeToString(pubKey.bytes().expectNoErrors())
        assertEquals(pub, pub2)
    }

    // decrypt performs an RSA decryption, resulting in a plaintext integer. If a
    // random source is given, RSA blinding is used.
    private fun decrypt(priv: RsaPrivateKey, c: BigInteger): BigInteger {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, priv.privateKey)
        return BigInt.fromBytes(cipher.doFinal(BigInt.toBytes(c)))
    }

    private fun encrypt(pub: RsaPublicKey, m: BigInteger): BigInteger {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, pub.pubKey)
        return BigInt.fromBytes(cipher.doFinal(BigInt.toBytes(m)))
    }
}
