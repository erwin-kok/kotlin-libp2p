// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
import com.adarshr.gradle.testlogger.theme.ThemeType

plugins {
    idea
    kotlin("jvm")
    kotlin("plugin.serialization")

    id("org.jetbrains.kotlinx.kover")
    id("org.jlleitschuh.gradle.ktlint")
    id("com.adarshr.test-logger")
}

group = "org.erwinkok.libp2p"
version = "0.2.0"

tasks.test {
    useJUnitPlatform()
}

testlogger {
    theme = ThemeType.MOCHA
}

ktlint {
    filter {
        exclude("**/pb/**")
    }
}

kover {
    reports {
        filters {
            excludes {
                classes("org.erwinkok.libp2p.*.pb.*")
            }
        }

        verify {
            rule {
                bound {
                    minValue.set(0)
                }
            }
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }
    maven {
        url = uri("https://jcenter.bintray.com")
    }
    maven {
        url = uri("https://jitpack.io")
    }
}
