// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
import com.adarshr.gradle.testlogger.theme.ThemeType
import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.GroupingEntityType
import kotlinx.kover.gradle.plugin.dsl.MetricType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    idea
    kotlin("jvm")
    kotlin("plugin.serialization")

    org.jetbrains.kotlinx.kover
    org.jlleitschuh.gradle.ktlint
    com.adarshr.`test-logger`
}

group = "org.erwinkok.libp2p"
version = "0.2.0"

ktlint {
    verbose.set(true)
    outputToConsole.set(true)
    coloredOutput.set(true)
    reporters {
        reporter(ReporterType.CHECKSTYLE)
        reporter(ReporterType.HTML)
    }
    filter {
        exclude("**/style-violations.kt")
    }
}

tasks.test {
    useJUnitPlatform()
}

testlogger {
    theme = ThemeType.MOCHA
}

kover {
    useKoverTool()
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

    html {
        onCheck = true
    }

    verify {
        onCheck = true
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

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = JavaVersion.VERSION_17.toString()
    targetCompatibility = JavaVersion.VERSION_17.toString()
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
