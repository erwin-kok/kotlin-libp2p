// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    includeBuild("build-logic")
}

rootProject.name = "kotlin-libp2p"

include(":libp2p-core")
include(":libp2p-crypto")
include(":libp2p-datastore-rocksdb")
include(":libp2p-muxer-mplex")
include(":libp2p-security-noise")
include(":libp2p-security-plaintext")
include(":libp2p-testing")
include(":libp2p-transport-tcp")

include(":examples")

include(":test-plans")
