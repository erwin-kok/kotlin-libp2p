// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
plugins {
    id("libp2p.common")
    signing
    `maven-publish`

//    alias(libs.plugins.build.nexus)
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<Javadoc> {
    exclude("org/erwinkok/libp2p/**/pb/**")
}

tasks.named<Jar>("sourcesJar") {
    exclude("org/erwinkok/libp2p/**/pb/**")
}
