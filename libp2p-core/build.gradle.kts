// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
plugins {
    `java-test-fixtures`
    id("libp2p.library")
    id("libp2p.protoc")
    id("libp2p.publish")
}

group = "org.erwinkok.libp2p"
version = "0.1.0-SNAPSHOT"

dependencies {
    implementation(platform(kotlin("bom")))
    implementation(kotlin("stdlib"))

    implementation(projects.libp2pCrypto)

    implementation(libs.aedile)
    implementation(libs.slf4j.api)
    implementation(libs.kotlin.logging)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.result.monad)
    implementation(libs.multiformat)
    implementation(libs.ipaddress)
    implementation(libs.ktor.network)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.atomicfu)
    implementation(libs.kotlin.serialization.cbor)
    implementation(libs.reflections)

    runtimeOnly(libs.junit.jupiter.engine)

    testImplementation(testFixtures(libs.result.monad))
    testImplementation(projects.libp2pTesting)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.coroutines.debug)
    testImplementation(libs.io.mockk.mockk)

    testRuntimeOnly(libs.junit.jupiter.engine)

    api(libs.protobuf.java)
}
