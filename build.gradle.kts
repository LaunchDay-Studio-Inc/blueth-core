plugins {
    `java-library`
    `maven-publish`
}

group = "online.blueth"
version = "0.1.0-SNAPSHOT"
description = "Shared core library for Blueth plugin ecosystem"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("net.milkbowl.vault:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.11.6")
    api("net.kyori:adventure-api:4.21.0")
    api("net.kyori:adventure-text-minimessage:4.21.0")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
