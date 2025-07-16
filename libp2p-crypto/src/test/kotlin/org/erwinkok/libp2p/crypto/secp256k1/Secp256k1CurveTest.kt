// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.secp256k1

import org.erwinkok.libp2p.crypto.ecdsa.CurvePoint
import org.erwinkok.libp2p.crypto.math.BigInt
import org.erwinkok.util.Hex
import org.erwinkok.util.Tuple
import org.erwinkok.util.Tuple2
import org.erwinkok.util.Tuple5
import org.erwinkok.util.Tuple6
import org.erwinkok.util.Tuple8
import org.erwinkok.util.Tuple9
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigInteger
import java.security.SecureRandom
import java.util.stream.Stream

internal class Secp256k1CurveTest {
    @TestFactory
    fun addJacobian(): Stream<DynamicTest> {
        return listOf(
            // Addition with a point at infinity (left hand side).
            // ∞ + P = P
            Tuple9(
                "0",
                "0",
                "0",
                "d74bf844b0862475103d96a611cf2d898447e288d34b360bc885cb8ce7c00575",
                "131c670d414c4546b88ac3ff664611b1c38ceb1c21d76369d7a7a0969d61d97d",
                "1",
                "d74bf844b0862475103d96a611cf2d898447e288d34b360bc885cb8ce7c00575",
                "131c670d414c4546b88ac3ff664611b1c38ceb1c21d76369d7a7a0969d61d97d",
                "1",
            ),
            // Addition with a point at infinity (right hand side).
            // P + ∞ = P
            Tuple9(
                "d74bf844b0862475103d96a611cf2d898447e288d34b360bc885cb8ce7c00575",
                "131c670d414c4546b88ac3ff664611b1c38ceb1c21d76369d7a7a0969d61d97d",
                "1",
                "0",
                "0",
                "0",
                "d74bf844b0862475103d96a611cf2d898447e288d34b360bc885cb8ce7c00575",
                "131c670d414c4546b88ac3ff664611b1c38ceb1c21d76369d7a7a0969d61d97d",
                "1",
            ),
            // Addition with z1=z2=1 different x values.
            Tuple9(
                "34f9460f0e4f08393d192b3c5133a6ba099aa0ad9fd54ebccfacdfa239ff49c6",
                "0b71ea9bd730fd8923f6d25a7a91e7dd7728a960686cb5a901bb419e0f2ca232",
                "1",
                "d74bf844b0862475103d96a611cf2d898447e288d34b360bc885cb8ce7c00575",
                "131c670d414c4546b88ac3ff664611b1c38ceb1c21d76369d7a7a0969d61d97d",
                "1",
                "0cfbc7da1e569b334460788faae0286e68b3af7379d5504efc25e4dba16e46a6",
                "e205f79361bbe0346b037b4010985dbf4f9e1e955e7d0d14aca876bfa79aad87",
                "44a5646b446e3877a648d6d381370d9ef55a83b666ebce9df1b1d7d65b817b2f",
            ),
            // Addition with z1=z2=1 same x opposite y.
            // P(x, y, z) + P(x, -y, z) = infinity
            Tuple9(
                "34f9460f0e4f08393d192b3c5133a6ba099aa0ad9fd54ebccfacdfa239ff49c6",
                "0b71ea9bd730fd8923f6d25a7a91e7dd7728a960686cb5a901bb419e0f2ca232",
                "1",
                "34f9460f0e4f08393d192b3c5133a6ba099aa0ad9fd54ebccfacdfa239ff49c6",
                "f48e156428cf0276dc092da5856e182288d7569f97934a56fe44be60f0d359fd",
                "1",
                "0",
                "0",
                "0",
            ),
            // Addition with z1=z2=1 same point.
            // P(x, y, z) + P(x, y, z) = 2P
            Tuple9(
                "34f9460f0e4f08393d192b3c5133a6ba099aa0ad9fd54ebccfacdfa239ff49c6",
                "0b71ea9bd730fd8923f6d25a7a91e7dd7728a960686cb5a901bb419e0f2ca232",
                "1",
                "34f9460f0e4f08393d192b3c5133a6ba099aa0ad9fd54ebccfacdfa239ff49c6",
                "0b71ea9bd730fd8923f6d25a7a91e7dd7728a960686cb5a901bb419e0f2ca232",
                "1",
                "ec9f153b13ee7bd915882859635ea9730bf0dc7611b2c7b0e37ee64f87c50c27",
                "b082b53702c466dcf6e984a35671756c506c67c2fcb8adb408c44dd0755c8f2a",
                "16e3d537ae61fb1247eda4b4f523cfbaee5152c0d0d96b520376833c1e594464",
            ),

            // Addition with z1=z2 (!=1) different x values.
            Tuple9(
                "d3e5183c393c20e4f464acf144ce9ae8266a82b67f553af33eb37e88e7fd2718",
                "5b8f54deb987ec491fb692d3d48f3eebb9454b034365ad480dda0cf079651190",
                "2",
                "5d2fe112c21891d440f65a98473cb626111f8a234d2cd82f22172e369f002147",
                "98e3386a0a622a35c4561ffb32308d8e1c6758e10ebb1b4ebd3d04b4eb0ecbe8",
                "2",
                "cfbc7da1e569b334460788faae0286e68b3af7379d5504efc25e4dba16e46a60",
                "817de4d86ef80d1ac0ded00426176fd3e787a5579f43452b2a1db021e6ac3778",
                "129591ad11b8e1de99235b4e04dc367bd56a0ed99baf3a77c6c75f5a6e05f08d",
            ),
            // Addition with z1=z2 (!=1) same x opposite y.
            // P(x, y, z) + P(x, -y, z) = infinity
            Tuple9(
                "d3e5183c393c20e4f464acf144ce9ae8266a82b67f553af33eb37e88e7fd2718",
                "5b8f54deb987ec491fb692d3d48f3eebb9454b034365ad480dda0cf079651190",
                "2",
                "d3e5183c393c20e4f464acf144ce9ae8266a82b67f553af33eb37e88e7fd2718",
                "a470ab21467813b6e0496d2c2b70c11446bab4fcbc9a52b7f225f30e869aea9f",
                "2",
                "0",
                "0",
                "0",
            ),
            // Addition with z1=z2 (!=1) same point.
            // P(x, y, z) + P(x, y, z) = 2P
            Tuple9(
                "d3e5183c393c20e4f464acf144ce9ae8266a82b67f553af33eb37e88e7fd2718",
                "5b8f54deb987ec491fb692d3d48f3eebb9454b034365ad480dda0cf079651190",
                "2",
                "d3e5183c393c20e4f464acf144ce9ae8266a82b67f553af33eb37e88e7fd2718",
                "5b8f54deb987ec491fb692d3d48f3eebb9454b034365ad480dda0cf079651190",
                "2",
                "9f153b13ee7bd915882859635ea9730bf0dc7611b2c7b0e37ee65073c50fabac",
                "2b53702c466dcf6e984a35671756c506c67c2fcb8adb408c44dd125dc91cb988",
                "6e3d537ae61fb1247eda4b4f523cfbaee5152c0d0d96b520376833c2e5944a11",
            ),

            // Addition with z1!=z2 and z2=1 different x values.
            Tuple9(
                "d3e5183c393c20e4f464acf144ce9ae8266a82b67f553af33eb37e88e7fd2718",
                "5b8f54deb987ec491fb692d3d48f3eebb9454b034365ad480dda0cf079651190",
                "2",
                "d74bf844b0862475103d96a611cf2d898447e288d34b360bc885cb8ce7c00575",
                "131c670d414c4546b88ac3ff664611b1c38ceb1c21d76369d7a7a0969d61d97d",
                "1",
                "3ef1f68795a6ccd1181e23eab80a1b9a2cebdcde755413bf097936eb5b91b4f3",
                "0bef26c377c068d606f6802130bb7e9f3c3d2abcfa1a295950ed81133561cb04",
                "252b235a2371c3bd3246b69c09b86cf7aad41db3375e74ef8d8ebeb4dc0be11a",
            ),
            // Addition with z1!=z2 and z2=1 same x opposite y.
            // P(x, y, z) + P(x, -y, z) = infinity
            Tuple9(
                "d3e5183c393c20e4f464acf144ce9ae8266a82b67f553af33eb37e88e7fd2718",
                "5b8f54deb987ec491fb692d3d48f3eebb9454b034365ad480dda0cf079651190",
                "2",
                "34f9460f0e4f08393d192b3c5133a6ba099aa0ad9fd54ebccfacdfa239ff49c6",
                "f48e156428cf0276dc092da5856e182288d7569f97934a56fe44be60f0d359fd",
                "1",
                "0",
                "0",
                "0",
            ),
            // Addition with z1!=z2 and z2=1 same point.
            // P(x, y, z) + P(x, y, z) = 2P
            Tuple9(
                "d3e5183c393c20e4f464acf144ce9ae8266a82b67f553af33eb37e88e7fd2718",
                "5b8f54deb987ec491fb692d3d48f3eebb9454b034365ad480dda0cf079651190",
                "2",
                "34f9460f0e4f08393d192b3c5133a6ba099aa0ad9fd54ebccfacdfa239ff49c6",
                "0b71ea9bd730fd8923f6d25a7a91e7dd7728a960686cb5a901bb419e0f2ca232",
                "1",
                "9f153b13ee7bd915882859635ea9730bf0dc7611b2c7b0e37ee65073c50fabac",
                "2b53702c466dcf6e984a35671756c506c67c2fcb8adb408c44dd125dc91cb988",
                "6e3d537ae61fb1247eda4b4f523cfbaee5152c0d0d96b520376833c2e5944a11",
            ),

            // Addition with z1!=z2 and z2!=1 different x values.
            // P(x, y, z) + P(x, y, z) = 2P
            Tuple9(
                "d3e5183c393c20e4f464acf144ce9ae8266a82b67f553af33eb37e88e7fd2718",
                "5b8f54deb987ec491fb692d3d48f3eebb9454b034365ad480dda0cf079651190",
                "2",
                "91abba6a34b7481d922a4bd6a04899d5a686f6cf6da4e66a0cb427fb25c04bd4",
                "03fede65e30b4e7576a2abefc963ddbf9fdccbf791b77c29beadefe49951f7d1",
                "3",
                "3f07081927fd3f6dadd4476614c89a09eba7f57c1c6c3b01fa2d64eac1eef31e",
                "949166e04ebc7fd95a9d77e5dfd88d1492ecffd189792e3944eb2b765e09e031",
                "eb8cba81bcffa4f44d75427506737e1f045f21e6d6f65543ee0e1d163540c931",
            ),

            // Addition with z1!=z2 and z2!=1 same x opposite y.
            // P(x, y, z) + P(x, -y, z) = infinity
            Tuple9(
                "d3e5183c393c20e4f464acf144ce9ae8266a82b67f553af33eb37e88e7fd2718",
                "5b8f54deb987ec491fb692d3d48f3eebb9454b034365ad480dda0cf079651190",
                "2",
                "dcc3768780c74a0325e2851edad0dc8a566fa61a9e7fc4a34d13dcb509f99bc7",
                "cafc41904dd5428934f7d075129c8ba46eb622d4fc88d72cd1401452664add18",
                "3",
                "0",
                "0",
                "0",
            ),

            // Addition with z1!=z2 and z2!=1 same point.
            // P(x, y, z) + P(x, y, z) = 2P
            Tuple9(
                "d3e5183c393c20e4f464acf144ce9ae8266a82b67f553af33eb37e88e7fd2718",
                "5b8f54deb987ec491fb692d3d48f3eebb9454b034365ad480dda0cf079651190",
                "2",
                "dcc3768780c74a0325e2851edad0dc8a566fa61a9e7fc4a34d13dcb509f99bc7",
                "3503be6fb22abd76cb082f8aed63745b9149dd2b037728d32ebfebac99b51f17",
                "3",
                "9f153b13ee7bd915882859635ea9730bf0dc7611b2c7b0e37ee65073c50fabac",
                "2b53702c466dcf6e984a35671756c506c67c2fcb8adb408c44dd125dc91cb988",
                "6e3d537ae61fb1247eda4b4f523cfbaee5152c0d0d96b520376833c2e5944a11",
            ),
        ).map {
                (
                    test_x1: String, test_y1: String, test_z1: String, // Coordinates (in hex) of first point to add
                    test_x2: String, test_y2: String, test_z2: String, // Coordinates (in hex) of second point to add
                    test_x3: String, test_y3: String, test_z3: String,
                ),
            ->
            DynamicTest.dynamicTest("Test: $test_x1, $test_x2, $test_x3") {
                // Convert hex to field values.
                val p1 = JacobianPoint.fromHex(test_x1, test_y1, test_z1)
                val p2 = JacobianPoint.fromHex(test_x2, test_y2, test_z2)
                val expected = JacobianPoint.fromHex(test_x3, test_y3, test_z3)

                // Ensure the test data is using points that are actually on
                // the curve (or the point at infinity).
                assertFalse(!p1.z.isZero && !isJacobianOnS256Curve(p1))
                assertFalse(!p2.z.isZero && !isJacobianOnS256Curve(p2))
                assertFalse(!expected.z.isZero && !isJacobianOnS256Curve(expected))
                // Add the two points.
                val r = Secp256k1Curve.addNonConst(p1, p2)
                assertEquals(expected, r)
            }
        }.stream()
    }

    @TestFactory
    fun doubleJacobian(): Stream<DynamicTest> {
        // Doubling a point at infinity is still infinity.
        return listOf(
            Tuple6(
                "0",
                "0",
                "0",
                "0",
                "0",
                "0",
            ),
            // Doubling with z1=1.
            Tuple6(
                "34f9460f0e4f08393d192b3c5133a6ba099aa0ad9fd54ebccfacdfa239ff49c6",
                "0b71ea9bd730fd8923f6d25a7a91e7dd7728a960686cb5a901bb419e0f2ca232",
                "1",
                "ec9f153b13ee7bd915882859635ea9730bf0dc7611b2c7b0e37ee64f87c50c27",
                "b082b53702c466dcf6e984a35671756c506c67c2fcb8adb408c44dd0755c8f2a",
                "16e3d537ae61fb1247eda4b4f523cfbaee5152c0d0d96b520376833c1e594464",
            ),
            // Doubling with z1!=1.
            Tuple6(
                "d3e5183c393c20e4f464acf144ce9ae8266a82b67f553af33eb37e88e7fd2718",
                "5b8f54deb987ec491fb692d3d48f3eebb9454b034365ad480dda0cf079651190",
                "2",
                "9f153b13ee7bd915882859635ea9730bf0dc7611b2c7b0e37ee65073c50fabac",
                "2b53702c466dcf6e984a35671756c506c67c2fcb8adb408c44dd125dc91cb988",
                "6e3d537ae61fb1247eda4b4f523cfbaee5152c0d0d96b520376833c2e5944a11",
            ),
            // From btcd issue #709.
            Tuple6(
                "201e3f75715136d2f93c4f4598f91826f94ca01f4233a5bd35de9708859ca50d",
                "bdf18566445e7562c6ada68aef02d498d7301503de5b18c6aef6e2b1722412e1",
                "0000000000000000000000000000000000000000000000000000000000000001",
                "4a5e0559863ebb4e9ed85f5c4fa76003d05d9a7626616e614a1f738621e3c220",
                "00000000000000000000000000000000000000000000000000000001b1388778",
                "7be30acc88bceac58d5b4d15de05a931ae602a07bcb6318d5dedc563e4482993",
            ),
        ).map {
                (
                    test_x1: String, test_y1: String, test_z1: String, // Coordinates (in hex) of point to double
                    test_x3: String, test_y3: String, test_z3: String,
                ),
            -> // Coordinates (in hex) of expected point
            DynamicTest.dynamicTest("Test: $test_x1, $test_y1, $test_z1") {
                // Convert hex to field values.
                val p1 = JacobianPoint.fromHex(test_x1, test_y1, test_z1)
                val expected = JacobianPoint.fromHex(test_x3, test_y3, test_z3)
                assertFalse(!p1.z.isZero && !isJacobianOnS256Curve(p1))
                assertFalse(!expected.z.isZero && !isJacobianOnS256Curve(expected))
                // Double the point.
                val r = Secp256k1Curve.doubleNonConst(p1)
                assertEquals(expected, r)
            }
        }.stream()
    }

    @TestFactory
    fun naf(): Stream<DynamicTest> {
        return listOf(
            Tuple2("zero", "00"),
            Tuple2("just before first carry", "aa"),
            Tuple2("first carry", "ab"),
            Tuple2("leading zeroes", "002f20569b90697ad471c1be6107814f53f47446be298a3a2a6b686b97d35cf9"),
            Tuple2("257 bits when NAF encoded", "c000000000000000000000000000000000000000000000000000000000000001"),
            Tuple2("32-byte scalar", "6df2b5d30854069ccdec40ae022f5c948936324a4e9ebed8eb82cfd5a6b6d766"),
            Tuple2("first term of balanced length-two representation #1", "b776e53fb55f6b006a270d42d64ec2b1"),
            Tuple2("second term balanced length-two representation #1", "d6cc32c857f1174b604eefc544f0c7f7"),
            Tuple2("first term of balanced length-two representation #2", "45c53aa1bb56fcd68c011e2dad6758e4"),
            Tuple2("second term of balanced length-two representation #2", "a2e79d200f27f2360fba57619936159b"),
        ).map { (name: String, input: String) ->
            DynamicTest.dynamicTest("Test: $name") {
                // Ensure the resulting positive and negative portions of the overall
                // NAF representation adhere to the requirements of NAF encoding and
                // they sum back to the original value.
                val result = Secp256k1Curve.naf(Hex.decodeOrThrow(input))
                val bigInt = BigInt.fromHex(input)
                checkNafEncoding(result.pos, result.neg, bigInt)
            }
        }.stream()
    }

    @Test
    fun nafEmpty() {
        val result = Secp256k1Curve.naf(Hex.decodeOrThrow(""))
        assertTrue(result.pos.isEmpty())
        assertTrue(result.neg.isEmpty())
    }

    @Test
    fun nafRandom() {
        for (i in 0..1024) {
            val (bigIntVal, modNVal) = randIntAndModNScalar()
            val valBytes = modNVal.bytes()
            val result = Secp256k1Curve.naf(valBytes)
            checkNafEncoding(result.pos, result.neg, bigIntVal)
        }
    }

    @TestFactory
    fun scalarMult(): Stream<DynamicTest> {
        return listOf(
            Tuple8(
                "base mult, essentially",
                "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                "483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8",
                "1",
                "18e14a7b6a307f426a94f8114701e7c8e774e7f9a47e2c2035db29a206321725",
                "e94ce9a0f0ebf730a168b834bbb0c56b9d24040cabd0187ca1d74c51d10c9cc4",
                "f8130e7ce8c7e521e68a6ed95dcdea39ee447dc109503936a8ccd282da8191ff",
                "7a0ce7c2cba4f0f98f81704096a1092d2adcbc3066900ca4bd6d8ee994524a52",
            ),
            Tuple8(
                "From btcd issue #709",
                "000000000000000000000000000000000000000000000000000000000000002c",
                "420e7a99bba18a9d3952597510fd2b6728cfeafc21a4e73951091d4d8ddbe94e",
                "1",
                "a2e8ba2e8ba2e8ba2e8ba2e8ba2e8ba219b51835b55cc30ebfe2f6599bc56f58",
                "ab73a226e5b14c350ae7e44ea0ba158b05079ff5735e89d3b462741128f67f0d",
                "2db60fff1e90aea7d0aae787afc499d4c5ed3e48b3fc44252cccf836c0b8be79",
                "5770fc1ff2e893dff52dd87c7f9edbc50778d365511a499e34bfa0949a53295b",
            ),
        ).map { (name: String, test_x: String, test_y: String, test_z: String, test_k: String, test_rx: String, test_ry: String, test_rz: String) ->
            DynamicTest.dynamicTest("Test: $name") {
                val point = JacobianPoint.fromHex(test_x, test_y, test_z)
                val k = Hex.decodeOrThrow(test_k)
                val expected = JacobianPoint.fromHex(test_rx, test_ry, test_rz)
                val kModN = ModNScalar.setByteSlice(k)
                val result = Secp256k1Curve.scalarMultNonConst(kModN, point)
                assertEquals(expected, result)
            }
        }.stream()
    }

    @Test
    fun scalarMultRandom() {
        // Strategy for this test:
        //
        // Get a random exponent from the generator point at first
        // This creates a new point which is used in the next iteration
        // Use another random exponent on the new point.
        // We use BaseMult to verify by multiplying the previous exponent
        // and the new random exponent together (mod N)
        val basePoint = bigAffineToJacobian(Secp256k1Curve.g)
        val exponent = ModNScalar.setInt(1u)
        val secureRandom = SecureRandom()
        for (i in 0..1023) {
            val data = ByteArray(32)
            secureRandom.nextBytes(data)
            val k = ModNScalar.setByteSlice(data)
            val point = Secp256k1Curve.scalarMultNonConst(k, basePoint).toAffine()
            val want = Secp256k1Curve.scalarBaseMultNonConst(exponent * k).toAffine()
            assertEquals(want, point)
        }
    }

    @TestFactory
    fun splitK(): Stream<DynamicTest> {
        return listOf(
            Tuple5(
                "6df2b5d30854069ccdec40ae022f5c948936324a4e9ebed8eb82cfd5a6b6d766",
                "b776e53fb55f6b006a270d42d64ec2b1",
                "d6cc32c857f1174b604eefc544f0c7f7",
                -1,
                -1,
            ),
            Tuple5(
                "6ca00a8f10632170accc1b3baf2a118fa5725f41473f8959f34b8f860c47d88d",
                "07b21976c1795723c1bfbfa511e95b84",
                "d8d2d5f9d20fc64fd2cf9bda09a5bf90",
                1,
                -1,
            ),
            Tuple5(
                "b2eda8ab31b259032d39cbc2a234af17fcee89c863a8917b2740b67568166289",
                "507d930fecda7414fc4a523b95ef3c8c",
                "f65ffb179df189675338c6185cb839be",
                -1,
                -1,
            ),
            Tuple5(
                "f6f00e44f179936f2befc7442721b0633f6bafdf7161c167ffc6f7751980e3a0",
                "08d0264f10bcdcd97da3faa38f85308d",
                "65fed1506eb6605a899a54e155665f79",
                -1,
                -1,
            ),
            Tuple5(
                "8679085ab081dc92cdd23091ce3ee998f6b320e419c3475fae6b5b7d3081996e",
                "89fbf24fbaa5c3c137b4f1cedc51d975",
                "d38aa615bd6754d6f4d51ccdaf529fea",
                -1,
                -1,
            ),
            Tuple5(
                "6b1247bb7931dfcae5b5603c8b5ae22ce94d670138c51872225beae6bba8cdb3",
                "8acc2a521b21b17cfb002c83be62f55d",
                "35f0eff4d7430950ecb2d94193dedc79",
                -1,
                -1,
            ),
            Tuple5(
                "a2e8ba2e8ba2e8ba2e8ba2e8ba2e8ba219b51835b55cc30ebfe2f6599bc56f58",
                "45c53aa1bb56fcd68c011e2dad6758e4",
                "a2e79d200f27f2360fba57619936159b",
                -1,
                -1,
            ),
        ).map { (test_k: String, test_k1: String, test_k2: String, test_s1: Int, test_s2: Int) ->
            DynamicTest.dynamicTest("Test: $test_k, $test_k1, $test_k2") {
                val k = BigInt.fromHex(test_k)
                val (k1Bytes, k2Bytes, k1Sign, k2Sign) = Secp256k1Curve.splitK(BigInt.toBytes(k))
                assertEquals(test_k1, Hex.encode(k1Bytes))
                assertEquals(test_k2, Hex.encode(k2Bytes))
                assertEquals(test_s1, k1Sign)
                assertEquals(test_s2, k2Sign)
                var k1Int = BigInt.fromBytes(k1Bytes)
                val k1SignInt = BigInteger.valueOf(k1Sign.toLong())
                k1Int = k1Int.multiply(k1SignInt)
                var k2Int = BigInt.fromBytes(k2Bytes)
                val k2SignInt = BigInteger.valueOf(k2Sign.toLong())
                k2Int = k2Int.multiply(k2SignInt)
                var gotK = k2Int.multiply(Secp256k1Curve.endomorphismLambda)
                gotK = k1Int.add(gotK)
                gotK = gotK.mod(Secp256k1Curve.n)
                assertEquals(0, k.compareTo(gotK))
            }
        }.stream()
    }

    @Test
    fun splitKRandom() {
        val random = SecureRandom()
        for (i in 0..1023) {
            val bytesK = ByteArray(32)
            random.nextBytes(bytesK)
            val k = BigInt.fromBytes(bytesK)
            val (k1Bytes, k2Bytes, k1Sign, k2Sign) = Secp256k1Curve.splitK(bytesK)
            var k1Int = BigInt.fromBytes(k1Bytes)
            val k1SignInt = BigInteger.valueOf(k1Sign.toLong())
            k1Int = k1Int.multiply(k1SignInt)
            var k2Int = BigInt.fromBytes(k2Bytes)
            val k2SignInt = BigInteger.valueOf(k2Sign.toLong())
            k2Int = k2Int.multiply(k2SignInt)
            var gotK = k2Int.multiply(Secp256k1Curve.endomorphismLambda)
            gotK = k1Int.add(gotK).mod(Secp256k1Curve.n)
            assertEquals(0, k.compareTo(gotK))
        }
    }

    @TestFactory
    fun decompressY(): Stream<DynamicTest> {
        return listOf(
            Tuple5(
                "x = 0 -- not a point on the curve",
                "00",
                false,
                "",
                "",
            ),
            Tuple5(
                "x = 1",
                "01",
                true,
                "bde70df51939b94c9c24979fa7dd04ebd9b3572da7802290438af2a681895441",
                "4218f20ae6c646b363db68605822fb14264ca8d2587fdd6fbc750d587e76a7ee",
            ),
            Tuple5(
                "x = secp256k1 prime (aka 0) -- not a point on the curve",
                "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f",
                false,
                "",
                "",
            ),
            Tuple5(
                "x = secp256k1 prime - 1 -- not a point on the curve",
                "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e",
                false,
                "",
                "",
            ),
            Tuple5(
                "x = secp256k1 group order",
                "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141",
                true,
                "670999be34f51e8894b9c14211c28801d9a70fde24b71d3753854b35d07c9a11",
                "98f66641cb0ae1776b463ebdee3d77fe2658f021db48e2c8ac7ab4c92f83621e",
            ),
            Tuple5(
                "x = secp256k1 group order - 1 -- not a point on the curve",
                "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140",
                false,
                "",
                "",
            ),
        ).map { (name: String, x: String, valid: Boolean, wantOddY: String, wantEvenY: String) ->
            DynamicTest.dynamicTest("Test: $name") {
                // Decompress the test odd y coordinate for the given test x coordinate
                // and ensure the returned validity flag matches the expected result.
                val fx = FieldVal.setByteSlice(Hex.decodeOrThrow(x))
                val (oddY, oddYValid) = Secp256k1Curve.decompressY(fx, true)
                assertEquals(valid, oddYValid)

                val (evenY, evenYValid) = Secp256k1Curve.decompressY(fx, false)
                assertEquals(valid, evenYValid)

                if (evenYValid) {
                    assertEquals(FieldVal.setByteSlice(Hex.decodeOrThrow(wantOddY)), oddY.normalize())
                    assertEquals(FieldVal.setByteSlice(Hex.decodeOrThrow(wantEvenY)), evenY.normalize())
                    assertTrue(oddY.normalize().isOdd)
                    assertFalse(evenY.normalize().isOdd)
                }
            }
        }.stream()
    }

    @Test
    fun decompressYRandom() {
        val random = SecureRandom()
        for (i in 0..1023) {
            val buf = ByteArray(32)
            random.nextBytes(buf)
            val x = FieldVal.setBytes(buf)
            val (oddY, oddSuccess) = Secp256k1Curve.decompressY(x, true)
            val (evenY, evenSuccess) = Secp256k1Curve.decompressY(x, false)
            assertEquals(oddSuccess, evenSuccess)
            if (oddSuccess) {
                assertTrue(oddY.normalize().isOdd)
                assertFalse(evenY.normalize().isOdd)
                assertTrue(Secp256k1Curve.isOnCurve(x, oddY))
                assertTrue(Secp256k1Curve.isOnCurve(x, evenY))
            }
        }
    }

    // isJacobianOnS256Curve returns boolean if the point (x,y,z) is on the
    // secp256k1 curve.
    private fun isJacobianOnS256Curve(p: JacobianPoint): Boolean {
        // Elliptic curve equation for secp256k1 is: y^2 = x^3 + 7
        // In Jacobian coordinates, Y = y/z^3 and X = x/z^2
        // Thus:
        // (y/z^3)^2 = (x/z^2)^3 + 7
        // y^2/z^6 = x^3/z^6 + 7
        // y^2 = x^3 + 7*z^6
        val y2 = p.y.square().normalize()
        val z2 = p.z.square()
        val x3 = p.x.square() * p.x
        val qw1 = z2.square() * z2
        val qw2 = qw1 * 7
        val result = (qw2 + x3).normalize()
        return y2 == result
    }

    // checkNAFEncoding returns an error if the provided positive and negative
    // portions of an overall NAF encoding do not adhere to the requirements or they
    // do not sum back to the provided original value.
    private fun checkNafEncoding(pos: ByteArray, neg: ByteArray, origValue: BigInteger) {
        // NAF must not have a leading zero byte and the number of negative
        // bytes must not exceed the positive portion.
        assertFalse(pos.isNotEmpty() && pos[0] == 0.toByte())
        assertTrue(neg.size <= pos.size)
        // Ensure the result doesn't have any adjacent non-zero digits.
        val gotPos = BigInt.fromBytes(pos)
        val gotNeg = BigInt.fromBytes(neg)
        val posOrNeg = gotPos.or(gotNeg)
        var prevBit = posOrNeg.testBit(0)
        for (bit in 1 until posOrNeg.bitLength()) {
            val thisBit = posOrNeg.testBit(bit)
            assertFalse(prevBit && thisBit)
            prevBit = thisBit
        }
        // Ensure the resulting positive and negative portions of the overall
        // NAF representation sum back to the original value.
        val gotValue = gotPos.subtract(gotNeg)
        assertEquals(0, origValue.compareTo(gotValue))
    }

    private fun randIntAndModNScalar(): Tuple2<BigInteger, ModNScalar> {
        val r = SecureRandom()
        val buf = ByteArray(32)
        r.nextBytes(buf)
        val big = BigInt.fromBytes(buf)
        return Tuple(big.mod(Secp256k1Curve.n), ModNScalar.setBytes(buf))
    }

    private fun bigAffineToJacobian(curvePoint: CurvePoint): JacobianPoint {
        return JacobianPoint(
            FieldVal.setByteSlice(BigInt.toBytes(curvePoint.x)),
            FieldVal.setByteSlice(BigInt.toBytes(curvePoint.y)),
            FieldVal.One,
        )
    }
}
