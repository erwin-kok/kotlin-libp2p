// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.datastore

import org.erwinkok.libp2p.testing.testsuites.datastore.DatastoreTestSuite
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream

internal class LogDatastoreTest {
    @TestFactory
    fun testSuiteBasic(): Stream<DynamicTest> {
        return DatastoreTestSuite("LogDatastore").testBasic { scope -> LogDatastore(scope, "", MapDatastore(scope)) }
    }

    @TestFactory
    fun testSuiteBatch(): Stream<DynamicTest> {
        return DatastoreTestSuite("LogDatastore").testBatch { scope -> LogDatastore(scope, "", MapDatastore(scope)) }
    }
}
