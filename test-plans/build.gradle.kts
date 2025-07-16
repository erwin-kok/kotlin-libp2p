// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("libp2p.shadow")
}

application {
    mainClass.set("org.erwinkok.libp2p.testplans.ping.PingKt")
}

tasks.withType<ShadowJar> {
    archiveBaseName.set("test-plans-shadow")
    archiveClassifier.set("")
    archiveVersion.set("")
    manifest {
        attributes["Main-Class"] = "org.erwinkok.libp2p.testplans.ping.PingKt"
    }
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

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
