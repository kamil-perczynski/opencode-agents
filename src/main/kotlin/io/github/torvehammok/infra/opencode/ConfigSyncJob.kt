package io.github.torvehammok.infra.opencode

import io.github.ktor_batterypack.core.di.InitCallback
import io.github.torvehammok.domain.ConfigSyncProps
import kotlinx.coroutines.*
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase
import org.eclipse.jgit.transport.sshd.SshdSessionFactory
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.PublicKey
import kotlin.time.Duration.Companion.seconds

private val log = LoggerFactory.getLogger(ConfigSyncJob::class.java)

class ConfigSyncJob(private val props: ConfigSyncProps) : AutoCloseable, InitCallback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("config-sync"))

    private val transportConfigCallback: TransportConfigCallback = buildTransportConfigCallback()

    override fun onInit() {
        if (!props.enabled) {
            log.info("Config sync is disabled")
            return
        }
        if (props.repoUrl.isBlank()) {
            log.warn("Config sync is enabled but repoUrl is empty")
            return
        }

        sync()
        startPeriodicSync()
    }

    fun sync() {
        val targetDir = Path.of(props.targetDir)

        if (Files.exists(targetDir.resolve(".git"))) {
            Git.open(targetDir.toFile()).use { git ->
                val result = git.pull()
                    .setTransportConfigCallback(transportConfigCallback)
                    .call()
                if (result.isSuccessful) {
                    if (result.mergeResult?.mergedCommits?.isNotEmpty() == true) {
                        log.info("Config sync pulled new commits from {}", props.repoUrl)
                    } else {
                        log.info("Config sync is up to date with {}", props.repoUrl)
                    }
                } else {
                    log.warn("Config sync pull from {} was not successful", props.repoUrl)
                }
            }
        } else {
            if (Files.exists(targetDir)) {
                Files.walk(targetDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach { Files.deleteIfExists(it) }
            }
            Files.createDirectories(targetDir)
            Git.cloneRepository()
                .setURI(props.repoUrl)
                .setBranch(props.branch)
                .setDirectory(targetDir.toFile())
                .setTransportConfigCallback(transportConfigCallback)
                .call()
                .use {
                    log.info("Config sync cloned {} branch {} into {}", props.repoUrl, props.branch, props.targetDir)
                }
        }
    }

    private fun startPeriodicSync() {
        scope.launch(CoroutineName("config-sync-loop")) {
            while (isActive) {
                delay(props.intervalSeconds.seconds)
                try {
                    sync()
                } catch (e: Exception) {
                    log.error("Config sync failed during periodic pull", e)
                }
            }
        }
    }

    override fun close() {
        scope.cancel()
        log.info("Config sync stopped")
    }

    private fun buildTransportConfigCallback(): TransportConfigCallback {
        if (props.ssh.privateKeyPath.isBlank()) {
            return TransportConfigCallback { }
        }

        val builder = SshdSessionFactoryBuilder()
            .setPreferredAuthentications("publickey")
            .setDefaultIdentities { listOf(Path.of(props.ssh.privateKeyPath)) }

        if (props.ssh.knownHostsPath.isNotBlank()) {
            builder.setDefaultKnownHostsFiles { listOf(Path.of(props.ssh.knownHostsPath)) }
        }

        if (!props.ssh.strictHostKeyChecking) {
            builder.setServerKeyDatabase { _, _ -> PermissiveServerKeyDatabase }
        }

        val factory: SshdSessionFactory = builder.build(null)

        return TransportConfigCallback { transport ->
            if (transport is SshTransport) {
                transport.sshSessionFactory = factory
            }
        }
    }
}

private object PermissiveServerKeyDatabase : ServerKeyDatabase {
    override fun lookup(
        connectAddress: String,
        remoteAddress: InetSocketAddress,
        config: ServerKeyDatabase.Configuration
    ): List<PublicKey> = emptyList()

    override fun accept(
        connectAddress: String,
        remoteAddress: InetSocketAddress,
        serverKey: PublicKey,
        config: ServerKeyDatabase.Configuration,
        provider: CredentialsProvider?
    ): Boolean = true
}