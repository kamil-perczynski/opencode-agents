package io.github.torvehammok.infra.git

import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair

object SshKeyGenerator {

    fun generateRsaKeyPair(): KeyPair {
        return KeyUtils.generateKeyPair("ssh-rsa", 2048)
    }

    fun writeKeyPair(keyPair: KeyPair, privateKeyPath: Path, publicKeyPath: Path, comment: String) {
        Files.newOutputStream(privateKeyPath).use { out ->
            OpenSSHKeyPairResourceWriter.INSTANCE.writePrivateKey(keyPair, comment, null, out)
        }
        Files.newOutputStream(publicKeyPath).use { out ->
            OpenSSHKeyPairResourceWriter.INSTANCE.writePublicKey(keyPair.public, comment, out)
        }
    }
}