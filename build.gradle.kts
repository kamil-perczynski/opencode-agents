plugins {
    kotlin("jvm") version "2.3.21"
    alias(libs.plugins.koin.compiler)
    application
}

group = "io.github.torvehammok"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/kamil-perczynski/ktor-batterypack")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
        }
    }
}

dependencies {
    // Source: https://mvnrepository.com/artifact/io.github.cdimascio/dotenv-kotlin
    implementation("io.github.cdimascio:dotenv-kotlin:6.5.1")
    implementation("com.slack.api:bolt:1.49.0")
    implementation("com.slack.api:bolt-jetty:1.49.0")
    implementation("org.glassfish.tyrus.bundles:tyrus-standalone-client:1.20")

    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.10.2")

    implementation("io.github.kperczynski:ktor-batterypack-core:0.0.1-alpha")
    implementation("io.github.kperczynski:ktor-batterypack-metrics:0.0.1-alpha")
    implementation("io.github.kperczynski:ktor-batterypack-redis:0.0.1-alpha")

    implementation(ktorLibs.serialization.jackson3)
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.statusPages)

    implementation(libs.lettuce.core)
    implementation(libs.hoplite.core)
    implementation(libs.hoplite.yaml)
    implementation(libs.koin.annotations)
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.logback.classic)
    implementation(libs.logstash.logback.encoder)
    implementation(libs.janino)

    implementation(ktorLibs.client.core)
    implementation(ktorLibs.client.cio)
    implementation(ktorLibs.client.contentNegotiation)
    implementation(ktorLibs.client.logging)

}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass.set("io.github.torvehammok.KtorMainKt")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "io.github.torvehammok.KtorMainKt"
    }
}

tasks.test {
    useJUnitPlatform()
}