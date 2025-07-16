// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
plugins {
    id("libp2p.common")
    com.google.protobuf
}

sourceSets {
    main {
        java {
            srcDirs("src/main/kotlin", "build/generated/source/proto/main/java")
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.31.1"
    }
}
