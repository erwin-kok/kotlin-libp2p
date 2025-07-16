// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.secp256k1

import org.erwinkok.libp2p.crypto.secp256k1.Secp256k1PublicKey.Companion.PubKeyBytesLenCompressed
import org.erwinkok.libp2p.crypto.secp256k1.Secp256k1PublicKey.Companion.PubKeyFormatCompressedEven
import org.erwinkok.libp2p.crypto.secp256k1.Secp256k1PublicKey.Companion.PubKeyFormatCompressedOdd
import org.erwinkok.libp2p.crypto.secp256k1.Secp256k1PublicKey.Companion.PubKeyFormatHybridEven
import org.erwinkok.libp2p.crypto.secp256k1.Secp256k1PublicKey.Companion.PubKeyFormatUncompressed
import org.erwinkok.result.assertErrorResult
import org.erwinkok.result.expectNoErrors
import org.erwinkok.util.Hex
import org.erwinkok.util.Tuple4
import org.erwinkok.util.Tuple5
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.stream.Stream

class Secp256k1PublicKeyTest {
    @TestFactory
    fun parsePublicKeys(): Stream<DynamicTest> {
        return listOf(
            Tuple5(
                "uncompressed ok",
                "04" +
                    "11db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5c" +
                    "b2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3",
                null,
                "11db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5c",
                "b2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3",
            ),
            Tuple5(
                "uncompressed x changed (not on curve)",
                "04" +
                    "15db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5c" +
                    "b2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3",
                "invalid public key: [15db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5c,b2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3] not on secp256k1 curve",
                "",
                "",
            ),
            Tuple5(
                "uncompressed y changed (not on curve)",
                "04" +
                    "11db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5c" +
                    "b2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a4",
                "invalid public key: [11db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5c,b2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a4] not on secp256k1 curve",
                "",
                "",
            ),
            Tuple5(
                "uncompressed claims compressed",
                "03" +
                    "11db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5c" +
                    "b2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3",
                "invalid public key: unsupported format: 3",
                "",
                "",
            ),
            Tuple5(
                "uncompressed as hybrid ok (ybit = 0)",
                "06" +
                    "11db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5c" +
                    "4d1f1522047b33068bbb9b07d1e9f40564749b062b3fc0666479bc08a94be98c",
                null,
                "11db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5c",
                "4d1f1522047b33068bbb9b07d1e9f40564749b062b3fc0666479bc08a94be98c",
            ),
            Tuple5(
                "uncompressed as hybrid ok (ybit = 1)",
                "07" +
                    "11db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5c" +
                    "b2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3",
                null,
                "11db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5c",
                "b2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3",
            ),
            Tuple5(
                "uncompressed as hybrid wrong oddness",
                "06" +
                    "11db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5c" +
                    "b2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3",
                "invalid public key: y oddness does not match specified value of false",
                "",
                "",
            ),
            Tuple5(
                "compressed ok (ybit = 0)",
                "02" +
                    "ce0b14fb842b1ba549fdd675c98075f12e9c510f8ef52bd021a9a1f4809d3b4d",
                null,
                "ce0b14fb842b1ba549fdd675c98075f12e9c510f8ef52bd021a9a1f4809d3b4d",
                "0890ff84d7999d878a57bee170e19ef4b4803b4bdede64503a6ac352b03c8032",
            ),
            Tuple5(
                "compressed ok (ybit = 1)",
                "03" +
                    "2689c7c2dab13309fb143e0e8fe396342521887e976690b6b47f5b2a4b7d448e",
                null,
                "2689c7c2dab13309fb143e0e8fe396342521887e976690b6b47f5b2a4b7d448e",
                "499dd7852849a38aa23ed9f306f07794063fe7904e0f347bc209fdddaf37691f",
            ),
            Tuple5(
                "compressed claims uncompressed (ybit = 0)",
                "04" +
                    "ce0b14fb842b1ba549fdd675c98075f12e9c510f8ef52bd021a9a1f4809d3b4d",
                "invalid public key: unsupported format: 4",
                "",
                "",
            ),
            Tuple5(
                "compressed claims uncompressed (ybit = 1)",
                "04" +
                    "2689c7c2dab13309fb143e0e8fe396342521887e976690b6b47f5b2a4b7d448e",
                "invalid public key: unsupported format: 4",
                "",
                "",
            ),
            Tuple5(
                "compressed claims hybrid (ybit = 0)",
                "06" +
                    "ce0b14fb842b1ba549fdd675c98075f12e9c510f8ef52bd021a9a1f4809d3b4d",
                "invalid public key: unsupported format: 6",
                "",
                "",
            ),
            Tuple5(
                "compressed claims hybrid (ybit = 1)",
                "07" +
                    "2689c7c2dab13309fb143e0e8fe396342521887e976690b6b47f5b2a4b7d448e",
                "invalid public key: unsupported format: 7",
                "",
                "",
            ),
            Tuple5(
                "compressed with invalid x coord (ybit = 0)",
                "03" +
                    "ce0b14fb842b1ba549fdd675c98075f12e9c510f8ef52bd021a9a1f4809d3b4c",
                "invalid public key: x coordinate ce0b14fb842b1ba549fdd675c98075f12e9c510f8ef52bd021a9a1f4809d3b4c is not on the secp256k1 curve",
                "",
                "",
            ),
            Tuple5(
                "compressed with invalid x coord (ybit = 1)",
                "03" +
                    "2689c7c2dab13309fb143e0e8fe396342521887e976690b6b47f5b2a4b7d448d",
                "invalid public key: x coordinate 2689c7c2dab13309fb143e0e8fe396342521887e976690b6b47f5b2a4b7d448d is not on the secp256k1 curve",
                "",
                "",
            ),
            Tuple5(
                "empty",
                "",
                "malformed public key: invalid length: 0",
                "",
                "",
            ),
            Tuple5(
                "wrong length",
                "05",
                "malformed public key: invalid length: 1",
                "",
                "",
            ),
            Tuple5(
                "uncompressed x == p",
                "04" +
                    "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f" +
                    "b2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3",
                "invalid public key: x >= field prime",
                "",
                "",
            ),
            Tuple5(
                // The y coordinate produces a valid point for x == 1 (mod p), but it
                // should fail to parse instead of wrapping around.
                "uncompressed x > p (p + 1 -- aka 1)",
                "04" +
                    "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc30" +
                    "bde70df51939b94c9c24979fa7dd04ebd9b3572da7802290438af2a681895441",
                "invalid public key: x >= field prime",
                "",
                "",
            ),
            Tuple5(
                "uncompressed y == p",
                "04" +
                    "11db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5c" +
                    "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f",
                "invalid public key: y >= field prime",
                "",
                "",
            ),
            Tuple5(
                // The x coordinate produces a valid point for y == 1 (mod p), but it
                // should fail to parse instead of wrapping around.
                "uncompressed y > p (p + 1 -- aka 1)",
                "04" +
                    "1fe1e5ef3fceb5c135ab7741333ce5a6e80d68167653f6b2b24bcbcfaaaff507" +
                    "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc30",
                "invalid public key: y >= field prime",
                "",
                "",
            ),
            Tuple5(
                "compressed x == p (ybit = 0)",
                "02" +
                    "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f",
                "invalid public key: x >= field prime",
                "",
                "",
            ),
            Tuple5(
                "compressed x == p (ybit = 1)",
                "03" +
                    "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f",
                "invalid public key: x >= field prime",
                "",
                "",
            ),
            Tuple5(
                // This would be valid for x == 2 (mod p), but it should fail to parse
                // instead of wrapping around.
                "compressed x > p (p + 2 -- aka 2) (ybit = 0)",
                "02" +
                    "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc31",
                "invalid public key: x >= field prime",
                "",
                "",
            ),
            Tuple5(
                // This would be valid for x == 1 (mod p), but it should fail to parse
                // instead of wrapping around.
                "compressed x > p (p + 1 -- aka 1) (ybit = 1)",
                "03" +
                    "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc30",
                "invalid public key: x >= field prime",
                "",
                "",
            ),
            Tuple5(
                "hybrid x == p (ybit = 1)",
                "07" +
                    "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f" +
                    "b2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3",
                "invalid public key: x >= field prime",
                "",
                "",
            ),
            Tuple5(
                // The y coordinate produces a valid point for x == 1 (mod p), but it
                // should fail to parse instead of wrapping around.
                "hybrid x > p (p + 1 -- aka 1) (ybit = 0)",
                "06" +
                    "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc30" +
                    "bde70df51939b94c9c24979fa7dd04ebd9b3572da7802290438af2a681895441",
                "invalid public key: x >= field prime",
                "",
                "",
            ),
            Tuple5(
                "hybrid y == p (ybit = 0 when mod p)",
                "06" +
                    "11db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5c" +
                    "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f",
                "invalid public key: y >= field prime",
                "",
                "",
            ),
            Tuple5(
                // The x coordinate produces a valid point for y == 1 (mod p), but it
                // should fail to parse instead of wrapping around.
                "hybrid y > p (p + 1 -- aka 1) (ybit = 1 when mod p)",
                "07" +
                    "1fe1e5ef3fceb5c135ab7741333ce5a6e80d68167653f6b2b24bcbcfaaaff507" +
                    "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc30",
                "invalid public key: y >= field prime",
                "",
                "",
            ),
        ).map { (name: String, key: String, error: String?, wantX: String, wantY: String) ->
            DynamicTest.dynamicTest("Test: $name") {
                val pubKeyBytes = Hex.decodeOrThrow(key)
                if (error != null) {
                    assertErrorResult(error) { Secp256k1PublicKey.parsePubKey(pubKeyBytes) }
                } else {
                    assertDoesNotThrow {
                        val pubKey = Secp256k1PublicKey.parsePubKey(pubKeyBytes).expectNoErrors()
                        assertEquals(FieldVal.fromHex(wantX), pubKey.key.x)
                        assertEquals(FieldVal.fromHex(wantY), pubKey.key.y)
                    }
                }
            }
        }.stream()
    }

    @TestFactory
    fun publicKeySerialize(): Stream<DynamicTest> {
        return listOf(
            Tuple5(
                "uncompressed (ybit = 0)",
                "11db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5c",
                "4d1f1522047b33068bbb9b07d1e9f40564749b062b3fc0666479bc08a94be98c",
                false,
                "04" +
                    "11db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5c" +
                    "4d1f1522047b33068bbb9b07d1e9f40564749b062b3fc0666479bc08a94be98c",
            ),
            Tuple5(
                "uncompressed (ybit = 1)",
                "11db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5c",
                "b2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3",
                false,
                "04" +
                    "11db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5c" +
                    "b2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3",
            ),
            Tuple5(
                // It's invalid to parse pubkeys that are not on the curve, however it
                // is possible to manually create them and they should serialize
                // correctly.
                "uncompressed not on the curve due to x coord",
                "15db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5c",
                "b2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3",
                false,
                "04" +
                    "15db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5c" +
                    "b2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3",
            ),
            Tuple5(
                // It's invalid to parse pubkeys that are not on the curve, however it
                // is possible to manually create them and they should serialize
                // correctly.
                "uncompressed not on the curve due to y coord",
                "15db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5c",
                "b2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a4",
                false,
                "04" +
                    "15db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5c" +
                    "b2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a4",
            ),
            Tuple5(
                "compressed (ybit = 0)",
                "ce0b14fb842b1ba549fdd675c98075f12e9c510f8ef52bd021a9a1f4809d3b4d",
                "0890ff84d7999d878a57bee170e19ef4b4803b4bdede64503a6ac352b03c8032",
                true,
                "02" +
                    "ce0b14fb842b1ba549fdd675c98075f12e9c510f8ef52bd021a9a1f4809d3b4d",
            ),
            Tuple5(
                "compressed (ybit = 1)",
                "2689c7c2dab13309fb143e0e8fe396342521887e976690b6b47f5b2a4b7d448e",
                "499dd7852849a38aa23ed9f306f07794063fe7904e0f347bc209fdddaf37691f",
                true,
                "03" +
                    "2689c7c2dab13309fb143e0e8fe396342521887e976690b6b47f5b2a4b7d448e",
            ),
            Tuple5(
                // It's invalid to parse pubkeys that are not on the curve, however it
                // is possible to manually create them and they should serialize
                // correctly.
                "compressed not on curve (ybit = 0)",
                "ce0b14fb842b1ba549fdd675c98075f12e9c510f8ef52bd021a9a1f4809d3b4c",
                "0890ff84d7999d878a57bee170e19ef4b4803b4bdede64503a6ac352b03c8032",
                true,
                "02" +
                    "ce0b14fb842b1ba549fdd675c98075f12e9c510f8ef52bd021a9a1f4809d3b4c",
            ),
            Tuple5(
                // It's invalid to parse pubkeys that are not on the curve, however it
                // is possible to manually create them and they should serialize
                // correctly.
                "compressed not on curve (ybit = 1)",
                "2689c7c2dab13309fb143e0e8fe396342521887e976690b6b47f5b2a4b7d448d",
                "499dd7852849a38aa23ed9f306f07794063fe7904e0f347bc209fdddaf37691f",
                true,
                "03" +
                    "2689c7c2dab13309fb143e0e8fe396342521887e976690b6b47f5b2a4b7d448d",
            ),
        ).map { (name: String, pubX: String, pubY: String, compress: Boolean, expected: String) ->
            DynamicTest.dynamicTest("Test: $name") {
                val pubKey = Secp256k1PublicKey(Jacobian2dPoint(FieldVal.fromHex(pubX), FieldVal.fromHex(pubY)))
                val serialize = if (compress) {
                    pubKey.serializeCompressed()
                } else {
                    pubKey.serializeUncompressed()
                }
                assertArrayEquals(Hex.decodeOrThrow(expected), serialize)
            }
        }.stream()
    }

    @Test
    fun isEqual() {
        val pubKey1 = Secp256k1PublicKey(Jacobian2dPoint(FieldVal.fromHex("2689c7c2dab13309fb143e0e8fe396342521887e976690b6b47f5b2a4b7d448e"), FieldVal.fromHex("499dd7852849a38aa23ed9f306f07794063fe7904e0f347bc209fdddaf37691f")))
        val pubKey1Copy = Secp256k1PublicKey(Jacobian2dPoint(FieldVal.fromHex("2689c7c2dab13309fb143e0e8fe396342521887e976690b6b47f5b2a4b7d448e"), FieldVal.fromHex("499dd7852849a38aa23ed9f306f07794063fe7904e0f347bc209fdddaf37691f")))
        val pubKey2 = Secp256k1PublicKey(Jacobian2dPoint(FieldVal.fromHex("ce0b14fb842b1ba549fdd675c98075f12e9c510f8ef52bd021a9a1f4809d3b4d"), FieldVal.fromHex("ce0b14fb842b1ba549fdd675c98075f12e9c510f8ef52bd021a9a1f4809d3b4d")))
        assertEquals(pubKey1, pubKey1)
        assertEquals(pubKey1, pubKey1Copy)
        assertNotEquals(pubKey1, pubKey2)
    }

    @TestFactory
    fun publicKeyAsJacobian(): Stream<DynamicTest> {
        return listOf(
            Tuple4(
                "public key for private key 0x01",
                "0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                "483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8",
            ),
            Tuple4(
                "public for private key 0x03",
                "02f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9",
                "f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9",
                "388f7b0f632de8140fe337e62a37f3566500a99934c2231b6cb9fd7584b8e672",
            ),
            Tuple4(
                "public for private key 0x06",
                "03fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a1460297556",
                "fff97bd5755eeea420453a14355235d382f6472f8568a18b2f057a1460297556",
                "ae12777aacfbb620f3be96017f45c560de80f0f6518fe4a03c870c36b075f297",
            ),
        ).map { (name: String, t_pubKey: String, t_wantX: String, t_wantY: String) ->
            DynamicTest.dynamicTest("Test: $name") {
                val pubKeyBytes = Hex.decodeOrThrow(t_pubKey)
                val wantX = FieldVal.fromHex(t_wantX)
                val wantY = FieldVal.fromHex(t_wantY)
                val pubKey = Secp256k1PublicKey.parsePubKey(pubKeyBytes).expectNoErrors()
                val point = pubKey.asJacobian()
                assertTrue(point.z.isOne)
                assertEquals(wantX, point.x)
                assertEquals(wantY, point.y)
            }
        }.stream()
    }

    @TestFactory
    fun publicKeyIsOnCurve(): Stream<DynamicTest> {
        return listOf(
            Tuple4(
                "valid with even y",
                "11db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5c",
                "4d1f1522047b33068bbb9b07d1e9f40564749b062b3fc0666479bc08a94be98c",
                true,
            ),
            Tuple4(
                "valid with odd y",
                "11db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5c",
                "b2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3",
                true,
            ),
            Tuple4(
                "invalid due to x coord",
                "15db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5c",
                "b2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3",
                false,
            ),
            Tuple4(
                "invalid due to y coord",
                "15db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5c",
                "b2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a4",
                false,
            ),
        ).map { (name: String, pubX: String, pubY: String, want: Boolean) ->
            DynamicTest.dynamicTest("Test: $name") {
                val publicKey = Secp256k1PublicKey(Jacobian2dPoint(FieldVal.fromHex(pubX), FieldVal.fromHex(pubY)))
                assertEquals(want, publicKey.isOnCurve())
            }
        }.stream()
    }

    @TestFactory
    fun publicKeys(): Stream<DynamicTest> {
        return listOf(
            Tuple4(
                "uncompressed ok",
                "0411db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5cb2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3",
                PubKeyFormatUncompressed,
                null,
            ),
            Tuple4(
                "uncompressed x changed",
                "0415db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5cb2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3",
                null,
                "invalid public key: [15db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5c,b2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3] not on secp256k1 curve",
            ),
            Tuple4(
                "uncompressed y changed",
                "0411db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5cb2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a4",
                null,
                "invalid public key: [11db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5c,b2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a4] not on secp256k1 curve",
            ),
            Tuple4(
                "uncompressed claims compressed",
                "0311db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5cb2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3",
                null,
                "invalid public key: unsupported format: 3",
            ),
            Tuple4(
                "uncompressed as hybrid ok",
                "0411db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5cb2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3",
                PubKeyFormatHybridEven,
                null,
            ),
            Tuple4(
                "uncompressed as hybrid wrong",
                "0611db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5cb2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3",
                null,
                "invalid public key: y oddness does not match specified value of false",
            ),
            // from tx 0b09c51c51ff762f00fb26217269d2a18e77a4fa87d69b3c363ab4df16543f20
            Tuple4(
                "compressed ok (ybit = 0)",
                "02ce0b14fb842b1ba549fdd675c98075f12e9c510f8ef52bd021a9a1f4809d3b4d",
                PubKeyFormatCompressedEven,
                null,
            ),
            // from tx fdeb8e72524e8dab0da507ddbaf5f88fe4a933eb10a66bc4745bb0aa11ea393c
            Tuple4(
                "compressed ok (ybit = 1)",
                "032689c7c2dab13309fb143e0e8fe396342521887e976690b6b47f5b2a4b7d448e",
                PubKeyFormatCompressedEven,
                null,
            ),
            Tuple4(
                "compressed claims uncompressed (ybit = 0)",
                "04ce0b14fb842b1ba549fdd675c98075f12e9c510f8ef52bd021a9a1f4809d3b4d",
                null,
                "invalid public key: unsupported format: 4",
            ),
            Tuple4(
                "compressed claims uncompressed (ybit = 1)",
                "052689c7c2dab13309fb143e0e8fe396342521887e976690b6b47f5b2a4b7d448e",
                null,
                "invalid public key: unsupported format: 5",
            ),
            Tuple4(
                "wrong length",
                "05",
                null,
                "malformed public key: invalid length: 1",
            ),
            Tuple4(
                "X == P",
                "04fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2fb2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3",
                null,
                "invalid public key: x >= field prime",
            ),
            Tuple4(
                "X > P",
                "04fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffd2fb2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3",
                null,
                "invalid public key: x >= field prime",
            ),
            Tuple4(
                "Y == P",
                "0411db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5cfffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f",
                null,
                "invalid public key: y >= field prime",
            ),
            Tuple4(
                "Y > P",
                "0411db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5cfffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffd2f",
                null,
                "invalid public key: y >= field prime",
            ),
            Tuple4(
                "hybrid",
                "0679be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8",
                PubKeyFormatHybridEven,
                null,
            ),
        ).map { (name: String, keys: String, format: Byte?, error: String?) ->
            DynamicTest.dynamicTest("Test: $name") {
                val key = Hex.decodeOrThrow(keys)
                if (error == null) {
                    val pub = Secp256k1PublicKey.parsePubKey(key).expectNoErrors()
                    val pubB = when (format) {
                        PubKeyFormatUncompressed -> {
                            pub.serializeUncompressed()
                        }

                        PubKeyFormatCompressedEven -> {
                            pub.serializeCompressed()
                        }

                        PubKeyFormatHybridEven -> {
                            key
                        }

                        else -> byteArrayOf()
                    }
                    assertArrayEquals(key, pubB)
                    val isCompressed = isCompressedPubKey(key)
                    val wantCompressed = (format == PubKeyFormatCompressedEven)
                    assertEquals(wantCompressed, isCompressed)
                } else {
                    assertErrorResult(error) { Secp256k1PublicKey.parsePubKey(key) }
                }
            }
        }.stream()
    }

    @Test
    fun pubKeyIsEqual() {
        val pubKey1 = Secp256k1PublicKey.parsePubKey(Hex.decodeOrThrow("032689c7c2dab13309fb143e0e8fe396342521887e976690b6b47f5b2a4b7d448e"))
        val pubKey2 = Secp256k1PublicKey.parsePubKey(Hex.decodeOrThrow("02ce0b14fb842b1ba549fdd675c98075f12e9c510f8ef52bd021a9a1f4809d3b4d"))
        assertEquals(pubKey1, pubKey1)
        assertNotEquals(pubKey1, pubKey2)
    }

    // IsCompressedPubKey returns true the the passed serialized public key has
    // been encoded in compressed format, and false otherwise.
    private fun isCompressedPubKey(pubKey: ByteArray): Boolean {
        // The public key is only compressed if it is the correct length and
        // the format (first byte) is one of the compressed pubkey values.
        val format = pubKey[0]
        return pubKey.size == PubKeyBytesLenCompressed && (format == PubKeyFormatCompressedEven || format == PubKeyFormatCompressedOdd)
    }
}
