// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.rsa

import org.apache.kerby.asn1.type.Asn1Integer
import org.apache.kerby.asn1.type.Asn1Sequence
import org.erwinkok.libp2p.crypto.KeyPair
import org.erwinkok.libp2p.crypto.PrivateKey
import org.erwinkok.libp2p.crypto.PublicKey
import org.erwinkok.libp2p.crypto.asn1.Asn1Decoder
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.erwinkok.result.getOrElse
import java.io.IOException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPrivateCrtKeySpec
import java.security.spec.RSAPublicKeySpec
import java.security.spec.X509EncodedKeySpec

object Rsa {
    private const val MIN_RSA_KEY_BITS = 2048

    fun generateKeyPair(bits: Int): Result<KeyPair> {
        if (bits < MIN_RSA_KEY_BITS) {
            throw NumberFormatException("RSA key too small")
        }
        val rsaPrivateKey = generatePrivateKey(bits)
        return Ok(KeyPair(rsaPrivateKey, rsaPrivateKey.publicKey))
    }

    fun generatePrivateKey(bits: Int): RsaPrivateKey {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(bits)
        val pair = kpg.generateKeyPair()
        val privateKey = pair.private as RSAPrivateCrtKey
        val publicKey = pair.public as RSAPublicKey
        return RsaPrivateKey(RsaPublicKey(publicKey), privateKey)
    }

    // x509.MarshalPKCS1PrivateKey
    fun marshalPrivateKey(rsaPrivateKey: RsaPrivateKey): Result<ByteArray> {
        return try {
            val privateKey = rsaPrivateKey.privateKey
            val sequence = Asn1Sequence()
            sequence.addItem(Asn1Integer(0))
            sequence.addItem(Asn1Integer(privateKey.modulus))
            sequence.addItem(Asn1Integer(privateKey.publicExponent))
            sequence.addItem(Asn1Integer(privateKey.privateExponent))
            sequence.addItem(Asn1Integer(privateKey.primeP))
            sequence.addItem(Asn1Integer(privateKey.primeQ))
            sequence.addItem(Asn1Integer(privateKey.primeExponentP))
            sequence.addItem(Asn1Integer(privateKey.primeExponentQ))
            sequence.addItem(Asn1Integer(privateKey.crtCoefficient))
            Ok(sequence.encode())
        } catch (e: IOException) {
            Err("Could not marshal ASN1 PrivateKey: ${errorMessage(e)}")
        }
    }

    fun unmarshalPrivateKey(data: ByteArray): Result<PrivateKey> {
        return try {
            val decoder = Asn1Decoder.fromBytes(data)
                .getOrElse { return Err(it) }
            if (decoder.size < 6) {
                return Err("invalid length in ASN1 sequence.")
            }
            val version = decoder.nextInt
                .getOrElse { return Err(it) }
            if (version > 1) {
                return Err("x509: unsupported private key version")
            }
            val n = decoder.nextBigInt
                .getOrElse { return Err(it) }
            val e = decoder.nextInt
                .getOrElse { return Err(it) }
            val d = decoder.nextBigInt
                .getOrElse { return Err(it) }
            val p = decoder.nextBigInt
                .getOrElse { return Err(it) }
            val q = decoder.nextBigInt
                .getOrElse { return Err(it) }
            if (n.signum() <= 0 || d.signum() <= 0 || p.signum() <= 0 || q.signum() <= 0) {
                return Err("x509: private key contains zero or negative value")
            }
            if (n.bitLength() < MIN_RSA_KEY_BITS) {
                return Err("RSA key too small")
            }

            // We ignore these values, if present, because rsa will calculate them.
            val dp = decoder.nextBigInt // Dp
                .getOrElse { return Err(it) }
            val dq = decoder.nextBigInt // Dq
                .getOrElse { return Err(it) }
            val dInv = decoder.nextBigInt // Qinv
                .getOrElse { return Err(it) }
            while (decoder.index < decoder.size) {
                val a = decoder.nextBigInt
                    .getOrElse { return Err(it) }
                if (a.signum() <= 0) {
                    return Err("x509: private key contains zero or negative primes")
                }
                // We ignore the other two values because rsa will calculate
                // them as needed.
                decoder.nextBigInt
                decoder.nextBigInt
            }
            val keyFactory = KeyFactory.getInstance("RSA")
            val privKey = keyFactory.generatePrivate(RSAPrivateCrtKeySpec(n, BigInteger.valueOf(e.toLong()), d, p, q, dp, dq, dInv)) as RSAPrivateCrtKey
            val pubKey = keyFactory.generatePublic(RSAPublicKeySpec(n, BigInteger.valueOf(e.toLong()))) as RSAPublicKey
            val publicKey = RsaPublicKey(pubKey)
            Ok(RsaPrivateKey(publicKey, privKey))
        } catch (e: ClassCastException) {
            Err("Could not unmarshal ASN1 RsaPublicKey: ${errorMessage(e)}")
        } catch (e: IOException) {
            Err("Could not unmarshal ASN1 RsaPublicKey: ${errorMessage(e)}")
        }
    }

    fun marshalPublicKey(rsaPublicKey: RsaPublicKey): Result<ByteArray> {
        val publicKey = rsaPublicKey.pubKey
        require(publicKey.format == "X.509")
        return Ok(publicKey.encoded)
    }

    fun unmarshalPublicKey(data: ByteArray): Result<PublicKey> {
        val keyFactory = KeyFactory.getInstance("RSA")
        val pubKey = keyFactory.generatePublic(X509EncodedKeySpec(data)) as RSAPublicKey
        val pub = RsaPublicKey(pubKey)
        if (pubKey.modulus.bitLength() < MIN_RSA_KEY_BITS) {
            return Err("RSA key too small")
        }
        return Ok(pub)
    }
}
