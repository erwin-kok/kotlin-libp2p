// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.secp256k1

import org.erwinkok.util.Hex
import org.erwinkok.util.Tuple3
import org.erwinkok.util.Tuple7
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.security.MessageDigest
import java.util.stream.Stream

internal class NonceTest {
    @TestFactory
    fun nonceRFC6979(): Stream<DynamicTest> {
        return listOf(
            Tuple7(
                "key 32 bytes, hash 32 bytes, no extra data, no version",
                "0011111111111111111111111111111111111111111111111111111111111111",
                "0000000000000000000000000000000000000000000000000000000000000001",
                "",
                "",
                0,
                "154e92760f77ad9af6b547edd6f14ad0fae023eb2221bc8be2911675d8a686a3",
            ),
            Tuple7(
                // Should be same as key with 32 bytes due to zero padding.
                "key <32 bytes, hash 32 bytes, no extra data, no version",
                "11111111111111111111111111111111111111111111111111111111111111",
                "0000000000000000000000000000000000000000000000000000000000000001",
                "",
                "",
                0,
                "154e92760f77ad9af6b547edd6f14ad0fae023eb2221bc8be2911675d8a686a3",
            ),
            Tuple7(
                // Should be same as key with 32 bytes due to truncation.
                "key >32 bytes, hash 32 bytes, no extra data, no version",
                "001111111111111111111111111111111111111111111111111111111111111111",
                "0000000000000000000000000000000000000000000000000000000000000001",
                "",
                "",
                0,
                "154e92760f77ad9af6b547edd6f14ad0fae023eb2221bc8be2911675d8a686a3",
            ),
            Tuple7(
                "hash <32 bytes (padded), no extra data, no version",
                "0011111111111111111111111111111111111111111111111111111111111111",
                "00000000000000000000000000000000000000000000000000000000000001",
                "",
                "",
                0,
                "154e92760f77ad9af6b547edd6f14ad0fae023eb2221bc8be2911675d8a686a3",
            ),
            Tuple7(
                "hash >32 bytes (truncated), no extra data, no version",
                "0011111111111111111111111111111111111111111111111111111111111111",
                "000000000000000000000000000000000000000000000000000000000000000100",
                "",
                "",
                0,
                "154e92760f77ad9af6b547edd6f14ad0fae023eb2221bc8be2911675d8a686a3",
            ),
            Tuple7(
                "hash 32 bytes, extra data <32 bytes (ignored), no version",
                "0011111111111111111111111111111111111111111111111111111111111111",
                "0000000000000000000000000000000000000000000000000000000000000001",
                "00000000000000000000000000000000000000000000000000000000000002",
                "",
                0,
                "154e92760f77ad9af6b547edd6f14ad0fae023eb2221bc8be2911675d8a686a3",
            ),
            Tuple7(
                "hash 32 bytes, extra data >32 bytes (ignored), no version",
                "0011111111111111111111111111111111111111111111111111111111111111",
                "0000000000000000000000000000000000000000000000000000000000000001",
                "000000000000000000000000000000000000000000000000000000000000000002",
                "",
                0,
                "154e92760f77ad9af6b547edd6f14ad0fae023eb2221bc8be2911675d8a686a3",
            ),
            Tuple7(
                "hash 32 bytes, no extra data, version <16 bytes (ignored)",
                "0011111111111111111111111111111111111111111111111111111111111111",
                "0000000000000000000000000000000000000000000000000000000000000001",
                "",
                "000000000000000000000000000003",
                0,
                "154e92760f77ad9af6b547edd6f14ad0fae023eb2221bc8be2911675d8a686a3",
            ),
            Tuple7(
                "hash 32 bytes, no extra data, version >16 bytes (ignored)",
                "001111111111111111111111111111111111111111111111111111111111111122",
                "0000000000000000000000000000000000000000000000000000000000000001",
                "",
                "0000000000000000000000000000000003",
                0,
                "154e92760f77ad9af6b547edd6f14ad0fae023eb2221bc8be2911675d8a686a3",
            ),
            Tuple7(
                "hash 32 bytes, extra data 32 bytes, no version",
                "0011111111111111111111111111111111111111111111111111111111111111",
                "0000000000000000000000000000000000000000000000000000000000000001",
                "0000000000000000000000000000000000000000000000000000000000000002",
                "",
                0,
                "67893461ade51cde61824b20bc293b585d058e6b9f40fb68453d5143f15116ae",
            ),
            Tuple7(
                "hash 32 bytes, no extra data, version 16 bytes",
                "0011111111111111111111111111111111111111111111111111111111111111",
                "0000000000000000000000000000000000000000000000000000000000000001",
                "",
                "00000000000000000000000000000003",
                0,
                "7b27d6ceff87e1ded1860ca4e271a530e48514b9d3996db0af2bb8bda189007d",
            ),
            Tuple7(
                // Should be same as no extra data + version specified due to padding.
                "hash 32 bytes, extra data 32 bytes all zero, version 16 bytes",
                "0011111111111111111111111111111111111111111111111111111111111111",
                "0000000000000000000000000000000000000000000000000000000000000001",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "00000000000000000000000000000003",
                0,
                "7b27d6ceff87e1ded1860ca4e271a530e48514b9d3996db0af2bb8bda189007d",
            ),
            Tuple7(
                "hash 32 bytes, extra data 32 bytes, version 16 bytes",
                "0011111111111111111111111111111111111111111111111111111111111111",
                "0000000000000000000000000000000000000000000000000000000000000001",
                "0000000000000000000000000000000000000000000000000000000000000002",
                "00000000000000000000000000000003",
                0,
                "9b5657643dfd4b77d99dfa505ed8a17e1b9616354fc890669b4aabece2170686",
            ),
            Tuple7(
                "hash 32 bytes, no extra data, no version, extra iteration",
                "0011111111111111111111111111111111111111111111111111111111111111",
                "0000000000000000000000000000000000000000000000000000000000000001",
                "",
                "",
                1,
                "66fca3fe494a6216e4a3f15cfbc1d969c60d9cdefda1a1c193edabd34aa8cd5e",
            ),
            Tuple7(
                "hash 32 bytes, no extra data, no version, 2 extra iterations",
                "0011111111111111111111111111111111111111111111111111111111111111",
                "0000000000000000000000000000000000000000000000000000000000000001",
                "",
                "",
                2,
                "70da248c92b5d28a52eafca1848b1a37d4cb36526c02553c9c48bb0b895fc77d",
            ),
        ).map { (name: String, t_key: String, t_hash: String, t_extraData: String, t_version: String, iterations: Int, t_expected: String) ->
            DynamicTest.dynamicTest("Test: $name") {
                val privKey = Hex.decodeOrThrow(t_key)
                val hash = Hex.decodeOrThrow(t_hash)
                val extraData = Hex.decodeOrThrow(t_extraData)
                val version = Hex.decodeOrThrow(t_version)
                val wantNonce = Hex.decodeOrThrow(t_expected)

                // Ensure deterministically generated nonce is the expected value.
                val gotNonce = Nonce.nonceRFC6979(privKey, hash, extraData, version, iterations)
                val gotNonceBytes = gotNonce.bytes()
                assertArrayEquals(wantNonce, gotNonceBytes)
            }
        }.stream()
    }

    @TestFactory
    fun rfc6979Compat(): Stream<DynamicTest> {
        return listOf(
            Tuple3(
                "cca9fbcc1b41e5a95d369eaa6ddcff73b61a4efaa279cfc6567e8daa39cbaf50",
                "sample",
                "2df40ca70e639d89528a6b670d9d48d9165fdc0febc0974056bdce192b8e16a3",
            ),
            Tuple3(
                // This signature hits the case when S is higher than halforder.
                // If S is not canonicalized (lowered by halforder), this test will fail.
                "0000000000000000000000000000000000000000000000000000000000000001",
                "Satoshi Nakamoto",
                "8f8a276c19f4149656b280621e358cce24f5f52542772691ee69063b74f15d15",
            ),
            Tuple3(
                "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140",
                "Satoshi Nakamoto",
                "33a19b60e25fb6f4435af53a3d42d493644827367e6453928554f43e49aa6f90",
            ),
            Tuple3(
                "f8b8af8ce3c7cca5e300d33939540c10d45ce001b8f252bfbc57ba0342904181",
                "Alan Turing",
                "525a82b70e67874398067543fd84c83d30c175fdc45fdeee082fe13b1d7cfdf1",
            ),
            Tuple3(
                "0000000000000000000000000000000000000000000000000000000000000001",
                "All those moments will be lost in time, like tears in rain. Time to die...",
                "38aa22d72376b4dbc472e06c3ba403ee0a394da63fc58d88686c611aba98d6b3",
            ),
            Tuple3(
                "e91671c46231f833a6406ccbea0e3e392c76c167bac1cb013f6f1013980455c2",
                "There is a computer disease that anybody who works with computers knows about. It's a very serious disease and it interferes completely with the work. The trouble with computers is that you 'play' with them!",
                "1f4b84c23a86a221d233f2521be018d9318639d5b8bbd6374a8a59232d16ad3d",
            ),
        ).map { (key: String, msg: String, nonce: String) ->
            DynamicTest.dynamicTest("Test: $msg") {
                val privKey = Hex.decodeOrThrow(key)
                val hasher = MessageDigest.getInstance("SHA-256")
                val hash = hasher.digest(msg.toByteArray())

                // Ensure deterministically generated nonce is the expected value.
                val gotNonce = Nonce.nonceRFC6979(privKey, hash, byteArrayOf(), byteArrayOf(), 0)
                val wantNonce = Hex.decodeOrThrow(nonce)
                val gotNonceBytes = gotNonce.bytes()
                assertArrayEquals(wantNonce, gotNonceBytes)
            }
        }.stream()
    }
}
