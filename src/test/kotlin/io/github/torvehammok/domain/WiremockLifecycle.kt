package io.github.torvehammok.domain

import com.github.tomakehurst.wiremock.WireMockServer
import io.github.ktor_batterypack.core.di.InitCallback
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Singleton
import org.slf4j.LoggerFactory
import java.lang.AutoCloseable

private val log = LoggerFactory.getLogger(WiremockLifecycle::class.java)

@Singleton
class WiremockLifecycle(@Provided private val wiremockServer: WireMockServer): InitCallback, AutoCloseable {

    override fun onInit() {
        wiremockServer.start()
        log.info("Starting wiremock server at port={}", wiremockServer.port())
    }

    override fun close() {
        log.info("Closing wiremock server at port={}", wiremockServer.port())
        wiremockServer.shutdown()
    }

}