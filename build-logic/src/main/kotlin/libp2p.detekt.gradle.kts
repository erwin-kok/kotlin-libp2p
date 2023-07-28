// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    io.gitlab.arturbosch.detekt
}

detekt {
    config.setFrom("${project.rootDir}/detekt-config.yml")
}
