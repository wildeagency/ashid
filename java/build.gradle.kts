plugins {
    `java-library`
    `maven-publish`
    signing
}

group = "agency.wilde"
version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("Ashid")
                description.set("Time-sortable, double-click-selectable unique identifiers using Crockford Base32")
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
                    url.set("https://github.com/wildeagency/ashid/tree/main/java")
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
    val signingKey = System.getenv("GPG_SIGNING_KEY")
    val signingPassword = System.getenv("GPG_SIGNING_PASSWORD")
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["maven"])
    }
}
