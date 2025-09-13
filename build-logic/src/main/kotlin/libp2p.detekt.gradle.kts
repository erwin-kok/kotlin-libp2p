// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
plugins {
    id("io.gitlab.arturbosch.detekt")
}

detekt {
    config.setFrom("${project.rootDir}/detekt-config.yml")
}
