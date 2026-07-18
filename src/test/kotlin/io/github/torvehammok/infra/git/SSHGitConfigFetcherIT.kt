package io.github.torvehammok.infra.git

import io.github.torvehammok.domain.ConfigSyncProps
import io.github.torvehammok.domain.SshProps
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.URIish
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.MountableFile
import java.nio.file.Files
import java.nio.file.Path

class SSHGitConfigFetcherIT {

    @TempDir
    lateinit var tmpDir: Path

    companion object {

        val gitServer: GenericContainer<*> = GenericContainer("rockstorm/git-server:latest")
            .withExposedPorts(22)
            .withEnv("SSH_AUTH_METHODS", "publickey")

        @BeforeAll
        @JvmStatic
        fun setUp() {
            gitServer.start()
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            gitServer.stop()
        }

    }

    @Test
    fun `should clone and pull config via SSH`() {
        val keyPair = SshKeyGenerator.generateRsaKeyPair()
        val privateKey = tmpDir.resolve("id_rsa")
        val publicKey = tmpDir.resolve("id_rsa.pub")
        SshKeyGenerator.writeKeyPair(keyPair, privateKey, publicKey, "config-sync-it")

        gitServer.execInContainer("mkdir", "-p", "/home/git/.ssh")
        gitServer.execInContainer("chmod", "700", "/home/git/.ssh")
        gitServer.execInContainer("chown", "-R", "git:git", "/home/git/.ssh")
        gitServer.execInContainer("mkdir", "-p", "/srv/git")

        gitServer.copyFileToContainer(
            MountableFile.forHostPath(publicKey),
            "/home/git/.ssh/authorized_keys"
        )
        gitServer.execInContainer("chmod", "600", "/home/git/.ssh/authorized_keys")
        gitServer.execInContainer("chown", "git:git", "/home/git/.ssh/authorized_keys")

        gitServer.execInContainer("git", "init", "--bare", "/srv/git/opencode-config.git")
        gitServer.execInContainer("chown", "-R", "git:git", "/srv/git/opencode-config.git")

        val sourceDir = tmpDir.resolve("source")
        val targetDir = tmpDir.resolve("config")

        val props = ConfigSyncProps(
            enabled = true,
            repoUrl = "ssh://git@${gitServer.host}:${gitServer.getMappedPort(22)}/srv/git/opencode-config.git",
            branch = "main",
            targetDir = targetDir.toString(),
            intervalSeconds = 3600,
            ssh = SshProps(
                privateKeyPath = privateKey.toString(),
                strictHostKeyChecking = false
            )
        )

        val sshTransportCallback = SSHTransportConfigCallbackFactory(props.ssh).createSSHTransportCallback()

        createSourceRepoAndPush(sourceDir, sshTransportCallback)

        val job = SSHGitConfigFetcher(props, sshTransportCallback)

        job.gitClone()

        val clonedFile = targetDir.resolve("opencode.json")
        assertThat(clonedFile).exists()
        assertThat(Files.readString(clonedFile)).isEqualTo("""{"agents": []}""")

        updateSourceRepoAndPush(sourceDir, sshTransportCallback)

        job.gitPull()

        assertThat(Files.readString(clonedFile)).isEqualTo("""{"agents": [{"name": "test"}]}""")
    }

    private fun createSourceRepoAndPush(sourceDir: Path, sshTransportCallback: TransportConfigCallback) {
        Files.createDirectories(sourceDir)
        Git.init().setDirectory(sourceDir.toFile()).setInitialBranch("main").call().use { git ->
            Files.writeString(sourceDir.resolve("opencode.json"), """{"agents": []}""")
            git.add().addFilepattern(".").call()
            git.commit().setMessage("Initial commit").call()
            git.remoteAdd()
                .setName("origin")
                .setUri(URIish("ssh://git@${gitServer.host}:${gitServer.getMappedPort(22)}/srv/git/opencode-config.git"))
                .call()
            git.push()
                .setTransportConfigCallback(sshTransportCallback)
                .setRefSpecs(RefSpec("refs/heads/main:refs/heads/main"))
                .call()
        }
    }

    private fun updateSourceRepoAndPush(sourceDir: Path, sshTransportCallback: TransportConfigCallback) {
        Git.open(sourceDir.toFile()).use { git ->
            Files.writeString(sourceDir.resolve("opencode.json"), """{"agents": [{"name": "test"}]}""")
            git.add().addFilepattern(".").call()
            git.commit().setMessage("Add test agent").call()
            git.push()
                .setTransportConfigCallback(sshTransportCallback)
                .setRefSpecs(RefSpec("refs/heads/main:refs/heads/main"))
                .call()
        }
    }

}

