package io.github.torvehammok.domain

data class ConfigSyncProps(
    val enabled: Boolean = false,
    val repoUrl: String = "",
    val branch: String = "main",
    val targetDir: String = "/app/opencode-config",
    val intervalSeconds: Long = 300,
    val ssh: SshProps = SshProps()
)

data class SshProps(
    val privateKeyPath: String = "",
    val knownHostsPath: String = "",
    val strictHostKeyChecking: Boolean = true
)
