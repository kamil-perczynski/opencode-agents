package io.github.torvehammok

import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>) {
    dotenv {
        ignoreIfMissing = true
        systemProperties = true
    }

    EngineMain.main(args)
}
