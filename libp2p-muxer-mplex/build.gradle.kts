// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
plugins {
//    id("libp2p.detekt")
    id("libp2p.library")
    id("libp2p.publish")
}

dependencies {
    implementation(platform(kotlin("bom")))
    implementation(kotlin("stdlib"))

    implementation(projects.libp2pCore)

    implementation(libs.kotlin.logging)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.network)
    implementation(libs.multiformat)
    implementation(libs.result.monad)
    implementation(libs.kotlinx.atomicfu)

    testImplementation(testFixtures(libs.result.monad))
    testImplementation(projects.libp2pTesting)
    testImplementation(libs.io.mockk.mockk)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.coroutines.debug)
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
