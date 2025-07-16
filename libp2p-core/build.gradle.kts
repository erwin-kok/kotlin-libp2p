// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
plugins {
    `java-test-fixtures`
    id("libp2p.library")
    id("libp2p.protoc")
    id("libp2p.publish")
}

dependencies {
    implementation(platform(kotlin("bom")))
    implementation(kotlin("stdlib"))

    implementation(projects.libp2pCrypto)

    implementation(libs.caffeine)
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

    testImplementation(testFixtures(libs.result.monad))
    testImplementation(projects.libp2pTesting)
    testImplementation(projects.libp2pTransportTcp)
    testImplementation(projects.libp2pSecurityPlaintext)
    testImplementation(projects.libp2pMuxerMplex)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.coroutines.debug)
    testImplementation(libs.io.mockk.mockk)
    testImplementation(libs.slf4j.api)

    testRuntimeOnly(libs.logback.classic)
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    api(libs.protobuf.java)
}
