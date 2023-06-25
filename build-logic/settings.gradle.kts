// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
