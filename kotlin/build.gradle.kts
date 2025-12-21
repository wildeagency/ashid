import java.util.Base64

plugins {
    kotlin("jvm") version "1.9.22"
    `java-library`
    `maven-publish`
    signing
}

group = "agency.wilde"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin standard library
    implementation(kotlin("stdlib"))

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:1.9.22")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8

    withJavadocJar()
    withSourcesJar()
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            artifactId = "ashid"

            pom {
                name.set("ashid")
                description.set("Ash IDs - Time-sortable, double-click-selectable unique identifiers using Crockford Base32")
                url.set("https://github.com/wilde-agency/ashid")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("wilde-agency")
                        name.set("Wilde Agency")
                        url.set("https://wilde.agency")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/wilde-agency/ashid.git")
                    developerConnection.set("scm:git:ssh://github.com/wilde-agency/ashid.git")
                    url.set("https://github.com/wilde-agency/ashid")
                }
            }
        }
    }

    repositories {
        maven {
            name = "Local"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}

signing {
    // Only sign if publishing to Maven Central
    val signingKeyBase64 = System.getenv("GPG_SIGNING_KEY")
    val signingPassword = System.getenv("GPG_SIGNING_PASSWORD") ?: ""
    if (!signingKeyBase64.isNullOrEmpty()) {
        val signingKey = String(Base64.getDecoder().decode(signingKeyBase64))
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["maven"])
    }
}
