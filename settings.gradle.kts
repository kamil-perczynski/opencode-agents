rootProject.name = "opencode-agents"

pluginManagement {
    repositories {
        maven {
            name = "KtorBaterrypackMaven"
            url = uri("https://repo.repsy.io/ktor-baterrypack/maven")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven {
            name = "KtorBaterrypackMaven"
            url = uri("https://repo.repsy.io/ktor-baterrypack/maven")
        }
        mavenCentral()
    }
    versionCatalogs {
        create("ktorLibs").from("io.ktor:ktor-version-catalog:3.4.0")
        create("batterypackLibs").from("io.github.ktor_batterypack:ktor-batterypack-versions-catalog:0.0.13-alpha")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
