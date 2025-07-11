// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
import com.adarshr.gradle.testlogger.theme.ThemeType
import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.GroupingEntityType
import kotlinx.kover.gradle.plugin.dsl.MetricType

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    idea
    kotlin("jvm")
    kotlin("plugin.serialization")

    org.jetbrains.kotlinx.kover
//    org.jlleitschuh.gradle.ktlint
    com.adarshr.`test-logger`
}

group = "org.erwinkok.libp2p"
version = "0.2.0"

//ktlint {
//    verbose.set(true)
//    outputToConsole.set(true)
//    coloredOutput.set(true)
//    reporters {
//        reporter(ReporterType.CHECKSTYLE)
//        reporter(ReporterType.HTML)
//    }
//    filter {
//        exclude("**/build/**")
//    }
//}

tasks.test {
    useJUnitPlatform()
}

testlogger {
    theme = ThemeType.MOCHA
}

kover {
    excludeInstrumentation {
        classes("org.erwinkok.libp2p.*.pb.*")
    }
}

koverReport {
    filters {
        excludes {
            classes("org.erwinkok.libp2p.*.pb.*")
        }
        includes {
            classes("org.erwinkok.libp2p.*")
        }
    }

    defaults {
        html {
            onCheck = true
        }
    }

    verify {
        rule {
            isEnabled = true
            entity = GroupingEntityType.APPLICATION
            bound {
                minValue = 0
                maxValue = 99
                metric = MetricType.LINE
                aggregation = AggregationType.COVERED_PERCENTAGE
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
