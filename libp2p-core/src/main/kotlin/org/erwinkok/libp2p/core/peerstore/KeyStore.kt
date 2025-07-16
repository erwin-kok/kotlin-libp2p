// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.peerstore

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.toSet
import org.erwinkok.libp2p.core.datastore.BatchingDatastore
import org.erwinkok.libp2p.core.datastore.Datastore
import org.erwinkok.libp2p.core.datastore.Key
import org.erwinkok.libp2p.core.datastore.Key.Companion.key
import org.erwinkok.libp2p.core.datastore.query.Query
import org.erwinkok.libp2p.core.datastore.query.queryFilterError
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.host.RemoteIdentity
import org.erwinkok.libp2p.core.host.builder.DekConfig
import org.erwinkok.libp2p.core.host.builder.PeerstoreConfig
import org.erwinkok.libp2p.crypto.CryptoUtil
import org.erwinkok.multiformat.multibase.bases.Base32
import org.erwinkok.multiformat.multibase.bases.Base64
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.erwinkok.result.flatMap
import org.erwinkok.result.flatMapIfError
import org.erwinkok.result.getOr
import org.erwinkok.result.getOrElse
import org.erwinkok.result.map
import org.erwinkok.result.mapIfError
import org.erwinkok.result.onFailure
import org.erwinkok.result.onSuccess
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

class KeyStore private constructor(
    private val datastore: BatchingDatastore,
    private val dekConfig: DekConfig? = null,
    private var secretKey: SecretKey? = null,
) {
    suspend fun addRemoteIdentity(remoteIdentity: RemoteIdentity): Result<Unit> {
        if (!remoteIdentity.peerId.matchesPublicKey(remoteIdentity.publicKey)) {
            randomDelay()
            return Err("PeerId does not match public key")
        }
        val publicKeyBytes = CryptoUtil.marshalPublicKey(remoteIdentity.publicKey)
            .getOrElse {
                randomDelay()
                return Err("Could not marshal public key for $remoteIdentity")
            }
        return datastore.put(peerToKey(remoteIdentity.peerId, PublicSuffix), publicKeyBytes)
    }

    suspend fun remoteIdentity(peerId: PeerId): RemoteIdentity? {
        val key = peerToKey(peerId, PublicSuffix)
        return datastore.get(key)
            .flatMap { CryptoUtil.unmarshalPublicKey(it) }
            .flatMap { RemoteIdentity.fromPublicKey(it) }
            .flatMapIfError(Datastore.ErrNotFound) { _ ->
                val publicKey = peerId.extractPublicKey()
                    .mapIfError(PeerId.ErrNoPublicKey) {
                        logger.info { "Peer $peerId has no public key" }
                        return null
                    }
                    .getOrElse {
                        logger.info { "Could not extract public key for peer: ${errorMessage(it)}" }
                        return null
                    }
                CryptoUtil.marshalPublicKey(publicKey)
                    .map { datastore.put(key, it) }
                    .onFailure {
                        logger.info { "Could not store public key for peer $peerId" }
                    }
                RemoteIdentity.fromPublicKey(publicKey)
            }.getOrElse {
                logger.info { "Could not extract public key for peer: ${errorMessage(it)}" }
                return null
            }
    }

    suspend fun addLocalIdentity(localIdentity: LocalIdentity): Result<Unit> {
        if (!localIdentity.peerId.matchesPrivateKey(localIdentity.privateKey)) {
            randomDelay()
            return Err("PeerId does not match private key")
        }
        val privateKeyBytes = CryptoUtil.marshalPrivateKey(localIdentity.privateKey)
            .getOrElse {
                randomDelay()
                return Err("Could not marshal private key for $localIdentity")
            }
        return datastore.put(peerToKey(localIdentity.peerId, PrivateSuffix), encrypt(privateKeyBytes, secretKey))
    }

    suspend fun localIdentity(peerId: PeerId): LocalIdentity? {
        val key = peerToKey(peerId, PrivateSuffix)
        return datastore.get(key)
            .map { decrypt(it, secretKey) }
            .flatMap { CryptoUtil.unmarshalPrivateKey(it) }
            .flatMap { LocalIdentity.fromPrivateKey(it) }
            .getOr(null)
    }

    suspend fun peersWithKeys(): Set<PeerId> {
        return uniquePeerIds()
            .getOrElse {
                logger.warn { "Could not retrieve keys from datastore: ${errorMessage(it)}: " }
                return setOf()
            }
            .toSet()
    }

    suspend fun removePeer(peerId: PeerId) {
        datastore.delete(peerToKey(peerId, PublicSuffix))
        datastore.delete(peerToKey(peerId, PrivateSuffix))
    }

    suspend fun rotateKeychainPass(newPassword: String): Result<Unit> {
        if (newPassword.length < 20) {
            randomDelay()
            return Err("Password should be at least 20 characters")
        }
        if (dekConfig == null || secretKey == null) {
            randomDelay()
            return Err("KeyStore is not configured with a DekConfig")
        }
        logger.info { "recreating keychain" }
        val newSecretKey = createSecretKey(dekConfig, newPassword)
            .getOrElse {
                randomDelay()
                return Err(it)
            }
        val keys = privateKeys()
            .getOrElse {
                randomDelay()
                return Err(it)
            }
        val batch = datastore.batch()
            .getOrElse {
                randomDelay()
                return Err(it)
            }
        keys
            .onCompletion { batch.commit() }
            .collect { key ->
                datastore.get(key)
                    .onSuccess { result ->
                        val bytes = decrypt(result, secretKey)
                        batch.put(key, encrypt(bytes, newSecretKey))
                        secretKey = newSecretKey
                    }
                    .onFailure {
                        logger.warn { "Could not rotate key ${key.name}" }
                    }
            }
        return Ok(Unit)
    }

    private suspend fun randomDelay() {
        delay(Random.nextLong(200, 1000))
    }

    // "/peers/keys/<peer-id>/public"
    // "/peers/keys/<peer-id>/private"
    private fun uniquePeerIds(): Result<Flow<PeerId>> {
        val keys = datastore.query(Query(prefix = KeyBase, keysOnly = true))
            .getOrElse { return Err(it) }
        val peers = keys
            .queryFilterError { logger.error { "Error retrieving peer ids: ${errorMessage(it)}" } }
            .map { it.key.parent().name }
            .mapNotNull {
                Base32.decodeStdNoPad(it)
                    .flatMap { peerBytes -> PeerId.fromBytes(peerBytes) }
                    .onFailure { logger.warn { "Can not convert PeerId from bytes in KeyStore" } }
                    .getOr(null)
            }
        return Ok(peers)
    }

    private fun privateKeys(): Result<Flow<Key>> {
        val keys = datastore.query(Query(prefix = KeyBase, keysOnly = true))
            .getOrElse { return Err(it) }
        val privateKeys = keys
            .queryFilterError { logger.error { "Error retrieving keys: ${errorMessage(it)}" } }
            .filter { "/${it.key.name}" == PrivateSuffix }
            .mapNotNull { it.key }
        return Ok(privateKeys)
    }

    private fun peerToKey(peerId: PeerId, suffix: String): Key {
        val peerIdb32 = Base32.encodeStdLowerNoPad(peerId.idBytes())
        return key("$KeyBase/$peerIdb32/$suffix")
    }

    companion object {
        private const val KeyBase = "/peers/keys"
        private const val PublicSuffix = "/public"
        private const val PrivateSuffix = "/private"
        private const val nistMinKeyLength = 112
        private const val nistMinSaltLength = 128 / 8
        private const val nistMinIterationCount = 1000
        private const val ENCRYPT_ALGO = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BIT = 128
        private const val IV_LENGTH_BYTE = 12

        fun create(datastore: BatchingDatastore, peerstoreConfig: PeerstoreConfig? = null): Result<KeyStore> {
            val keyStoreConfig = peerstoreConfig?.keyStoreConfig
            if (keyStoreConfig != null) {
                val password = keyStoreConfig.password
                if (password == null || password.length < 20) {
                    return Err("Password should be at least 20 characters")
                }
                val secretKey = createSecretKey(keyStoreConfig.dekConfig, password)
                    .getOrElse { return Err(it) }
                return Ok(KeyStore(datastore, keyStoreConfig.dekConfig, secretKey))
            } else {
                return Ok(KeyStore(datastore))
            }
        }

        private fun createSecretKey(dekConfig: DekConfig, password: String): Result<SecretKey> {
            val keyLength = dekConfig.keyLength
            if (keyLength < nistMinKeyLength) {
                return Err("KeyLength should be at least $nistMinKeyLength bytes")
            }
            val salt = dekConfig.salt
            if (salt.length < nistMinSaltLength) {
                return Err("Salt should be at least $nistMinKeyLength bytes")
            }
            val saltBytes = Base64.decodeStringStd(salt)
                .getOrElse { return Err("Could not decode salt: ${errorMessage(it)}") }
            val iterationCount = dekConfig.iterationCount
            if (iterationCount < nistMinIterationCount) {
                return Err("IterationCount should be at least $nistMinIterationCount")
            }
            val spec = PBEKeySpec(password.toCharArray(), saltBytes, iterationCount, keyLength)
            val factory = getSecretKeyFactoryForHasher(dekConfig.hash)
                .getOrElse { return Err(it) }
            val hash = factory.generateSecret(spec).encoded
            return Ok(SecretKeySpec(hash, "AES"))
        }

        private fun getSecretKeyFactoryForHasher(hash: String): Result<SecretKeyFactory> {
            val factory =
                try {
                    when (hash) {
                        "sha-1", "sha1" -> SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                        "sha2-256", "sha256" -> SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        "sha2-512", "sha512" -> SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
                        else -> null
                    }
                } catch (_: NoSuchAlgorithmException) {
                    null
                }
            return if (factory == null) {
                Err("Could not create SecretKeyFactory for hasher $hash")
            } else {
                Ok(factory)
            }
        }

        private fun encrypt(plain: ByteArray, secretKey: SecretKey?): ByteArray {
            return if (secretKey != null) {
                val iv = ByteArray(IV_LENGTH_BYTE)
                SecureRandom().nextBytes(iv)
                val cipher = Cipher.getInstance(ENCRYPT_ALGO)
                val parameterSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
                val cipherText = cipher.doFinal(plain)
                iv + cipherText
            } else {
                plain
            }
        }

        private fun decrypt(encrypted: ByteArray, secretKey: SecretKey?): ByteArray {
            return if (secretKey != null) {
                val iv = encrypted.copyOfRange(0, IV_LENGTH_BYTE)
                val cipherText = encrypted.copyOfRange(IV_LENGTH_BYTE, encrypted.size)
                val cipher = Cipher.getInstance(ENCRYPT_ALGO)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BIT, iv))
                return cipher.doFinal(cipherText)
            } else {
                encrypted
            }
        }
    }
}
