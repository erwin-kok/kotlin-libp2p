// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
plugins {
    id("libp2p.library")
    id("libp2p.protoc")
    id("libp2p.publish")
}

dependencies {
    implementation(platform(kotlin("bom")))
    implementation(kotlin("stdlib"))

    implementation(libs.kotlin.logging)
    implementation(libs.kerby.asn1)
    implementation(libs.multiformat)
    implementation(libs.result.monad)

    testImplementation(testFixtures(libs.result.monad))
    testImplementation(projects.libp2pTesting)
    testImplementation(libs.io.mockk.mockk)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.klaxon)
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    api(libs.protobuf.java)
}
