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
    val sshDir: String = "opencode-data/.ssh",
    val privateKeyPath: String = "opencode-data/.ssh/github",
    val knownHostsPath: String = "",
    val strictHostKeyChecking: Boolean = true
)
