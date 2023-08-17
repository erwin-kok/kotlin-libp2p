// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.buildscript.atomicfu)
    implementation(libs.buildscript.detekt)
    implementation(libs.buildscript.kotlin)
    implementation(libs.buildscript.kover)
    implementation(libs.buildscript.ktlint)
    implementation(libs.buildscript.protobuf)
    implementation(libs.buildscript.serialization)
    implementation(libs.buildscript.shadow)
    implementation(libs.buildscript.testlogger)
}
