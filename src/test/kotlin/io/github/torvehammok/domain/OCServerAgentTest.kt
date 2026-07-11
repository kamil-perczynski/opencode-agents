package io.github.torvehammok.domain

import com.github.tomakehurst.wiremock.WireMockServer
import io.github.ktor_batterypack.core.problemdetail.ProblemDetail
import io.github.torvehammok.domain.dto.OCThread
import io.github.torvehammok.domain.dto.OCThreadMessage
import kotlinx.coroutines.runBlocking
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

        // and: session id must match session id in capture_ses_0ae090e0dffeD2gZw8cFr4DA6K.jsonl
        val sessionId = "ses_0ae090e0dffeD2gZw8cFr4DA6K"

        mockOCServerApi.mockCreateSession(
            responseDef = { res, json ->
                val session = someCreateSessionRequest().copy(id = sessionId)
                res.withBody(json.writeValueAsString(session))
            }
        )
        mockOCServerApi.mockSessionSSE(
            responseDef = { res, _ ->
                res.withBody(sseEventsFromResource("./capture_ses_0ae090e0dffeD2gZw8cFr4DA6K.jsonl"))
            }
        )
        mockOCServerApi.mockPromptAsync(sessionId)

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
        assertThat(response.duration.inWholeMilliseconds).isBetween(0, 1000)
    }

    @Test
    fun testAgentSessionTimeout(): Unit = runBlocking {
        // given:
        val question = """
            What is current weather in Warsaw? 
            Feel free to use some API to fetch the current weather like wettr
        """.trimIndent()

        // and: session id must match session id in capture_ses_0ae090e0dffeD2gZw8cFr4DA6K.jsonl
        val sessionId = "ses_0ae090e0dffeD2gZw8cFr4DA6K"

        mockOCServerApi.mockCreateSession(
            responseDef = { res, json ->
                val session = someCreateSessionRequest().copy(id = sessionId)
                res.withBody(json.writeValueAsString(session))
            }
        )
        mockOCServerApi.mockSessionSSE(
            responseDef = { res, _ ->
                res.withBody(sseEventsFromResource("./capture_ses_0ae090e0dffeD2gZw8cFr4DA6K.jsonl"))
                    .withChunkedDribbleDelay(10, 2000)
            }
        )
        mockOCServerApi.mockPromptAsync(sessionId)

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
                val session = someCreateSessionRequest().copy(id = sessionId)
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

        mockOCServerApi.mockPromptAsync(
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

    return buildString {
        // Break the file into lines and prepend "data: " to each
        fileContent.lines().filter { it.isNotBlank() }.forEach { line ->
            append("data: ").append(line).append("\n").append("\n")
        }
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
