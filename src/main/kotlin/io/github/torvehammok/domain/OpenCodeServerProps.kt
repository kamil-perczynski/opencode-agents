package io.github.torvehammok.domain

import com.fasterxml.jackson.annotation.JsonPropertyDescription

data class OpenCodeServerProps(
    @param:JsonPropertyDescription("Enable OpenCode HTTP server")
    val enabled: Boolean = false,
    @param:JsonPropertyDescription("OpenCode server port")
    val port: Int = 12335,
    @param:JsonPropertyDescription("OpenCode server username")
    val username: String? = null,
    @param:JsonPropertyDescription("OpenCode server password")
    val password: String? = null,
    @param:JsonPropertyDescription("OpenCode server environment configuration")
    val env: OpenCodeServerEnvProps = OpenCodeServerEnvProps()
)

data class OpenCodeServerEnvProps(
    @param:JsonPropertyDescription("Custom environment variables")
    val custom: Map<String, String> = emptyMap(),
    @param:JsonPropertyDescription("Environment variables to pass from the parent process")
    val pass: List<String> = emptyList(),
    @param:JsonPropertyDescription("Pass all environment variables from the parent process")
    val passAll: Boolean = false
)