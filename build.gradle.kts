import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.8.0"
    application
    id("com.github.johnrengelman.shadow") version "7.1.1"
    id("maven-publish")
}

group = "com.blakesmods"
version = "1.0.4"

repositories {
    mavenCentral()
    maven("https://maven.reposilite.com/releases")
}

dependencies {
    compileOnly("com.reposilite:reposilite:3.5.19")

    implementation("org.litote.kmongo:kmongo:4.8.0")
}

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set("com.blakesmods.plugin.watcher.WatcherPlugin")
}

tasks.withType<ShadowJar> {
    archiveFileName.set("reposilite-plugin-watcher-$version.jar")
    destinationDirectory.set(file("$rootDir/output"))
    mergeServiceFiles()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(tasks["shadowJar"])
        }
    }

    repositories {
        maven {
            url = uri("https://maven.blakesmods.com")

            credentials {
                username = System.getenv("BLAKESMODS_MAVEN_USERNAME")
                password = System.getenv("BLAKESMODS_MAVEN_PASSWORD")
            }
        }
    }
}