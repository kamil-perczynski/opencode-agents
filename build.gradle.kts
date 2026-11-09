plugins {
    alias(batterypackLibs.plugins.kotlin.jvm)
    alias(batterypackLibs.plugins.koin.compiler)
    alias(batterypackLibs.plugins.ktor.batterypack)
}

group = "io.github.torvehammok"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(batterypackLibs.dotenv.kotlin)
    ksp(batterypackLibs.ktor.batterypack.validation.ksp)

    implementation("org.eclipse.jgit:org.eclipse.jgit:7.8.1.202611041320-r")
    implementation("org.eclipse.jgit:org.eclipse.jgit.ssh.apache:7.8.1.202611041320-r")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.86")
    implementation("com.slack.api:bolt:1.54.0")
    implementation("com.slack.api:bolt-jetty:1.54.0")
    implementation("org.glassfish.tyrus.bundles:tyrus-standalone-client:1.22")

    implementation(batterypackLibs.kotlinx.coroutines.core)
    implementation(batterypackLibs.kotlinx.coroutines.reactive)
    implementation(batterypackLibs.exposed.jdbc)
    implementation(batterypackLibs.jakarta.annotation.api)
    implementation(batterypackLibs.jakarta.validation.api)
    implementation(batterypackLibs.ktor.batterypack.core)
    implementation(batterypackLibs.ktor.batterypack.annotations)
    implementation(batterypackLibs.ktor.batterypack.validation)
    implementation(batterypackLibs.ktor.batterypack.metrics)
    implementation(batterypackLibs.ktor.batterypack.redis)
    implementation(batterypackLibs.logstash.logback.encoder)
    implementation(libs.janino)

    testImplementation(kotlin("test"))
    testImplementation(platform(batterypackLibs.junit.bom))
    testImplementation(batterypackLibs.ktor.batterypack.redis.testing)
    testImplementation(batterypackLibs.junit.jupiter)
    testImplementation(batterypackLibs.assertj.core)
    testImplementation(batterypackLibs.testcontainers)
    testImplementation(batterypackLibs.wiremock)
    testImplementation(ktorLibs.server.testHost)
    testImplementation(ktorLibs.client.mock)
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
    // opencode exports OPENCODE=1 to its shells; the env-first config loader would read it as the whole `opencode:` section
    environment.remove("OPENCODE")
}

ktorBatterypack {
    mainClass = "io.github.torvehammok.KtorMainKt"
    configMetadataClass = "io.github.torvehammok.infra.ConfigMap"
}

tasks.compileKotlin {
    compilerOptions.freeCompilerArgs.set(listOf("-Xannotation-default-target=param-property"))
}