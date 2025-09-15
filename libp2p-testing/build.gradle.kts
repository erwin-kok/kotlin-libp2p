// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
plugins {
    `java-test-fixtures`
    id("libp2p.library")
    id("libp2p.publish")
}

dependencies {
    implementation(platform(kotlin("bom")))
    implementation(kotlin("stdlib"))

    implementation(testFixtures(libs.result.monad))
    implementation(projects.libp2pCore)
    implementation(projects.libp2pCrypto)
    implementation(libs.io.mockk.mockk)
    implementation(libs.ipaddress)
    implementation(libs.junit.jupiter.api)
    implementation(libs.junit.jupiter.params)
    implementation(libs.kotlin.logging)
    implementation(libs.kotlinx.coroutines.test)
    implementation(libs.kotlinx.coroutines.debug)
    implementation(libs.ktor.network)
    implementation(libs.multiformat)
    testRuntimeOnly(libs.logback.classic)
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
