// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
plugins {
    id("libp2p.library")
    id("libp2p.protoc")
    id("libp2p.publish")
}

group = "org.erwinkok.libp2p"
version = "0.1.0-SNAPSHOT"

dependencies {
    implementation(platform(kotlin("bom")))
    implementation(kotlin("stdlib"))

    implementation(projects.libp2pCore)
    implementation(projects.libp2pCrypto)

    implementation(libs.kotlin.logging)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.network)
    implementation(libs.multiformat)
    implementation(libs.result.monad)

    testImplementation(testFixtures(libs.result.monad))
    testImplementation(projects.libp2pTesting)
    testImplementation(libs.io.mockk.mockk)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.coroutines.debug)
}
