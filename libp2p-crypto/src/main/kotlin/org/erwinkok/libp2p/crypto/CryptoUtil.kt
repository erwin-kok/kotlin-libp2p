// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto

import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import mu.KotlinLogging
import org.erwinkok.libp2p.crypto.ecdsa.Ecdsa
import org.erwinkok.libp2p.crypto.ed25519.Ed25519
import org.erwinkok.libp2p.crypto.pb.Crypto
import org.erwinkok.libp2p.crypto.rsa.Rsa
import org.erwinkok.libp2p.crypto.secp256k1.Secp256k1
import org.erwinkok.result.Err
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.erwinkok.result.map
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom

private val logger = KotlinLogging.logger {}

object CryptoUtil {
    val sha256Digest: MessageDigest by lazy { init256Digest() }

    fun generateKeyPair(keyType: KeyType, bits: Int = 2048, random: SecureRandom = SecureRandom()): Result<KeyPair> {
        return when (keyType) {
            KeyType.RSA -> Rsa.generateKeyPair(bits)
            KeyType.ED25519 -> Ed25519.generateKeyPair(random)
            KeyType.SECP256K1 -> Secp256k1.generateKeyPair()
            KeyType.ECDSA -> Ecdsa.generateKeyPair(random)
        }
    }

    fun unmarshalPublicKey(rawPubKey: ByteArray): Result<PublicKey> {
        return try {
            val decoded = Crypto.PublicKey.parseFrom(rawPubKey)
            val data = decoded.data.toByteArray()
            when (decoded.type) {
                Crypto.KeyType.RSA -> Rsa.unmarshalPublicKey(data)
                Crypto.KeyType.Ed25519 -> Ed25519.unmarshalPublicKey(data)
                Crypto.KeyType.Secp256k1 -> Secp256k1.unmarshalPublicKey(data)
                Crypto.KeyType.ECDSA -> Ecdsa.unmarshalPublicKey(data)
                else -> Err("Unknown keyType: ${decoded.type}")
            }
        } catch (e: InvalidProtocolBufferException) {
            Err("Could not parse protocol buffer: ${errorMessage(e)}")
        }
    }

    fun marshalPublicKey(publicKey: PublicKey): Result<ByteArray> {
        return try {
            publicKey.raw()
                .map {
                    Crypto.PublicKey.newBuilder()
                        .setType(publicKey.keyType)
                        .setData(ByteString.copyFrom(it))
                        .build()
                        .toByteArray()
                }
        } catch (e: Exception) {
            Err("Could not build protocol buffer: ${errorMessage(e)}")
        }
    }

    fun unmarshalPrivateKey(rawPrivKey: ByteArray): Result<PrivateKey> {
        return try {
            val decoded = Crypto.PrivateKey.parseFrom(rawPrivKey)
            val data = decoded.data
            when (decoded.type) {
                Crypto.KeyType.RSA -> Rsa.unmarshalPrivateKey(data.toByteArray())
                Crypto.KeyType.Ed25519 -> Ed25519.unmarshalPrivateKey(data.toByteArray())
                Crypto.KeyType.Secp256k1 -> Secp256k1.unmarshalPrivateKey(data.toByteArray())
                Crypto.KeyType.ECDSA -> Ecdsa.unmarshalPrivateKey(data.toByteArray())
                else -> Err("Unknown keyType: ${decoded.type}")
            }
        } catch (e: InvalidProtocolBufferException) {
            Err("Could not parse protocol buffer: ${errorMessage(e)}")
        }
    }

    fun marshalPrivateKey(privateKey: PrivateKey): Result<ByteArray> {
        return try {
            privateKey.raw()
                .map {
                    Crypto.PrivateKey.newBuilder()
                        .setType(privateKey.keyType)
                        .setData(ByteString.copyFrom(it))
                        .build()
                        .toByteArray()
                }
        } catch (e: Exception) {
            Err("Could not build protocol buffer: ${errorMessage(e)}")
        }
    }

    fun convertPublicKey(publicKey: PublicKey): Result<Crypto.PublicKey> {
        return try {
            marshalPublicKey(publicKey)
                .map { Crypto.PublicKey.parseFrom(it) }
        } catch (e: InvalidProtocolBufferException) {
            Err("Could not parse protocol buffer: ${errorMessage(e)}")
        }
    }

    fun convertPublicKey(publicKey: Crypto.PublicKey): Result<PublicKey> {
        return unmarshalPublicKey(publicKey.toByteArray())
    }

    fun convertPrivateKey(privateKey: PrivateKey): Result<Crypto.PrivateKey> {
        return try {
            marshalPrivateKey(privateKey)
                .map { Crypto.PrivateKey.parseFrom(it) }
        } catch (e: InvalidProtocolBufferException) {
            Err("Could not parse protocol buffer: ${errorMessage(e)}")
        }
    }

    fun convertPrivateKey(privateKey: Crypto.PrivateKey): Result<PrivateKey> {
        return unmarshalPrivateKey(privateKey.toByteArray())
    }

    private fun init256Digest(): MessageDigest {
        try {
            return MessageDigest.getInstance("SHA-256")
        } catch (e: NoSuchAlgorithmException) {
            logger.error(e) { "Could not find algorithm: " + e.message }
            throw e
        }
    }
}
