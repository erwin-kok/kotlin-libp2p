// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
import com.google.protobuf.gradle.id

plugins {
    id("libp2p.common")
    id("com.google.protobuf")
}

sourceSets {
    main {
        java {
            srcDirs("src/main/kotlin", "build/generated/source/proto/main/java")
        }
    }
}

val protobufVersion = "4.31.1"

dependencies {
    implementation("com.google.protobuf:protobuf-kotlin:$protobufVersion")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    generateProtoTasks {
        all().forEach {
            it.builtins {
                id("kotlin")
            }
        }
    }
}
