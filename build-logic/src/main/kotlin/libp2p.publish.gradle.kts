// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
plugins {
    `java-library`
    `maven-publish`
    signing
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

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set(project.name)
                description.set("libp2p implementation in Kotlin")
                inceptionYear.set("2023")
                url.set("https://github.com/erwin-kok/kotlin-libp2p")
                licenses {
                    license {
                        name.set("BSD-3-Clause")
                        url.set("https://opensource.org/licenses/BSD-3-Clause")
                    }
                }
                developers {
                    developer {
                        id.set("erwin-kok")
                        name.set("Erwin Kok")
                        email.set("erwin-kok@gmx.com")
                        url.set("https://github.com/erwin-kok/")
                        roles.set(listOf("owner", "developer"))
                    }
                }
                scm {
                    url.set("https://github.com/erwin-kok/kotlin-libp2p")
                    connection.set("scm:git:https://github.com/erwin-kok/kotlin-libp2p")
                    developerConnection.set("scm:git:ssh://git@github.com:erwin-kok/kotlin-libp2p.git")
                }
                issueManagement {
                    system.set("GitHub")
                    url.set("https://github.com/erwin-kok/kotlin-libp2p/issues")
                }
            }
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}
