package io.github.torvehammok.infra.opencode

import io.github.torvehammok.domain.ConfigSyncProps
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.RefSpec
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ConfigSyncJobTest {

    @Test
    fun `should clone a bare repository via file protocol`(@TempDir tmpDir: Path) {
        val (sourceDir, bareDir) = createBareRepoWithSingleFile(tmpDir, "opencode.json", """{"agents": []}""")
        val cloneDir = tmpDir.resolve("clone")

        val job = ConfigSyncJob(
            ConfigSyncProps(
                enabled = true,
                repoUrl = bareDir.toAbsolutePath().toUri().toString(),
                branch = "main",
                targetDir = cloneDir.toString(),
                intervalSeconds = 3600
            )
        )

        job.onInit()

        val clonedFile = cloneDir.resolve("opencode.json")
        assertThat(clonedFile).exists()
        assertThat(Files.readString(clonedFile)).isEqualTo("""{"agents": []}""")

        job.close()
    }

    @Test
    fun `should pull new commits on subsequent sync`(@TempDir tmpDir: Path) {
        val (sourceDir, bareDir) = createBareRepoWithSingleFile(tmpDir, "opencode.json", """{"version": 1}""")
        val cloneDir = tmpDir.resolve("clone")

        val job = ConfigSyncJob(
            ConfigSyncProps(
                enabled = true,
                repoUrl = bareDir.toAbsolutePath().toUri().toString(),
                branch = "main",
                targetDir = cloneDir.toString(),
                intervalSeconds = 3600
            )
        )

        job.onInit()

        assertThat(Files.readString(cloneDir.resolve("opencode.json"))).isEqualTo("""{"version": 1}""")

        commitNewFile(sourceDir, "opencode.json", """{"version": 2}""", "Update version")
        pushToOrigin(sourceDir)

        job.sync()

        assertThat(Files.readString(cloneDir.resolve("opencode.json"))).isEqualTo("""{"version": 2}""")

        job.close()
    }

    @Test
    fun `should be a no-op when disabled`(@TempDir tmpDir: Path) {
        val cloneDir = tmpDir.resolve("clone")

        val job = ConfigSyncJob(
            ConfigSyncProps(
                enabled = false,
                repoUrl = "file:///nonexistent",
                targetDir = cloneDir.toString()
            )
        )

        job.onInit()

        assertThat(cloneDir).doesNotExist()

        job.close()
    }

    @Test
    fun `should stop the periodic sync loop on close`(@TempDir tmpDir: Path) {
        val (_, bareDir) = createBareRepoWithSingleFile(tmpDir, "opencode.json", """{"agents": []}""")
        val cloneDir = tmpDir.resolve("clone")

        val job = ConfigSyncJob(
            ConfigSyncProps(
                enabled = true,
                repoUrl = bareDir.toAbsolutePath().toUri().toString(),
                branch = "main",
                targetDir = cloneDir.toString(),
                intervalSeconds = 1
            )
        )

        job.onInit()
        job.close()

        assertThat(cloneDir.resolve("opencode.json")).exists()
    }

    private fun createBareRepoWithSingleFile(tmpDir: Path, filename: String, content: String): Pair<Path, Path> {
        val sourceDir = tmpDir.resolve("source-${System.nanoTime()}")
        val bareDir = tmpDir.resolve("bare-${System.nanoTime()}.git")

        Files.createDirectories(sourceDir)
        Git.init().setBare(true).setDirectory(bareDir.toFile()).setInitialBranch("main").call().use { }

        Git.init().setDirectory(sourceDir.toFile()).setInitialBranch("main").call().use { source ->
            Files.writeString(sourceDir.resolve(filename), content)
            source.add().addFilepattern(".").call()
            source.commit().setMessage("Initial commit").call()
            source.remoteAdd()
                .setName("origin")
                .setUri(org.eclipse.jgit.transport.URIish(bareDir.toAbsolutePath().toUri().toString()))
                .call()
            source.push().call()
        }

        return sourceDir to bareDir
    }

    private fun commitNewFile(repoDir: Path, filename: String, content: String, message: String) {
        Git.open(repoDir.toFile()).use { git ->
            Files.writeString(repoDir.resolve(filename), content)
            git.add().addFilepattern(".").call()
            git.commit().setMessage(message).call()
        }
    }

    private fun pushToOrigin(repoDir: Path) {
        Git.open(repoDir.toFile()).use { git ->
            git.push()
                .setRemote("origin")
                .setRefSpecs(RefSpec("refs/heads/main:refs/heads/main"))
                .call()
        }
    }
}