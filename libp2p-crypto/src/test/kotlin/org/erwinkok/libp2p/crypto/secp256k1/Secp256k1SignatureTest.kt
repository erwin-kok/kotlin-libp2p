// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.secp256k1

import org.erwinkok.result.assertErrorResult
import org.erwinkok.result.expectNoErrors
import org.erwinkok.util.Hex
import org.erwinkok.util.Tuple3
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertDoesNotThrow
import java.security.SecureRandom
import java.util.stream.Stream

internal class Secp256k1SignatureTest {
    @TestFactory
    fun parsing(): Stream<DynamicTest> {
        return listOf(
            Tuple3(
                // signature from Decred blockchain tx
                // 76634e947f49dfc6228c3e8a09cd3e9e15893439fc06df7df0fc6f08d659856c:0
                "valid signature 1",
                "3045022100cd496f2ab4fe124f977ffe3caa09f7576d8a34156b4e55d326b4dffc0399a094022013500a0510b5094bff220c74656879b8ca0369d3da78004004c970790862fc03",
                null
            ),
            Tuple3(
                // signature from Decred blockchain tx
                // 76634e947f49dfc6228c3e8a09cd3e9e15893439fc06df7df0fc6f08d659856c:1
                "valid signature 2",
                "3044022036334e598e51879d10bf9ce3171666bc2d1bbba6164cf46dd1d882896ba35d5d022056c39af9ea265c1b6d7eab5bc977f06f81e35cdcac16f3ec0fd218e30f2bad2a",
                null
            ),
            Tuple3(
                "valid signature.",
                "304402204e45e16932b8af514961a1d3a1a25fdf3f4f7732e9d624c6c61548ab5fb8cd410220181522ec8eca07de4860a4acdd12909d831cc56cbbac4622082221a8768d1d09",
                null
            ),
            Tuple3(
                "empty",
                "",
                "malformed signature: too short: 0 < 8"
            ),
            Tuple3(
                "too short",
                "30050201000200",
                "malformed signature: too short: 7 < 8"
            ),
            Tuple3(
                "too long",
                "3045022100f5353150d31a63f4a0d06d1f5a01ac65f7267a719e49f2a1ac584fd546bef074022030e09575e7a1541aa018876a4003cefe1b061a90556b5140c63e0ef8481352480101",
                "malformed signature: too long: 73 > 72"
            ),
            Tuple3(
                "bad ASN.1 sequence id",
                "3145022100f5353150d31a63f4a0d06d1f5a01ac65f7267a719e49f2a1ac584fd546bef074022030e09575e7a1541aa018876a4003cefe1b061a90556b5140c63e0ef848135248",
                "malformed signature: format has wrong type: 49"
            ),
            Tuple3(
                "mismatched data length (short one byte)",
                "3044022100f5353150d31a63f4a0d06d1f5a01ac65f7267a719e49f2a1ac584fd546bef074022030e09575e7a1541aa018876a4003cefe1b061a90556b5140c63e0ef848135248",
                "malformed signature: bad length: 68 != 69"
            ),
            Tuple3(
                "mismatched data length (long one byte)",
                "3046022100f5353150d31a63f4a0d06d1f5a01ac65f7267a719e49f2a1ac584fd546bef074022030e09575e7a1541aa018876a4003cefe1b061a90556b5140c63e0ef848135248",
                "malformed signature: bad length: 70 != 69"
            ),
            Tuple3(
                "bad R ASN.1 int marker",
                "304403204e45e16932b8af514961a1d3a1a25fdf3f4f7732e9d624c6c61548ab5fb8cd410220181522ec8eca07de4860a4acdd12909d831cc56cbbac4622082221a8768d1d09",
                "malformed signature: R integer marker: 3 != 2"
            ),
            Tuple3(
                "zero R length",
                "30240200022030e09575e7a1541aa018876a4003cefe1b061a90556b5140c63e0ef848135248",
                "malformed signature: R length is zero"
            ),
            Tuple3(
                "negative R (too little padding)",
                "30440220b2ec8d34d473c3aa2ab5eb7cc4a0783977e5db8c8daf777e0b6d7bfa6b6623f302207df6f09af2c40460da2c2c5778f636d3b2e27e20d10d90f5a5afb45231454700",
                "malformed signature: R is negative"
            ),
            Tuple3(
                "too much R padding",
                "304402200077f6e93de5ed43cf1dfddaa79fca4b766e1a8fc879b0333d377f62538d7eb5022054fed940d227ed06d6ef08f320976503848ed1f52d0dd6d17f80c9c160b01d86",
                "malformed signature: R value has too much padding"
            ),
            Tuple3(
                "bad S ASN.1 int marker",
                "3045022100f5353150d31a63f4a0d06d1f5a01ac65f7267a719e49f2a1ac584fd546bef074032030e09575e7a1541aa018876a4003cefe1b061a90556b5140c63e0ef848135248",
                "malformed signature: S integer marker: 3 != 2"
            ),
            Tuple3(
                "missing S ASN.1 int marker",
                "3023022100f5353150d31a63f4a0d06d1f5a01ac65f7267a719e49f2a1ac584fd546bef074",
                "malformed signature: S type indicator missing"
            ),
            Tuple3(
                "S length missing",
                "3024022100f5353150d31a63f4a0d06d1f5a01ac65f7267a719e49f2a1ac584fd546bef07402",
                "malformed signature: S length missing"
            ),
            Tuple3(
                "invalid S length (short one byte)",
                "3045022100f5353150d31a63f4a0d06d1f5a01ac65f7267a719e49f2a1ac584fd546bef074021f30e09575e7a1541aa018876a4003cefe1b061a90556b5140c63e0ef848135248",
                "malformed signature: invalid S length"
            ),
            Tuple3(
                "invalid S length (long one byte)",
                "3045022100f5353150d31a63f4a0d06d1f5a01ac65f7267a719e49f2a1ac584fd546bef074022130e09575e7a1541aa018876a4003cefe1b061a90556b5140c63e0ef848135248",
                "malformed signature: invalid S length"
            ),
            Tuple3(
                "zero S length",
                "3025022100f5353150d31a63f4a0d06d1f5a01ac65f7267a719e49f2a1ac584fd546bef0740200",
                "malformed signature: S length is zero"
            ),
            Tuple3(
                "negative S (too little padding)",
                "304402204fc10344934662ca0a93a84d14d650d8a21cf2ab91f608e8783d2999c955443202208441aacd6b17038ff3f6700b042934f9a6fea0cec2051b51dc709e52a5bb7d61",
                "malformed signature: S is negative"
            ),
            Tuple3(
                "too much S padding",
                "304402206ad2fdaf8caba0f2cb2484e61b81ced77474b4c2aa069c852df1351b3314fe20022000695ad175b09a4a41cd9433f6b2e8e83253d6a7402096ba313a7be1f086dde5",
                "malformed signature: S value has too much padding"
            ),
            Tuple3(
                "R == 0",
                "30250201000220181522ec8eca07de4860a4acdd12909d831cc56cbbac4622082221a8768d1d09",
                "invalid signature: R is 0"
            ),
            Tuple3(
                "R == N",
                "3045022100fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd03641410220181522ec8eca07de4860a4acdd12909d831cc56cbbac4622082221a8768d1d09",
                "invalid signature: R >= group order"
            ),
            Tuple3(
                "R > N (>32 bytes)",
                "3045022101cd496f2ab4fe124f977ffe3caa09f756283910fc1a96f60ee6873e88d3cfe1d50220181522ec8eca07de4860a4acdd12909d831cc56cbbac4622082221a8768d1d09",
                "invalid signature: R is larger than 256 bits"
            ),
            Tuple3(
                "R > N",
                "3045022100fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd03641420220181522ec8eca07de4860a4acdd12909d831cc56cbbac4622082221a8768d1d09",
                "invalid signature: R >= group order"
            ),
            Tuple3(
                "S == 0",
                "302502204e45e16932b8af514961a1d3a1a25fdf3f4f7732e9d624c6c61548ab5fb8cd41020100",
                "invalid signature: S is 0"
            ),
            Tuple3(
                "S == N",
                "304502204e45e16932b8af514961a1d3a1a25fdf3f4f7732e9d624c6c61548ab5fb8cd41022100fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141",
                "invalid signature: S >= group order"
            ),
            Tuple3(
                "S > N (>32 bytes)",
                "304502204e45e16932b8af514961a1d3a1a25fdf3f4f7732e9d624c6c61548ab5fb8cd4102210113500a0510b5094bff220c74656879b784b246ba89c0a07bc49bcf05d8993d44",
                "invalid signature: S is larger than 256 bits"
            ),
            Tuple3(
                "S > N",
                "304502204e45e16932b8af514961a1d3a1a25fdf3f4f7732e9d624c6c61548ab5fb8cd41022100fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364142",
                "invalid signature: S >= group order"
            ),
            Tuple3(
                "bad magic.",
                "314402204e45e16932b8af514961a1d3a1a25fdf3f4f7732e9d624c6c61548ab5fb8cd410220181522ec8eca07de4860a4acdd12909d831cc56cbbac4622082221a8768d1d09",
                "malformed signature: format has wrong type: 49"
            ),
            Tuple3(
                "bad 1st int marker magic.",
                "304403204e45e16932b8af514961a1d3a1a25fdf3f4f7732e9d624c6c61548ab5fb8cd410220181522ec8eca07de4860a4acdd12909d831cc56cbbac4622082221a8768d1d09",
                "malformed signature: R integer marker: 3 != 2"
            ),
            Tuple3(
                "bad 2nd int marker.",
                "304402204e45e16932b8af514961a1d3a1a25fdf3f4f7732e9d624c6c61548ab5fb8cd410320181522ec8eca07de4860a4acdd12909d831cc56cbbac4622082221a8768d1d09",
                "malformed signature: S integer marker: 3 != 2"
            ),
            Tuple3(
                "short len",
                "304302204e45e16932b8af514961a1d3a1a25fdf3f4f7732e9d624c6c61548ab5fb8cd410220181522ec8eca07de4860a4acdd12909d831cc56cbbac4622082221a8768d1d09",
                "malformed signature: bad length: 67 != 68"
            ),
            Tuple3(
                "long len",
                "304502204e45e16932b8af514961a1d3a1a25fdf3f4f7732e9d624c6c61548ab5fb8cd410220181522ec8eca07de4860a4acdd12909d831cc56cbbac4622082221a8768d1d09",
                "malformed signature: bad length: 69 != 68"
            ),
            Tuple3(
                "long X",
                "304402424e45e16932b8af514961a1d3a1a25fdf3f4f7732e9d624c6c61548ab5fb8cd410220181522ec8eca07de4860a4acdd12909d831cc56cbbac4622082221a8768d1d09",
                "malformed signature: S type indicator missing"
            ),
            Tuple3(
                "long Y",
                "304402204e45e16932b8af514961a1d3a1a25fdf3f4f7732e9d624c6c61548ab5fb8cd410221181522ec8eca07de4860a4acdd12909d831cc56cbbac4622082221a8768d1d09",
                "malformed signature: invalid S length"
            ),
            Tuple3(
                "short Y",
                "304402204e45e16932b8af514961a1d3a1a25fdf3f4f7732e9d624c6c61548ab5fb8cd410219181522ec8eca07de4860a4acdd12909d831cc56cbbac4622082221a8768d1d09",
                "malformed signature: invalid S length"
            ),
            Tuple3(
                "trailing crap.",
                "304402204e45e16932b8af514961a1d3a1a25fdf3f4f7732e9d624c6c61548ab5fb8cd410220181522ec8eca07de4860a4acdd12909d831cc56cbbac4622082221a8768d1d0901",
                "malformed signature: bad length: 68 != 69"
            ),
            Tuple3(
                "X == N ",
                "30440220fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd03641410220181522ec8eca07de4860a4acdd12909d831cc56cbbac4622082221a8768d1d09",
                "malformed signature: R is negative"
            ),
            Tuple3(
                "Y == N",
                "304402204e45e16932b8af514961a1d3a1a25fdf3f4f7732e9d624c6c61548ab5fb8cd410220fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141",
                "malformed signature: S is negative"
            ),
            Tuple3(
                "0 len X.",
                "302402000220181522ec8eca07de4860a4acdd12909d831cc56cbbac4622082221a8768d1d09",
                "malformed signature: R length is zero"
            ),
            Tuple3(
                "0 len Y.",
                "302402204e45e16932b8af514961a1d3a1a25fdf3f4f7732e9d624c6c61548ab5fb8cd410200",
                "malformed signature: S length is zero"
            ),
            Tuple3(
                "extra R padding.",
                "30450221004e45e16932b8af514961a1d3a1a25fdf3f4f7732e9d624c6c61548ab5fb8cd410220181522ec8eca07de4860a4acdd12909d831cc56cbbac4622082221a8768d1d09",
                "malformed signature: R value has too much padding"
            ),
            Tuple3(
                "extra S padding.",
                "304502204e45e16932b8af514961a1d3a1a25fdf3f4f7732e9d624c6c61548ab5fb8cd41022100181522ec8eca07de4860a4acdd12909d831cc56cbbac4622082221a8768d1d09",
                "malformed signature: S value has too much padding"
            )

        ).map { (name: String, sig: String, err: String?) ->
            DynamicTest.dynamicTest("Test: $name") {
                if (err == null) {
                    assertDoesNotThrow { Secp256k1Signature.parseDERSignature(Hex.decodeOrThrow(sig)) }
                } else {
                    assertErrorResult(err) { Secp256k1Signature.parseDERSignature(Hex.decodeOrThrow(sig)) }
                }
            }
        }.stream()
    }

    @TestFactory
    fun serialize(): Stream<DynamicTest> {
        return listOf(
            Tuple3(
                // signature from bitcoin blockchain tx
                // 0437cd7f8525ceed2324359c2d0ba26006d92d85
                "valid 1 - r and s most significant bits are zero",
                Secp256k1Signature(
                    ModNScalar.setByteSlice(Hex.decodeOrThrow("4e45e16932b8af514961a1d3a1a25fdf3f4f7732e9d624c6c61548ab5fb8cd41")),
                    ModNScalar.setByteSlice(Hex.decodeOrThrow("181522ec8eca07de4860a4acdd12909d831cc56cbbac4622082221a8768d1d09"))
                ),
                Hex.decodeOrThrow("304402204e45e16932b8af514961a1d3a1a25fdf3f4f7732e9d624c6c61548ab5fb8cd410220181522ec8eca07de4860a4acdd12909d831cc56cbbac4622082221a8768d1d09")
            ),
            Tuple3(
                // signature from bitcoin blockchain tx
                // cb00f8a0573b18faa8c4f467b049f5d202bf1101d9ef2633bc611be70376a4b4
                "valid 2 - r most significant bit is one",
                Secp256k1Signature(
                    ModNScalar.setByteSlice(Hex.decodeOrThrow("82235e21a2300022738dabb8e1bbd9d19cfb1e7ab8c30a23b0afbb8d178abcf3")),
                    ModNScalar.setByteSlice(Hex.decodeOrThrow("24bf68e256c534ddfaf966bf908deb944305596f7bdcc38d69acad7f9c868724"))
                ),
                Hex.decodeOrThrow("304502210082235e21a2300022738dabb8e1bbd9d19cfb1e7ab8c30a23b0afbb8d178abcf3022024bf68e256c534ddfaf966bf908deb944305596f7bdcc38d69acad7f9c868724")
            ),
            Tuple3(
                // signature from bitcoin blockchain tx
                // fda204502a3345e08afd6af27377c052e77f1fefeaeb31bdd45f1e1237ca5470
                //
                // Note that signatures with an S component that is > half the group
                // order are neither allowed nor produced in Decred, so this has been
                // modified to expect the equally valid low S signature variant.
                "valid 3 - s most significant bit is one",
                Secp256k1Signature(
                    ModNScalar.setByteSlice(Hex.decodeOrThrow("1cadddc2838598fee7dc35a12b340c6bde8b389f7bfd19a1252a17c4b5ed2d71")),
                    ModNScalar.setByteSlice(Hex.decodeOrThrow("c1a251bbecb14b058a8bd77f65de87e51c47e95904f4c0e9d52eddc21c1415ac"))
                ),
                Hex.decodeOrThrow("304402201cadddc2838598fee7dc35a12b340c6bde8b389f7bfd19a1252a17c4b5ed2d7102203e5dae44134eb4fa757428809a2178199e66f38daa53df51eaa380cab4222b95")
            ),
            Tuple3(
                "valid 4 - s is bigger than half order",
                Secp256k1Signature(
                    ModNScalar.setByteSlice(Hex.decodeOrThrow("a196ed0e7ebcbe7b63fe1d8eecbdbde03a67ceba4fc8f6482bdcb9606a911404")),
                    ModNScalar.setByteSlice(Hex.decodeOrThrow("971729c7fa944b465b35250c6570a2f31acbb14b13d1565fab7330dcb2b3dfb1"))
                ),
                Hex.decodeOrThrow("3045022100a196ed0e7ebcbe7b63fe1d8eecbdbde03a67ceba4fc8f6482bdcb9606a911404022068e8d638056bb4b9a4cadaf39a8f5d0b9fe32b9b9b7749dc145f2db01d826190")
            ),
            Tuple3(
                "zero signature",
                Secp256k1Signature(
                    ModNScalar.Zero,
                    ModNScalar.Zero
                ),
                Hex.decodeOrThrow("3006020100020100")
            )
        ).map { (name: String, sig: Secp256k1Signature, expected: ByteArray) ->
            DynamicTest.dynamicTest("Test: $name") {
                val actual = sig.serialize()
                assertArrayEquals(expected, actual)
            }
        }.stream()
    }

    @Test
    fun signCompact() {
        val secureRandom = SecureRandom()
        for (i in 0..1023) {
            val hashed = ByteArray(32)
            secureRandom.nextBytes(hashed)
            val isCompressed = (i % 2) != 0
            val privKey = Secp256k1PrivateKey.generatePrivateKey()
            val signingPubKey = privKey.secp256k1PublicKey
            val sig = Secp256k1Signature.signCompact(privKey, hashed, isCompressed)
            val (pk, wasCompressed) = Secp256k1Signature.recoverCompact(sig, hashed).expectNoErrors()
            assertEquals(signingPubKey, pk)
            assertEquals(isCompressed, wasCompressed)
            // If we change the compressed bit we should get the same key back,
            // but the compressed flag should be reversed.
            if (isCompressed) {
                sig[0] = (sig[0] - 4).toByte()
            } else {
                sig[0] = (sig[0] + 4).toByte()
            }
            val (pk2, wasCompressed2) = Secp256k1Signature.recoverCompact(sig, hashed).expectNoErrors()
            assertEquals(signingPubKey, pk2)
            assertNotEquals(isCompressed, wasCompressed2)
        }
    }

    @Test
    fun isEqual() {
        val sig1 = Secp256k1Signature(
            ModNScalar.setBytes(Hex.decodeOrThrow("82235e21a2300022738dabb8e1bbd9d19cfb1e7ab8c30a23b0afbb8d178abcf3")),
            ModNScalar.setBytes(Hex.decodeOrThrow("24bf68e256c534ddfaf966bf908deb944305596f7bdcc38d69acad7f9c868724"))
        )
        val sig1Copy = Secp256k1Signature(
            ModNScalar.setBytes(Hex.decodeOrThrow("82235e21a2300022738dabb8e1bbd9d19cfb1e7ab8c30a23b0afbb8d178abcf3")),
            ModNScalar.setBytes(Hex.decodeOrThrow("24bf68e256c534ddfaf966bf908deb944305596f7bdcc38d69acad7f9c868724"))
        )
        val sig2 = Secp256k1Signature(
            ModNScalar.setBytes(Hex.decodeOrThrow("4e45e16932b8af514961a1d3a1a25fdf3f4f7732e9d624c6c61548ab5fb8cd41")),
            ModNScalar.setBytes(Hex.decodeOrThrow("181522ec8eca07de4860a4acdd12909d831cc56cbbac4622082221a8768d1d09"))
        )
        assertEquals(sig1, sig1)
        assertEquals(sig1, sig1Copy)
        assertNotEquals(sig1, sig2)
    }
}
