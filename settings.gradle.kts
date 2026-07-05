rootProject.name = "opencode-agents"

pluginManagement {
    repositories {
        maven {
            name = "GitHubPackages"
            url =  uri("https://maven.pkg.github.com/kamil-perczynski/ktor-batterypack")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GH_USERNAME")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GH_TOKEN")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("ktorLibs").from("io.ktor:ktor-version-catalog:3.4.0")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
