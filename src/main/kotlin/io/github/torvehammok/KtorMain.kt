package io.github.torvehammok

import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.netty.EngineMain
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

fun main(args: Array<String>) {
    Security.addProvider(BouncyCastleProvider())
    
    dotenv {
        ignoreIfMissing = true
        systemProperties = true
    }

    EngineMain.main(args)
}
