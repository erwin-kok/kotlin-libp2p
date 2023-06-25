// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
plugins {
    id("libp2p.application")
}

group = "org.erwinkok.libp2p.app"
version = "0.1.0-SNAPSHOT"

application {
    mainClass.set("org.erwinkok.libp2p.app.ApplicationKt")
}

dependencies {
    implementation(platform(kotlin("bom")))
    implementation(kotlin("stdlib"))

    implementation(projects.libp2pCore)
    implementation(projects.libp2pCrypto)
    implementation(projects.libp2pMuxerMplex)
    implementation(projects.libp2pSecurityNoise)
    implementation(projects.libp2pTransportTcp)

    implementation(libs.slf4j.api)
    implementation(libs.kotlin.logging)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.result.monad)
    implementation(libs.multiformat)

    implementation(libs.ipaddress)
    implementation(libs.ktor.network)

    implementation(libs.kotlinx.atomicfu)

    runtimeOnly(libs.logback.classic)

    testRuntimeOnly(libs.junit.jupiter.engine)
}
