// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.secp256k1

import org.erwinkok.libp2p.crypto.ecdsa.CurvePoint
import org.erwinkok.libp2p.crypto.math.BigInt
import org.erwinkok.result.expectNoErrors
import org.erwinkok.util.Hex
import org.erwinkok.util.Tuple3
import org.erwinkok.util.Tuple4
import org.erwinkok.util.Tuple5
import org.erwinkok.util.Tuple6
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream

internal class KoblitzCurveTest {
    @TestFactory
    fun addAffine(): Stream<DynamicTest> {
        return listOf(
            // Addition with a point at infinity (left hand side).
            // ∞ + P = P
            Tuple6(
                "0",
                "0",
                "d74bf844b0862475103d96a611cf2d898447e288d34b360bc885cb8ce7c00575",
                "131c670d414c4546b88ac3ff664611b1c38ceb1c21d76369d7a7a0969d61d97d",
                "d74bf844b0862475103d96a611cf2d898447e288d34b360bc885cb8ce7c00575",
                "131c670d414c4546b88ac3ff664611b1c38ceb1c21d76369d7a7a0969d61d97d"
            ),
            // Addition with a point at infinity (right hand side).
            // P + ∞ = P
            Tuple6(
                "d74bf844b0862475103d96a611cf2d898447e288d34b360bc885cb8ce7c00575",
                "131c670d414c4546b88ac3ff664611b1c38ceb1c21d76369d7a7a0969d61d97d",
                "0",
                "0",
                "d74bf844b0862475103d96a611cf2d898447e288d34b360bc885cb8ce7c00575",
                "131c670d414c4546b88ac3ff664611b1c38ceb1c21d76369d7a7a0969d61d97d"
            ),

            // Addition with different x values.
            Tuple6(
                "34f9460f0e4f08393d192b3c5133a6ba099aa0ad9fd54ebccfacdfa239ff49c6",
                "0b71ea9bd730fd8923f6d25a7a91e7dd7728a960686cb5a901bb419e0f2ca232",
                "d74bf844b0862475103d96a611cf2d898447e288d34b360bc885cb8ce7c00575",
                "131c670d414c4546b88ac3ff664611b1c38ceb1c21d76369d7a7a0969d61d97d",
                "fd5b88c21d3143518d522cd2796f3d726793c88b3e05636bc829448e053fed69",
                "21cf4f6a5be5ff6380234c50424a970b1f7e718f5eb58f68198c108d642a137f"
            ),
            // Addition with same x opposite y.
            // P(x, y) + P(x, -y) = infinity
            Tuple6(
                "34f9460f0e4f08393d192b3c5133a6ba099aa0ad9fd54ebccfacdfa239ff49c6",
                "0b71ea9bd730fd8923f6d25a7a91e7dd7728a960686cb5a901bb419e0f2ca232",
                "34f9460f0e4f08393d192b3c5133a6ba099aa0ad9fd54ebccfacdfa239ff49c6",
                "f48e156428cf0276dc092da5856e182288d7569f97934a56fe44be60f0d359fd",
                "0",
                "0"
            ),
            // Addition with same point.
            // P(x, y) + P(x, y) = 2P
            Tuple6(
                "34f9460f0e4f08393d192b3c5133a6ba099aa0ad9fd54ebccfacdfa239ff49c6",
                "0b71ea9bd730fd8923f6d25a7a91e7dd7728a960686cb5a901bb419e0f2ca232",
                "34f9460f0e4f08393d192b3c5133a6ba099aa0ad9fd54ebccfacdfa239ff49c6",
                "0b71ea9bd730fd8923f6d25a7a91e7dd7728a960686cb5a901bb419e0f2ca232",
                "59477d88ae64a104dbb8d31ec4ce2d91b2fe50fa628fb6a064e22582196b365b",
                "938dc8c0f13d1e75c987cb1a220501bd614b0d3dd9eb5c639847e1240216e3b6"
            )
        ).map { (
            test_x1: String, test_y1: String, // Coordinates (in hex) of first point to add
            test_x2: String, test_y2: String, // Coordinates (in hex) of second point to add
            test_x3: String, test_y3: String
        ) -> // Coordinates (in hex) of expected point
            DynamicTest.dynamicTest("Test: $test_x1, $test_y1") {
                // Convert hex to field values.
                val x1 = BigInt.fromHex(test_x1)
                val y1 = BigInt.fromHex(test_y1)
                val x2 = BigInt.fromHex(test_x2)
                val y2 = BigInt.fromHex(test_y2)
                val x3 = BigInt.fromHex(test_x3)
                val y3 = BigInt.fromHex(test_y3)
                val cp1 = CurvePoint(x1, y1)
                assertFalse(!(x1.signum() == 0 && y1.signum() == 0) && !KoblitzCurve.secp256k1.isOnCurve(cp1))
                val cp2 = CurvePoint(x2, y2)
                assertFalse(!(x2.signum() == 0 && y2.signum() == 0) && !KoblitzCurve.secp256k1.isOnCurve(cp2))
                val cp3 = CurvePoint(x3, y3)
                assertFalse(!(x3.signum() == 0 && y3.signum() == 0) && !KoblitzCurve.secp256k1.isOnCurve(cp3))
                // Add the two points.
                val (x, y) = KoblitzCurve.secp256k1.addPoint(cp1, cp2)
                assertFalse(x.compareTo(x3) != 0 || y.compareTo(y3) != 0)
            }
        }.stream()
    }

    @TestFactory
    fun doubleAffine(): Stream<DynamicTest> {
        return listOf(
            // Doubling a point at infinity is still infinity.
            // 2*∞ = ∞ (point at infinity)
            Tuple4(
                "0",
                "0",
                "0",
                "0"
            ),
            // Random points.
            Tuple4(
                "e41387ffd8baaeeb43c2faa44e141b19790e8ac1f7ff43d480dc132230536f86",
                "1b88191d430f559896149c86cbcb703193105e3cf3213c0c3556399836a2b899",
                "88da47a089d333371bd798c548ef7caae76e737c1980b452d367b3cfe3082c19",
                "3b6f659b09a362821dfcfefdbfbc2e59b935ba081b6c249eb147b3c2100b1bc1"
            ),
            Tuple4(
                "b3589b5d984f03ef7c80aeae444f919374799edf18d375cab10489a3009cff0c",
                "c26cf343875b3630e15bccc61202815b5d8f1fd11308934a584a5babe69db36a",
                "e193860172998751e527bb12563855602a227fc1f612523394da53b746bb2fb1",
                "2bfcf13d2f5ab8bb5c611fab5ebbed3dc2f057062b39a335224c22f090c04789"
            ),
            Tuple4(
                "2b31a40fbebe3440d43ac28dba23eee71c62762c3fe3dbd88b4ab82dc6a82340",
                "9ba7deb02f5c010e217607fd49d58db78ec273371ea828b49891ce2fd74959a1",
                "2c8d5ef0d343b1a1a48aa336078eadda8481cb048d9305dc4fdf7ee5f65973a2",
                "bb4914ac729e26d3cd8f8dc8f702f3f4bb7e0e9c5ae43335f6e94c2de6c3dc95"
            ),
            Tuple4(
                "61c64b760b51981fab54716d5078ab7dffc93730b1d1823477e27c51f6904c7a",
                "ef6eb16ea1a36af69d7f66524c75a3a5e84c13be8fbc2e811e0563c5405e49bd",
                "5f0dcdd2595f5ad83318a0f9da481039e36f135005420393e72dfca985b482f4",
                "a01c849b0837065c1cb481b0932c441f49d1cab1b4b9f355c35173d93f110ae0"
            )
        ).map { (
            test_x1: String, test_y1: String, // Coordinates (in hex) of point to double
            test_x3: String, test_y3: String // Coordinates (in hex) of expected point
        ) ->
            DynamicTest.dynamicTest("Test: $test_x1, $test_y1 / $test_x3, $test_y3") {
                // Convert hex to field values.
                val x1 = BigInt.fromHex(test_x1)
                val y1 = BigInt.fromHex(test_y1)
                val x3 = BigInt.fromHex(test_x3)
                val y3 = BigInt.fromHex(test_y3)

                // Ensure the test data is using points that are actually on
                // the curve (or the point at infinity).
                assertFalse(!(x1.signum() == 0 && y1.signum() == 0) && !KoblitzCurve.secp256k1.isOnCurve(CurvePoint(x1, y1)))
                assertFalse(!(x3.signum() == 0 && y3.signum() == 0) && !KoblitzCurve.secp256k1.isOnCurve(CurvePoint(x3, y3)))
                // Double the point.
                val (x, y) = KoblitzCurve.secp256k1.doublePoint(CurvePoint(x1, y1))
                assertFalse(x.compareTo(x3) != 0 || y.compareTo(y3) != 0)
            }
        }.stream()
    }

    @Test
    fun onCurve() {
        assertTrue(KoblitzCurve.secp256k1.isOnCurve(KoblitzCurve.secp256k1.g))
    }

    @TestFactory
    fun baseMult(): Stream<DynamicTest> {
        return listOf(
            Tuple3(
                "aa5e28d6a97a2479a65527f7290311a3624d4cc0fa1578598ee3c2613bf99522",
                "34f9460f0e4f08393d192b3c5133a6ba099aa0ad9fd54ebccfacdfa239ff49c6",
                "0b71ea9bd730fd8923f6d25a7a91e7dd7728a960686cb5a901bb419e0f2ca232"
            ),
            Tuple3(
                "7e2b897b8cebc6361663ad410835639826d590f393d90a9538881735256dfae3",
                "d74bf844b0862475103d96a611cf2d898447e288d34b360bc885cb8ce7c00575",
                "131c670d414c4546b88ac3ff664611b1c38ceb1c21d76369d7a7a0969d61d97d"
            ),
            Tuple3(
                "6461e6df0fe7dfd05329f41bf771b86578143d4dd1f7866fb4ca7e97c5fa945d",
                "e8aecc370aedd953483719a116711963ce201ac3eb21d3f3257bb48668c6a72f",
                "c25caf2f0eba1ddb2f0f3f47866299ef907867b7d27e95b3873bf98397b24ee1"
            ),
            Tuple3(
                "376a3a2cdcd12581efff13ee4ad44c4044b8a0524c42422a7e1e181e4deeccec",
                "14890e61fcd4b0bd92e5b36c81372ca6fed471ef3aa60a3e415ee4fe987daba1",
                "297b858d9f752ab42d3bca67ee0eb6dcd1c2b7b0dbe23397e66adc272263f982"
            ),
            Tuple3(
                "1b22644a7be026548810c378d0b2994eefa6d2b9881803cb02ceff865287d1b9",
                "f73c65ead01c5126f28f442d087689bfa08e12763e0cec1d35b01751fd735ed3",
                "f449a8376906482a84ed01479bd18882b919c140d638307f0c0934ba12590bde"
            )
        ).map { (test_k: String, test_x: String, test_y: String) ->
            DynamicTest.dynamicTest("Test: $test_k, $test_x, $test_y") {
                val k = BigInt.fromHex(test_k)
                val (x, y) = KoblitzCurve.secp256k1.scalarBaseMult(BigInt.toBytes(k))
                assertEquals(test_x, Hex.encode(BigInt.toBytes(x)))
                assertEquals(test_y, Hex.encode(BigInt.toBytes(y)))
            }
        }.stream()
    }

    @TestFactory
    fun scalarMult(): Stream<DynamicTest> {
        return listOf(
            // base mult, essentially.
            Tuple5(
                "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                "483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8",
                "18e14a7b6a307f426a94f8114701e7c8e774e7f9a47e2c2035db29a206321725",
                "50863ad64a87ae8a2fe83c1af1a8403cb53f53e486d8511dad8a04887e5b2352",
                "2cd470243453a299fa9e77237716103abc11a1df38855ed6f2ee187e9c582ba6"
            ),
            // From btcd issue #709.
            Tuple5(
                "000000000000000000000000000000000000000000000000000000000000002c",
                "420e7a99bba18a9d3952597510fd2b6728cfeafc21a4e73951091d4d8ddbe94e",
                "a2e8ba2e8ba2e8ba2e8ba2e8ba2e8ba219b51835b55cc30ebfe2f6599bc56f58",
                "a2112dcdfbcd10ae1133a358de7b82db68e0a3eb4b492cc8268d1e7118c98788",
                "27fc7463b7bb3c5f98ecf2c84a6272bb1681ed553d92c69f2dfe25a9f9fd3836"
            )
        ).map { (test_x: String, test_y: String, test_k: String, test_rx: String, test_ry: String) ->
            DynamicTest.dynamicTest("Test: $test_k, $test_x, $test_y") {
                val x = BigInt.fromHex(test_x)
                val y = BigInt.fromHex(test_y)
                val k = BigInt.fromHex(test_k)
                val xWant = BigInt.fromHex(test_rx)
                val yWant = BigInt.fromHex(test_ry)
                val (x1, y1) = KoblitzCurve.secp256k1.scalarMult(CurvePoint(x, y), BigInt.toBytes(k))
                assertFalse(x1.compareTo(xWant) != 0 || y1.compareTo(yWant) != 0)
            }
        }.stream()
    }

    @Test
    fun keyGeneration() {
        val priv = Secp256k1.generatePrivateKey()
        val pub = priv.secp256k1PublicKey
        assertTrue(Secp256k1Curve.isOnCurve(pub.key.x, pub.key.y))
    }

    @Test
    fun signAndVerify() {
        val priv = Secp256k1.generatePrivateKey()
        val pub = priv.secp256k1PublicKey
        val hashed = "testing".toByteArray()
        val sig = priv.sign(hashed).expectNoErrors()
        assertTrue(pub.verify(hashed, sig).expectNoErrors())
        hashed[0] = (hashed[0].toInt() xor 0xff).toByte()
        assertFalse(pub.verify(hashed, sig).expectNoErrors())
    }
}
