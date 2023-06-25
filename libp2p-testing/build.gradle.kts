// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
plugins {
    `java-test-fixtures`
    id("libp2p.library")
    id("libp2p.publish")
}

group = "org.erwinkok.libp2p"
version = "0.1.0-SNAPSHOT"

dependencies {
    implementation(platform(kotlin("bom")))
    implementation(kotlin("stdlib"))

    implementation(testFixtures(libs.result.monad))
    implementation(projects.libp2pCore)
    implementation(libs.io.mockk.mockk)
    implementation(libs.junit.jupiter.api)
    implementation(libs.junit.jupiter.params)
    implementation(libs.kotlinx.coroutines.test)
    implementation(libs.kotlinx.coroutines.debug)
    implementation(libs.ktor.network)
    implementation(libs.multiformat)
}
