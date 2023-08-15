// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
plugins {
    id("libp2p.application")
}

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

    implementation(libs.ipaddress)
    implementation(libs.jedis)
    implementation(libs.klaxon)
    implementation(libs.kotlin.logging)
    implementation(libs.kotlinx.atomicfu)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.network)
    implementation(libs.multiformat)
    implementation(libs.result.monad)
    implementation(libs.slf4j.api)

    runtimeOnly(libs.logback.classic)
}
