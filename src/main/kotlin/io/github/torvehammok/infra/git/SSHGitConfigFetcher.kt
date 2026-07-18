package io.github.torvehammok.infra.git

import io.github.ktor_batterypack.core.exception.ErrorCodeException
import io.github.torvehammok.domain.ConfigSyncProps
import io.github.torvehammok.infra.git.GitConfigSyncErrorCode.GIT_CLONE_TARGET_DIR_EXISTS
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.lib.ObjectId
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Singleton
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.time.OffsetDateTime
import kotlin.time.measureTime

private val log = LoggerFactory.getLogger(SSHGitConfigFetcher::class.java)

@Singleton
class SSHGitConfigFetcher(
    private val props: ConfigSyncProps,
    @Provided private val sshTransportCallback: TransportConfigCallback
) {

    fun gitClone() {
        log.info("Cloning repo={}:{} into dir={}", props.repoUrl, props.branch, props.targetDir)

        val dir = Paths.get(props.targetDir)

        if (Files.exists(dir)) {
            throw ErrorCodeException(GIT_CLONE_TARGET_DIR_EXISTS)
        }

        Files.createDirectories(dir.parent)

        val measurement = measureTime {
            Git.cloneRepository()
                .setURI(props.repoUrl)
                .setBranch(props.branch)
                .setShallowSince(OffsetDateTime.now().minusMonths(1))
                .setDirectory(dir.toFile())
                .setTransportConfigCallback(sshTransportCallback)
                .call()
        }

        log.info(
            "Config sync cloned {} branch {} into {} in {}s",
            props.repoUrl,
            props.branch,
            props.targetDir,
            measurement.inWholeSeconds
        )
    }

    fun gitPull() {
        val dir = Paths.get(props.targetDir)

        Git.open(dir.toFile()).use { git ->
            val previousHead = git.repository.resolve("HEAD")

            val pullResult = git.pull()
                .setTransportConfigCallback(sshTransportCallback)
                .call()

            val nextHead = git.repository.resolve("HEAD")

            val commitsCount = countPulledCommits(previousHead, nextHead, git)
            log.info("Successfully pulled {} new commits", commitsCount)
            pullResult
        }
    }


}

private fun countPulledCommits(oldHead: ObjectId?, newHead: ObjectId?, git: Git): Int {
    if (oldHead != null && newHead != null && oldHead != newHead) {
        return git.log().addRange(oldHead, newHead).call().count()
    }

    if (oldHead == null && newHead != null) {
        return git.log().add(newHead).call().count()
    }

    return 0
}

