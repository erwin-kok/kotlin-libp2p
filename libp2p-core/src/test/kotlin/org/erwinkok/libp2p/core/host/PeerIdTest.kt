// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.host

import org.erwinkok.libp2p.core.host.PeerId.Companion.decode
import org.erwinkok.libp2p.core.host.PeerId.Companion.fromCid
import org.erwinkok.libp2p.core.host.PeerId.Companion.fromPrivateKey
import org.erwinkok.libp2p.core.host.PeerId.Companion.fromPublicKey
import org.erwinkok.libp2p.core.host.PeerId.Companion.fromString
import org.erwinkok.libp2p.crypto.CryptoUtil.generateKeyPair
import org.erwinkok.libp2p.crypto.CryptoUtil.unmarshalPrivateKey
import org.erwinkok.libp2p.crypto.KeyType
import org.erwinkok.libp2p.crypto.PrivateKey
import org.erwinkok.libp2p.crypto.PublicKey
import org.erwinkok.multiformat.cid.Cid
import org.erwinkok.multiformat.multibase.bases.Base58
import org.erwinkok.multiformat.multibase.bases.Base64
import org.erwinkok.multiformat.multicodec.Multicodec
import org.erwinkok.multiformat.multihash.Multihash
import org.erwinkok.result.assertErrorResult
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

data class KeySet(
    var sk: PrivateKey,
    var pk: PublicKey,
    var hpk: ByteArray,
    var hpkp: String,
)

internal class PeerIdTest {
    @Test
    fun gen1MatchesPublicKey() {
        checkKeys(gen1)
    }

    @Test
    fun gen2MatchesPublicKey() {
        checkKeys(gen2)
    }

    @Test
    fun manMatchesPublicKey() {
        checkKeys(man)
    }

    @Test
    fun throwsWhenDecodeNonPeerCid() {
        val exampleCid = "bafkreifoybygix7fh3r3g5rqle3wcnhqldgdg4shzf4k3ulyw3gn7mabt4"
        assertErrorResult("can't convert CID of type raw to a PeerId") { decode(exampleCid) }
        val cid = Cid.fromString(exampleCid).expectNoErrors()
        assertErrorResult("can't convert CID of type raw to a PeerId") { fromCid(cid) }
    }

    private fun checkKeys(ks: KeySet) {
        val p1 = fromString(ks.hpkp).expectNoErrors()
        assertArrayEquals(ks.hpk, p1.idBytes())
        assertTrue(p1.matchesPublicKey(ks.pk))
        assertTrue(p1.matchesPrivateKey(ks.sk))
        val p2 = fromPublicKey(ks.pk).expectNoErrors()
        assertEquals(p1, p2)
        assertEquals(ks.hpkp, p2.toString())
        val p3 = fromPrivateKey(ks.sk).expectNoErrors()
        assertEquals(p1, p3)
        assertEquals(ks.hpkp, p3.toString())
        val cid = p1.toCid().expectNoErrors()
        val p4 = fromCid(cid).expectNoErrors()
        assertEquals(p1, p4)
        val p5 = decode(cid.toString()).expectNoErrors()
        assertEquals(p1, p5)
        assertEquals(p1.encode(), ks.hpkp)
    }

    companion object {
        private const val hpkpMan = "QmcJeseojbPW9hSejUM1sQ1a2QmbrryPK4Z8pWbRUPaYEn"
        private const val skManBytes = (
            "CAASqAkwggSkAgEAAoIBAQC3hjPtPli71gFNzGJ6rUhYdb65BDwW7IrniEaZKi6z" +
                "tW4Iz0MouEJY8GPG1iQfqZKp5w9H2ENh4I1bk2dsezrJ7Nneg4Eqd78CmeHTAgaP" +
                "3PKsxohdMo/TOFNxwl8SkEF8FyVbio2TCoijYNHUuprZuq7MPEAJYr3Z1eEkM/xR" +
                "pMp3YI9S2SYsZQxbmmQ0/GfHOEvYajdow1qttreVTQkvmCppKtNLEU5InpX/W5fe" +
                "aQCj0pd7l74daZgM2WWz3juEUCVG7tdRUPg7ix1TYosbN96CKC3q2MJxe/wJ9gR5" +
                "Jvjnaaaoon+mci5vrKzxdKBDmZ/ZbLiHDfVljMkbdOQLAgMBAAECggEAEULaF3JJ" +
                "vkD+lmamzIsHxuosKhKv5CgTWHuEyFsjUVu7IbD8zBOoidzyRX1WoHO+i6Rj14oL" +
                "rGUGZpqSm61rdhqE01zjBS+GE6SNjN8f5uANIxr5MGrVBDTEBGsXrhNLVXSH2vhJ" +
                "II9ZEqTEl5GFhvz7+9Ge5EMZQCfRqSoKjVMdrs+Rueuusr9p0wNg9PH1myA+cXGt" +
                "iNZA17Rj2IiWVZLDgYNo4DVQUt4mFb+wTJW4NSspGKaFebpn0hf4z21laoGoJqTC" +
                "cNETJw+QwQ0uDaRoYotTLT2/55e8XBFTdcTg5cmbZoKgMyGqZEHfRyD9reVDAZlM" +
                "EZwKtrm41kz94QKBgQDmPp5zVtFXQNONmje1NE0IjCaUKcqURXk4ZiILztfT9XLC" +
                "OXAUCs3TCq21jirCkZZ6gLfo12Wx0xJYmsKlaUOGNTa8FI5Xa7OyheYKixUvV6FW" +
                "J95P/sNuWscTjh7oZHgZk/L3yKrNzNBz7awComwV6qciXW7EP1uACHf5fS/RdQKB" +
                "gQDMDa38W9OeegRDrhCeYGsniJK7btOCzhNooruQKPPXxk+O4dyJm7VBbC/3Ch55" +
                "a83W66T4k0Q7ysLVRT5Vqd5z3AM0sEM3ZoxUKCinG3NwPxVeXcoLasyEiq1vOFK6" +
                "GqZKCMThCj7ZpbkWy0DPJagnYfZGC62lammuj+XQx7mvfwKBgQCTKhka/bXmgD/3" +
                "9UeAIcLPIM2TzDZ4mQNHIjjGtVnMV8kXDaFung06xEuNjSYVoPq+qEFkqTCN/axv" +
                "R9P76BFJ2f93LehhRizggacsvAM5dFhh+i+lj+AYTBuMiz2EKpt9NcyJxhAuZKgk" +
                "QRi9wlU1mPtlArVG6HwylLcil3qV9QKBgQDJHtaU/KEY+2TGnIMuxxP2lEsjyLla" +
                "nOlOYc8C6Qpma8UwrHelfj5p7Eteb6/Xt6Tbp8kjZGuFj3T3plcpMdPbWEgkn3Kw" +
                "4TeBH0/qXUkrolHagBDLrglEvjbxf48ydV/fasM6l9GYzhofWFhZk+EoaArHwWz2" +
                "tGrTrmsynBjt2wKBgErdYe+zZ2Wo+wXQGAoZi4pfcwiw4a97Kdh0dx+WZz7acHms" +
                "h+V20VRmEHm5h8WnJ/Wv5uK94t6NY17wzjQ7y2BN5mY5cA2cZAcpeqtv/N06tH4S" +
                "cn1UEuRB8VpwkjaPUNZhqtYK40qff2OTdJy8taFtQiN7fz9euWTC78zjph2s"
            )
        var gen1 = generate()
        var gen2 = generate()
        var man = load(hpkpMan, skManBytes.replace("\n".toRegex(), ""))

        private fun generate(): KeySet {
            val (privateKey, publicKey) = generateKeyPair(KeyType.RSA, 2048).expectNoErrors()
            val bpk = publicKey.bytes().expectNoErrors()
            val hash = hash(bpk).bytes()
            val hpkp = Base58.encodeToStringBtc(hash)
            return KeySet(privateKey, publicKey, hash, hpkp)
        }

        private fun load(ghpkp: String, skBytesStr: String): KeySet {
            val skBytes = Base64.decodeStringStd(skBytesStr).expectNoErrors()
            val sk = unmarshalPrivateKey(skBytes).expectNoErrors()
            val pk = sk.publicKey
            val bpk = pk.bytes().expectNoErrors()
            val hash = hash(bpk).bytes()
            val hpkp = Base58.encodeToStringBtc(hash)
            assertEquals(ghpkp, hpkp, "hpkp doesn't match key. $ghpkp")
            return KeySet(sk, pk, hash, hpkp)
        }

        private fun hash(b: ByteArray): Multihash {
            return Multihash.sum(Multicodec.SHA2_256, b).expectNoErrors()
        }
    }
}
