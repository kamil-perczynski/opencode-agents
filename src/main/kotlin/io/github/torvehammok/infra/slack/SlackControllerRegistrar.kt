package io.github.torvehammok.infra.slack

import com.slack.api.bolt.App
import io.github.ktor_batterypack.core.di.InitCallback
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(SlackControllerRegistrar::class.java)

class SlackControllerRegistrar(private val app: App, private val controllers: List<SlackController>) : InitCallback {

    override fun onInit() {
        for (controller in controllers) {
            log.info("Registering Slack controller: ${controller::class.java.simpleName}")
            controller.register(app)
        }
    }

}
