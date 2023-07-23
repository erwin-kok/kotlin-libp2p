// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.peerstore

import kotlinx.coroutines.test.runTest
import org.erwinkok.libp2p.core.datastore.Key
import org.erwinkok.libp2p.core.datastore.Key.Companion.key
import org.erwinkok.libp2p.core.datastore.MapDatastore
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.host.RemoteIdentity
import org.erwinkok.libp2p.core.host.builder.KeyStoreConfig
import org.erwinkok.libp2p.core.host.builder.PeerstoreConfig
import org.erwinkok.multiformat.multibase.bases.Base32
import org.erwinkok.result.assertErrorResult
import org.erwinkok.result.coAssertErrorResult
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class KeyStoreTest {
    @Test
    fun createForSha1() = runTest {
        val ds = MapDatastore(this)
        val peerstoreConfig = createConfig("bladieblabladieblablabla", "sha1")
        KeyStore.create(ds, peerstoreConfig).expectNoErrors()
        ds.close()
    }

    @Test
    fun createForSha256() = runTest {
        val ds = MapDatastore(this)
        val peerstoreConfig = createConfig("bladieblabladieblablabla", "sha256")
        KeyStore.create(ds, peerstoreConfig).expectNoErrors()
        ds.close()
    }

    @Test
    fun createForSha512() = runTest {
        val ds = MapDatastore(this)
        val peerstoreConfig = createConfig("bladieblabladieblablabla", "sha512")
        KeyStore.create(ds, peerstoreConfig).expectNoErrors()
        ds.close()
    }

    @Test
    fun createForUnknown() = runTest {
        val ds = MapDatastore(this)
        val peerstoreConfig = createConfig("bladieblabladieblablabla", "sha123")
        assertErrorResult("Could not create SecretKeyFactory for hasher sha123") { KeyStore.create(ds, peerstoreConfig) }
        ds.close()
    }

    @Test
    fun storeLoadPrivateKey() = runTest {
        val ds = MapDatastore(this)
        val peerstoreConfig = createConfig("bladieblabladieblablabla", "sha512")
        val ks = KeyStore.create(ds, peerstoreConfig).expectNoErrors()
        val localIdentity = LocalIdentity.random().expectNoErrors()
        ks.addLocalIdentity(localIdentity).expectNoErrors()
        val loadedLocalIdentity1 = ks.localIdentity(localIdentity.peerId)
        assertNotNull(loadedLocalIdentity1)
        assertEquals(localIdentity, loadedLocalIdentity1)
        val bytes1 = ds.get(peerToKey(localIdentity.peerId)).expectNoErrors()
        val bytes2 = ds.get(peerToKey(localIdentity.peerId)).expectNoErrors()
        assertTrue(bytes1.contentEquals(bytes2))
        ks.rotateKeychainPass("newpassworda;ksdfhaldjksfhalsdfh").expectNoErrors()
        val loadedLocalIdentity2 = ks.localIdentity(localIdentity.peerId)
        assertNotNull(loadedLocalIdentity2)
        assertEquals(localIdentity, loadedLocalIdentity2)
        val bytes3 = ds.get(peerToKey(localIdentity.peerId)).expectNoErrors()
        assertFalse(bytes1.contentEquals(bytes3))
        val peers = ks.peersWithKeys()
        assertEquals(1, peers.size)
        assertEquals(setOf(localIdentity.peerId), peers)
        ds.close()
    }

    @Test
    fun storeLoadPrivateKeyNoDek() = runTest {
        val ds = MapDatastore(this)
        val ks = KeyStore.create(ds).expectNoErrors()
        val localIdentity = LocalIdentity.random().expectNoErrors()
        ks.addLocalIdentity(localIdentity).expectNoErrors()
        val loadedLocalIdentity1 = ks.localIdentity(localIdentity.peerId)
        assertNotNull(loadedLocalIdentity1)
        assertEquals(localIdentity, loadedLocalIdentity1)
        val bytes1 = ds.get(peerToKey(localIdentity.peerId)).expectNoErrors()
        val bytes2 = ds.get(peerToKey(localIdentity.peerId)).expectNoErrors()
        assertTrue(bytes1.contentEquals(bytes2))
        coAssertErrorResult("KeyStore is not configured with a DekConfig") { ks.rotateKeychainPass("newpassworda;ksdfhaldjksfhalsdfh") }
        val loadedLocalIdentity2 = ks.localIdentity(localIdentity.peerId)
        assertNotNull(loadedLocalIdentity2)
        assertEquals(localIdentity, loadedLocalIdentity2)
        val bytes3 = ds.get(peerToKey(localIdentity.peerId)).expectNoErrors()
        assertTrue(bytes1.contentEquals(bytes3))
        val peers = ks.peersWithKeys()
        assertEquals(1, peers.size)
        assertEquals(setOf(localIdentity.peerId), peers)
        ds.close()
    }

    @Test
    fun storeLoadPublicKey() = runTest {
        val ds = MapDatastore(this)
        val ks = KeyStore.create(ds).expectNoErrors()
        val localIdentity = LocalIdentity.random().expectNoErrors()
        val remoteIdentity = RemoteIdentity.fromPublicKey(localIdentity.publicKey).expectNoErrors()
        ks.addRemoteIdentity(remoteIdentity).expectNoErrors()
        val loadedRemoteIdentity = ks.remoteIdentity(remoteIdentity.peerId)
        assertNotNull(loadedRemoteIdentity)
        assertEquals(remoteIdentity, loadedRemoteIdentity)
        val peers = ks.peersWithKeys()
        assertEquals(1, peers.size)
        assertEquals(setOf(localIdentity.peerId), peers)
        ds.close()
    }

    @Test
    fun testKeyBookPeers() = runTest {
        val ds = MapDatastore(this)
        val ks = KeyStore.create(ds).expectNoErrors()
        val peers = ks.peersWithKeys()
        assertEquals(0, peers.size)
        val setPeers = mutableSetOf<PeerId>()
        repeat(1000) {
            val localIdentity = LocalIdentity.random().expectNoErrors()
            ks.addLocalIdentity(localIdentity).expectNoErrors()
            setPeers.add(localIdentity.peerId)

            val localIdentity1 = LocalIdentity.random().expectNoErrors()
            val remoteIdentity = RemoteIdentity.fromPublicKey(localIdentity1.publicKey).expectNoErrors()
            ks.addRemoteIdentity(remoteIdentity).expectNoErrors()
            setPeers.add(remoteIdentity.peerId)
        }
        val peersWithKeys = ks.peersWithKeys()
        assertEquals(setPeers, peersWithKeys)
        ds.close()
    }

    @Test
    fun testKeyBookDelete() = runTest {
        val ds = MapDatastore(this)
        val ks = KeyStore.create(ds).expectNoErrors()

        assertEquals(0, ks.peersWithKeys().size)

        val localIdentity = LocalIdentity.random().expectNoErrors()
        ks.addLocalIdentity(localIdentity).expectNoErrors()

        val localIdentity1 = LocalIdentity.random().expectNoErrors()
        val remoteIdentity = RemoteIdentity.fromPublicKey(localIdentity1.publicKey).expectNoErrors()
        ks.addRemoteIdentity(remoteIdentity).expectNoErrors()

        assertEquals(2, ks.peersWithKeys().size)

        assertEquals(localIdentity, ks.localIdentity(localIdentity.peerId))
        assertEquals(remoteIdentity, ks.remoteIdentity(remoteIdentity.peerId))

        ks.removePeer(localIdentity.peerId)
        assertNull(ks.localIdentity(localIdentity.peerId))
        assertEquals(remoteIdentity, ks.remoteIdentity(remoteIdentity.peerId))
        assertEquals(1, ks.peersWithKeys().size)

        ks.removePeer(remoteIdentity.peerId)
        assertNull(ks.localIdentity(localIdentity.peerId))
        // we can not retrieve the remoteIdentity here and check it whether it is null
        // this is because the remoteIdentity is automatically (re-)generated from the
        // given peerId
        assertEquals(0, ks.peersWithKeys().size)

        ds.close()
    }

    private fun peerToKey(peerId: PeerId): Key {
        val peerIdb32 = Base32.encodeStdLowerNoPad(peerId.idBytes())
        return key("/peers/keys/$peerIdb32/private")
    }

    private fun createConfig(password: String, hash: String): PeerstoreConfig {
        val peerstoreConfig = PeerstoreConfig()
        val keyStoreConfig = KeyStoreConfig()
        peerstoreConfig.keyStoreConfig = keyStoreConfig
        keyStoreConfig.password = password
        keyStoreConfig.dekConfig.hash = hash
        return peerstoreConfig
    }
}
