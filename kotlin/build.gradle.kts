import java.util.Base64

plugins {
    kotlin("jvm") version "1.9.22"
    `java-library`
    `maven-publish`
    signing
}

group = "agency.wilde"
version = "1.1.0"

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
                url.set("https://github.com/wildeagency/ashid")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("dathan")
                        name.set("Dathan Guiley")
                        email.set("dathan@wilde.agency")
                        organization.set("Wilde Agency")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/wildeagency/ashid.git")
                    developerConnection.set("scm:git:ssh://github.com/wildeagency/ashid.git")
                    url.set("https://github.com/wildeagency/ashid")
                }
            }
        }
    }

    repositories {
        maven {
            name = "OSSRH"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
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
