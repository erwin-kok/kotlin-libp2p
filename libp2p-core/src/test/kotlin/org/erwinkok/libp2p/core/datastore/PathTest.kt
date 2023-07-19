// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore

import org.erwinkok.libp2p.core.util.Path
import org.erwinkok.util.Tuple2
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream

internal class PathTest {
    @TestFactory
    fun clean(): Stream<DynamicTest> {
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
                Assertions.assertEquals(result, Path.clean(path))
                Assertions.assertEquals(result, Path.clean(result))
            }
        }.stream()
    }
}
