// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore

import org.erwinkok.libp2p.core.datastore.Key.Companion.key
import org.erwinkok.libp2p.core.util.Path
import org.erwinkok.util.Tuple2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream

internal class KeyTest {
    @TestFactory
    fun pathClean(): Stream<DynamicTest> {
        return listOf(
            // Already clean
            Tuple2("", "."),
            Tuple2("abc", "abc"),
            Tuple2("abc/def", "abc/def"),
            Tuple2("a/b/c", "a/b/c"),
            Tuple2(".", "."),
            Tuple2("..", ".."),
            Tuple2("../..", "../.."),
            Tuple2("../../abc", "../../abc"),
            Tuple2("/abc", "/abc"),
            Tuple2("/", "/"),

            // Remove trailing slash
            Tuple2("abc/", "abc"),
            Tuple2("abc/def/", "abc/def"),
            Tuple2("a/b/c/", "a/b/c"),
            Tuple2("./", "."),
            Tuple2("../", ".."),
            Tuple2("../../", "../.."),
            Tuple2("/abc/", "/abc"),

            // Remove doubled slash
            Tuple2("abc//def//ghi", "abc/def/ghi"),
            Tuple2("//abc", "/abc"),
            Tuple2("///abc", "/abc"),
            Tuple2("//abc//", "/abc"),
            Tuple2("abc//", "abc"),

            // Remove . elements
            Tuple2("abc/./def", "abc/def"),
            Tuple2("/./abc/def", "/abc/def"),
            Tuple2("abc/.", "abc"),

            // Remove .. elements
            Tuple2("abc/def/ghi/../jkl", "abc/def/jkl"),
            Tuple2("abc/def/../ghi/../jkl", "abc/jkl"),
            Tuple2("abc/def/..", "abc"),
            Tuple2("abc/def/../..", "."),
            Tuple2("/abc/def/../..", "/"),
            Tuple2("abc/def/../../..", ".."),
            Tuple2("/abc/def/../../..", "/"),
            Tuple2("abc/def/../../../ghi/jkl/../../../mno", "../../mno"),

            // Combinations
            Tuple2("abc/./../def", "def"),
            Tuple2("abc//./../def", "def"),
            Tuple2("abc/../../././../def", "../../def"),
        ).map { (path: String, result: String) ->
            DynamicTest.dynamicTest("Test: $path") {
                assertEquals(result, Path.clean(path))
                assertEquals(result, Path.clean(result))
            }
        }.stream()
    }

    @Test
    fun testWithNamespaces() {
        assertEquals("/one/two", Key.withNamespaces(listOf("one", "two")).toString())
    }

    @Test
    fun testClean() {
        assertEquals("/", key("").toString())
        assertEquals("/", key("//").toString())
        assertEquals("/bla", key("/bla//").toString())
        assertEquals("/bla/blie", key("/bla//blie//").toString())
    }

    @Test
    fun testReverse() {
        assertEquals("/Actor:JohnCleese/MontyPython/Comedy", key("/Comedy/MontyPython/Actor:JohnCleese").reverse().toString())
    }

    @Test
    fun testBaseNamespace() {
        assertEquals("Actor:JohnCleese", key("/Comedy/MontyPython/Actor:JohnCleese").baseNamespace())
    }

    @Test
    fun testList() {
        assertEquals(listOf("Comedy", "MontyPython", "Actor:JohnCleese"), key("/Comedy/MontyPython/Actor:JohnCleese").list())
    }

    @Test
    fun testType() {
        assertEquals("Actor", key("/Comedy/MontyPython/Actor:JohnCleese").type)
    }

    @Test
    fun testName() {
        assertEquals("JohnCleese", key("/Comedy/MontyPython/Actor:JohnCleese").name)
    }

    @Test
    fun testInstance() {
        assertEquals("/Comedy/MontyPython/Actor:JohnCleese", key("/Comedy/MontyPython/Actor").instance("JohnCleese").toString())
    }

    @Test
    fun testPath() {
        assertEquals("/Comedy/MontyPython/Actor", key("/Comedy/MontyPython/Actor:JohnCleese").path().toString())
    }

    @Test
    fun testParent() {
        assertEquals("/Comedy/MontyPython", key("/Comedy/MontyPython/Actor:JohnCleese").parent().toString())
    }

    @Test
    fun testChild() {
        assertEquals("/Comedy/MontyPython/Actor:JohnCleese", key("/Comedy/MontyPython").child(key("Actor:JohnCleese")).toString())
    }

    @Test
    fun testAncestor() {
        assertTrue(key("/Comedy").isAncestorOf(key("/Comedy/MontyPython")))
    }

    @Test
    fun testDecendant() {
        assertTrue(key("/Comedy/MontyPython").isDecendantOf(key("/Comedy")))
    }
}
