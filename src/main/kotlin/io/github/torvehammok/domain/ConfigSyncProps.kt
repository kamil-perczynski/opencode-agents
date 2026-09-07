package io.github.torvehammok.domain

import com.fasterxml.jackson.annotation.JsonPropertyDescription

data class ConfigSyncProps(
    @param:JsonPropertyDescription("Enable configuration sync from Git repository")
    val enabled: Boolean = false,
    @param:JsonPropertyDescription("Git repository URL for configuration")
    val repoUrl: String = "",
    @param:JsonPropertyDescription("Git branch to sync")
    val branch: String = "main",
    @param:JsonPropertyDescription("Target directory for synced configuration")
    val targetDir: String = "/app/opencode-config",
    @param:JsonPropertyDescription("Sync interval in seconds")
    val intervalSeconds: Long = 300,
    @param:JsonPropertyDescription("SSH configuration for Git access")
    val ssh: SshProps = SshProps()
)

data class SshProps(
    @param:JsonPropertyDescription("SSH directory path")
    val sshDir: String = "opencode-data/.ssh",
    @param:JsonPropertyDescription("SSH private key path")
    val privateKeyPath: String = "opencode-data/.ssh/github",
    @param:JsonPropertyDescription("SSH known hosts file path")
    val knownHostsPath: String = "",
    @param:JsonPropertyDescription("Enable strict host key checking")
    val strictHostKeyChecking: Boolean = true
)
