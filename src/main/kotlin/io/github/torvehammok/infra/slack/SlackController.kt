package io.github.torvehammok.infra.slack

import com.slack.api.bolt.App

interface SlackController {

    fun register(app: App)

}