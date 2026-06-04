plugins {
    kotlin("jvm") version "2.3.21"
}

group = "io.github.torvehammok"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Source: https://mvnrepository.com/artifact/io.github.cdimascio/dotenv-kotlin
    implementation("io.github.cdimascio:dotenv-kotlin:6.5.1")
    implementation("com.slack.api:bolt-socket-mode:1.49.0")
    implementation("javax.websocket:javax.websocket-api:1.1")
    implementation("org.glassfish.tyrus.bundles:tyrus-standalone-client:1.20")
    implementation("ch.qos.logback:logback-classic:1.5.18")

    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("tools.jackson.core:jackson-core:3.1.4")
    implementation("tools.jackson.core:jackson-databind:3.1.4")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.21")
    implementation("tools.jackson.module:jackson-module-kotlin:3.1.4")
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}