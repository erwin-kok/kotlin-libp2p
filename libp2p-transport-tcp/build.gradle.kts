// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
plugins {
    id("libp2p.library")
    id("libp2p.publish")
}

dependencies {
    implementation(platform(kotlin("bom")))
    implementation(kotlin("stdlib"))

    implementation(projects.libp2pCore)

    implementation(libs.ipaddress)
    implementation(libs.kotlin.logging)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.network)
    implementation(libs.multiformat)
    implementation(libs.result.monad)

    testImplementation(testFixtures(libs.result.monad))
    testImplementation(projects.libp2pCrypto)
    testImplementation(projects.libp2pMuxerMplex)
    testImplementation(projects.libp2pSecurityPlaintext)
    testImplementation(projects.libp2pTesting)
    testImplementation(libs.io.mockk.mockk)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.coroutines.debug)
}
