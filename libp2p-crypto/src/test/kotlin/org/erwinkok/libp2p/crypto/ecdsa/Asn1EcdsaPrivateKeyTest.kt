// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ecdsa

import org.erwinkok.result.expectNoErrors
import org.erwinkok.util.Hex
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

internal class Asn1EcdsaPrivateKeyTest {
    @Test
    fun parseAsn1PrivateKey1() {
        val bytes = Hex.decodeOrThrow("3081a40201010430bdb9839c08ee793d1157886a7a758a3c8b2a17a4df48f17ace57c72c56b4723cf21dcda21d4e1ad57ff034f19fcfd98ea00706052b81040022a16403620004feea808b5ee2429cfcce13c32160e1c960990bd050bb0fdf7222f3decd0a55008e32a6aa3c9062051c4cba92a7a3b178b24567412d43cdd2f882fa5addddd726fe3e208d2c26d733a773a597abb749714df7256ead5105fa6e7b3650de236b50")
        val asn1PrivateKey = Asn1EcdsaPrivateKey.unmarshal(bytes).expectNoErrors()
        val result = asn1PrivateKey.marshal().expectNoErrors()
        assertArrayEquals(bytes, result)
    }

    @Test
    fun parseAsn1PrivateKey2() {
        val bytes = Hex.decodeOrThrow("3078020101042100f9f43a04b9bdc3ab01f53be6df80e7a7bc3eaf7b87fc24e630a4a0aa97633645a00a06082a8648ce3d030107a1440342000441a51bc318461b4c39a45048a16d4fc2a935b1ea7fe86e8c1fa219d6f2438f7c7fd62957d3442efb94b6a23eb0ea66dda663dc42f379cda6630b21b7888a5d3d")
        val asn1PrivateKey = Asn1EcdsaPrivateKey.unmarshal(bytes).expectNoErrors()
        val result = asn1PrivateKey.marshal().expectNoErrors()
        assertArrayEquals(bytes, result)
    }

    @Test
    fun parseAsn1PrivateKey3() {
        val bytes = Hex.decodeOrThrow("3081db0201010441607b4f985774ac21e633999794542e09312073480baa69550914d6d43d8414441e61b36650567901da714f94dffb3ce0e2575c31928a0997d51df5c440e983ca17a00706052b81040023a181890381860004001661557afedd7ac8d6b70e038e576558c626eb62edda36d29c3a1310277c11f67a8c6f949e5430a37dcfb95d902c1b5b5379c389873b9dd17be3bdb088a4774a7401072f830fb9a08d93bfa50a03dd3292ea07928724ddb915d831917a338f6b0aecfbc3cf5352c4a1295d356890c41c34116d29eeb93779aab9d9d78e2613437740f6")
        val asn1PrivateKey = Asn1EcdsaPrivateKey.unmarshal(bytes).expectNoErrors()
        val result = asn1PrivateKey.marshal().expectNoErrors()
        assertArrayEquals(bytes, result)
    }
}
