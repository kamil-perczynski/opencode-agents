package io.github.torvehammok.infra.git

import io.github.torvehammok.domain.SshProps
import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase
import org.eclipse.jgit.transport.sshd.SshdSessionFactory
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder
import java.net.InetSocketAddress
import java.nio.file.Path
import java.nio.file.Paths
import java.security.PublicKey

class SSHTransportConfigCallbackFactory(private val props: SshProps) {

    fun createSSHTransportCallback(): TransportConfigCallback {
        if (props.privateKeyPath.isBlank()) {
            return TransportConfigCallback { }
        }

        val homeDir = Path.of(System.getProperty("user.home", "/tmp")).toFile()

        val builder = SshdSessionFactoryBuilder()
            .setHomeDirectory(homeDir)
            .setSshDirectory(Paths.get(props.sshDir).toFile())
            .setPreferredAuthentications("publickey")
            .setDefaultIdentities { listOf(Path.of(props.privateKeyPath)) }

        if (props.knownHostsPath.isNotBlank()) {
            builder.setDefaultKnownHostsFiles { listOf(Path.of(props.knownHostsPath)) }
        }

        if (!props.strictHostKeyChecking) {
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

internal object PermissiveServerKeyDatabase : ServerKeyDatabase {
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