package io.github.torvehammok.domain

import com.github.tomakehurst.wiremock.WireMockServer
import io.github.ktor_batterypack.core.problemdetail.ProblemDetail
import io.github.torvehammok.domain.dto.OCThread
import io.github.torvehammok.domain.dto.OCThreadMessage
import kotlinx.coroutines.runBlocking
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.ktor.plugin.koin

class OCServerAgentTest : OpenCodeAgentsIT() {

    private val wiremock: WireMockServer = application.koin().get()
    private val ocServerAgent: OCServerAgent = application.koin().get()
    private val mockOCServerApi: MockOCServerApi = application.koin().get()

    @BeforeEach
    fun setUp() {
        wiremock.resetAll()
    }

    @Test
    fun testRunAgenticLoop(): Unit = runBlocking {
        // given:
        val question = """
            What is current weather in Warsaw? 
            Feel free to use some API to fetch the current weather like wettr
        """.trimIndent()

        // and: session id must match session id in capture_v2_ses_0ae090e0dffeD2gZw8cFr4DA6K.jsonl
        val sessionId = "ses_0ae090e0dffeD2gZw8cFr4DA6K"

        mockOCServerApi.mockCreateSession(
            responseDef = { res, json ->
                val session = someCreateSessionResponse().let { it.copy(data = it.data.copy(id = sessionId)) }
                res.withBody(json.writeValueAsString(session))
            }
        )
        mockOCServerApi.mockSessionSSE(
            responseDef = { res, _ ->
                res.withBody(sseEventsFromResource("./capture_v2_ses_0ae090e0dffeD2gZw8cFr4DA6K.jsonl"))
            }
        )
        mockOCServerApi.mockPrompt(sessionId)

        // when:

        val response = ocServerAgent.run(
            someOCThread().copy(
                messages = listOf(someOCThreadMessage().copy(text = question))
            )
        )

        // then:
        assertThat(response.response).isEqualTo(
            """
            ☀️ **Current Weather in Warsaw:**

            - 🌧️ **Condition:** Rain Shower
            - 🌡️ **Temperature:** +22°C
            - 💧 **Humidity:** 61%
            - 💨 **Wind:** ↘ 12 km/h

            Not the worst! A little rain to keep things fresh. 🌂

            👇 Need a multi-day forecast or any other city? Just ask!
            """.trimIndent()
        )
        assertThat(response.cost).isZero
        assertThat(response.toolsInvocations).isEqualTo(1)
        assertThat(response.duration.inWholeMilliseconds).isBetween(0, 1000)

        // and: agent and model are set on the session
        wiremock.verify(
            postRequestedFor(urlPathEqualTo("/api/session"))
                .withRequestBody(equalToJson("""{"agent":"slack-bot","model":{"providerID":"opencode","id":"big-pickle"}}"""))
        )
    }

    @Test
    fun testSessionExecutionFailed(): Unit = runBlocking {
        // given:
        val sessionId = nextSessionId()

        mockOCServerApi.mockCreateSession(
            responseDef = { res, json ->
                val session = someCreateSessionResponse().let { it.copy(data = it.data.copy(id = sessionId)) }
                res.withBody(json.writeValueAsString(session))
            }
        )
        mockOCServerApi.mockSessionSSE(
            responseDef = { res, _ ->
                res.withBody(
                    sseEvents(
                        """{"id":"evt_1","type":"server.connected","data":{}}""",
                        """{"id":"evt_2","type":"session.execution.started","data":{"sessionID":"$sessionId"}}""",
                        """{"id":"evt_3","type":"session.usage.updated","data":{"sessionID":"$sessionId","cost":0.0125}}""",
                        """{"id":"evt_4","type":"session.execution.failed","data":{"sessionID":"$sessionId","error":{"type":"ProviderError","message":"Rate limited"}}}"""
                    )
                )
            }
        )
        mockOCServerApi.mockPrompt(sessionId)

        // when:
        val response = ocServerAgent.run(someOCThread())

        // then:
        assertThat(response.response).isEqualTo("Oops! The agent has broken down. Rest assured - we will fix it. Just try again later.")
        assertThat(response.cost).isEqualByComparingTo("0.0125")
    }

    @Test
    fun testAgentSessionTimeout(): Unit = runBlocking {
        // given:
        val question = """
            What is current weather in Warsaw? 
            Feel free to use some API to fetch the current weather like wettr
        """.trimIndent()

        // and: session id must match session id in capture_v2_ses_0ae090e0dffeD2gZw8cFr4DA6K.jsonl
        val sessionId = "ses_0ae090e0dffeD2gZw8cFr4DA6K"

        mockOCServerApi.mockCreateSession(
            responseDef = { res, json ->
                val session = someCreateSessionResponse().let { it.copy(data = it.data.copy(id = sessionId)) }
                res.withBody(json.writeValueAsString(session))
            }
        )
        mockOCServerApi.mockSessionSSE(
            responseDef = { res, _ ->
                res.withBody(sseEventsFromResource("./capture_v2_ses_0ae090e0dffeD2gZw8cFr4DA6K.jsonl"))
                    .withChunkedDribbleDelay(10, 2000)
            }
        )
        mockOCServerApi.mockPrompt(sessionId)
        mockOCServerApi.mockInterrupt(sessionId)

        // when:
        val response = ocServerAgent.run(
            someOCThread().copy(
                messages = listOf(someOCThreadMessage().copy(text = question))
            )
        )

        // then:
        assertThat(response.response).isEqualTo("The agent did not manage to respond in time. Try again later.")
        assertThat(response.cost).isZero
        assertThat(response.duration.inWholeMilliseconds).isGreaterThanOrEqualTo(2000)
        wiremock.verify(postRequestedFor(urlPathEqualTo("/api/session/$sessionId/interrupt")))
    }

    @Test
    fun testDownstreamOpenCodeServerError(): Unit = runBlocking {
        val sessionId = nextSessionId()
        // given:
        val question = """
                What is current weather in Warsaw? 
                Feel free to use some API to fetch the current weather like wettr
            """.trimIndent()

        mockOCServerApi.mockCreateSession(
            responseDef = { res, json ->
                val session = someCreateSessionResponse().let { it.copy(data = it.data.copy(id = sessionId)) }
                res.withBody(json.writeValueAsString(session))
            }
        )

        mockOCServerApi.mockSessionSSE(
            responseDef = { res, json ->
                res
                    .withStatus(500)
                    .withBody(
                        json.writeValueAsString(
                            ProblemDetail(
                                title = "Internal Error",
                                status = 500,
                                detail = "Internal Error",
                                instance = "/sessino",
                            )
                        )
                    )
            }
        )

        mockOCServerApi.mockPrompt(
            sessionId = sessionId,
            responseDef = { res, json ->
                res
                    .withStatus(500)
                    .withBody(
                        json.writeValueAsString(
                            ProblemDetail(
                                title = "Internal Error",
                                status = 500,
                                detail = "Internal Error",
                                instance = "/sessino",
                            )
                        )
                    )
            }
        )

        // when:
        val response = ocServerAgent.run(
            someOCThread().copy(
                messages = listOf(someOCThreadMessage().copy(text = question))
            )
        )

        // then:
        assertThat(response.response).isEqualTo("Oops! The agent has broken down. Rest assured - we will fix it. Just try again later.")
        assertThat(response.cost).isZero
        assertThat(response.duration.inWholeMilliseconds).isBetween(0, 500)
    }
}

private fun sseEventsFromResource(resourceName: String): String {
    val fileContent = OCServerAgentTest::class.java.classLoader.getResourceAsStream(resourceName)!!
        .bufferedReader()
        .use { it.readText() }

    return sseEvents(*fileContent.lines().filter { it.isNotBlank() }.toTypedArray())
}

private val EVENT_TYPE = Regex(""""type"\s*:\s*"([^"]+)"""")

private fun sseEvents(vararg jsonLines: String): String = buildString {
    jsonLines.forEach { line ->
        append("event: ").append(EVENT_TYPE.find(line)!!.groupValues[1]).append("\n")
        append("data: ").append(line).append("\n\n")
    }
}

private fun someOCThread(): OCThread {
    return OCThread(
        channelId = "404981234",
        threadTs = "404981234",
        messages = listOf(
            someOCThreadMessage()
        )
    )
}

private fun someOCThreadMessage(): OCThreadMessage = OCThreadMessage(
    text = "What is current date?",
    ts = "32423",
    user = "@UKNADJSF",
    isBot = false
)
