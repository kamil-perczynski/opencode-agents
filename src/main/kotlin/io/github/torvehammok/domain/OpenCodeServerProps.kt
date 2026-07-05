package io.github.torvehammok.domain

data class OpenCodeServerProps(
    val enabled: Boolean = false,
    val port: Int = 12335,
    val username: String? = null,
    val password: String? = null,
    val env: OpenCodeServerEnvProps = OpenCodeServerEnvProps()
)

data class OpenCodeServerEnvProps(
    val custom: Map<String, String> = emptyMap(),
    val pass: List<String> = emptyList(),
    val passAll: Boolean = false
)