package io.github.torvehammok.infra.opencode

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.github.torvehammok.domain.MockOCServerApi
import io.github.torvehammok.domain.OpenCodeAgentsIT
import io.github.torvehammok.infra.opencode.model.OCCreateSessionRequest
import io.github.torvehammok.infra.opencode.model.OCEvent
import io.github.torvehammok.infra.opencode.model.OCEventData
import io.github.torvehammok.infra.opencode.model.OCModelRef
import io.github.torvehammok.infra.opencode.model.OCPromptRequest
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.ktor.plugin.koin
import tools.jackson.databind.json.JsonMapper
import kotlin.time.Duration.Companion.seconds

class OpenCodeClientTest : OpenCodeAgentsIT() {

    private val wiremock: WireMockServer = application.koin().get()
    private val openCodeClient: OpenCodeClient = application.koin().get()
    private val mockOCServerApi: MockOCServerApi = application.koin().get()
    private val jsonMapper: JsonMapper = application.koin().get()

    @BeforeEach
    fun setUp() {
        wiremock.resetAll()
    }

    @Test
    fun testCreateSessionReturnsSessionId(): Unit = runBlocking {
        // given:
        val expectedSessionId = "ses_test_123"
        mockOCServerApi.mockCreateSession(
            responseDef = { res, json ->
                res.withBody(
                    json.writeValueAsString(
                        mapOf(
                            "data" to mapOf(
                                "id" to expectedSessionId,
                                "projectID" to "proj_test",
                                "agent" to "test-agent",
                                "model" to mapOf("id" to "gpt-4", "providerID" to "openai"),
                                "cost" to 0.0,
                                "title" to "Test Session"
                            )
                        )
                    )
                )
            }
        )

        // when:
        val sessionId = openCodeClient.createSession(agent = "test-agent", modelId = "openrouter/openai/gpt-4")

        // then:
        assertThat(sessionId).isEqualTo(expectedSessionId)

        // and: agent and model are set on the session, model id keeps everything after the provider
        val requests = mockOCServerApi.findRequests<OCCreateSessionRequest>(urlEqualTo("/api/session"))

        assertThat(requests).containsExactly(
            OCCreateSessionRequest(
                agent = "test-agent",
                model = OCModelRef(providerID = "openrouter", id = "openai/gpt-4")
            )
        )
    }

    @Test
    fun testCreateSessionThrowsOnFailure(): Unit = runBlocking {
        // given:
        mockOCServerApi.mockCreateSession(
            responseDef = { res, _ ->
                res.withStatus(500).withBody("Internal Server Error")
            }
        )

        // expect:
        val thrown = catchThrowable { runBlocking { openCodeClient.createSession(agent = "test-agent", modelId = "openai/gpt-4") } }
        assertThat(thrown)
            .isInstanceOf(OpenCodeClientException::class.java)
            .hasMessage("Failed to open new session")
    }

    @Test
    fun testPromptSendsText(): Unit = runBlocking {
        // given:
        val sessionId = "ses_prompt_123"
        mockOCServerApi.mockPrompt(sessionId)

        // when:
        openCodeClient.prompt(sessionId = sessionId, prompt = "Hello")

        // then:
        val requests = mockOCServerApi.findRequests<OCPromptRequest>(
            urlEqualTo("/api/session/$sessionId/prompt")
        )

        assertThat(requests).containsExactly(OCPromptRequest(text = "Hello"))
    }

    @Test
    fun testPromptThrowsOnFailure(): Unit = runBlocking {
        // given:
        val sessionId = "ses_prompt_fail"
        mockOCServerApi.mockPrompt(
            sessionId = sessionId,
            responseDef = { res, _ ->
                res.withStatus(503).withBody("Service Unavailable")
            }
        )

        // expect:
        val thrown = catchThrowable {
            runBlocking {
                openCodeClient.prompt(sessionId = sessionId, prompt = "Hello")
            }
        }

        assertThat(thrown)
            .isInstanceOf(OpenCodeClientException::class.java)
            .hasMessage("Failed to prompt session")
    }

    @Test
    fun testSubscribeSessionEventsFiltersBySessionId(): Unit = runBlocking {
        // given:
        val targetSessionId = "ses_target"
        val otherSessionId = "ses_other"

        mockOCServerApi.mockSessionSSE(
            responseDef = { res, _ ->
                res.withBody(
                    toSSEBody(
                        OCEvent(type = "server.connected", data = OCEventData()),
                        OCEvent(type = "session.execution.started", data = OCEventData(sessionID = otherSessionId)),
                        OCEvent(type = "session.execution.started", data = OCEventData(sessionID = targetSessionId)),
                        OCEvent(type = "session.execution.succeeded", data = OCEventData(sessionID = targetSessionId))
                    )
                )
            }
        )

        val capturedEvents = mutableListOf<OCEvent>()
        var connected = false

        // when:
        val job = launch {
            openCodeClient.subscribeSessionEvents(targetSessionId, onConnected = { connected = true }) { event ->
                capturedEvents.add(event)
                if (event.type == "session.execution.succeeded") {
                    this.cancel()
                }
            }
        }

        withTimeout(4.seconds) { job.join() }

        // then:
        assertThat(connected).isTrue
        assertThat(capturedEvents).extracting<String> { it.type }
            .containsExactly("session.execution.started", "session.execution.succeeded")
        assertThat(capturedEvents).allSatisfy { assertThat(it.data?.sessionID).isEqualTo(targetSessionId) }
    }

    @Test
    fun testFetchHealthcheckStatusReturnsStatus(): Unit = runBlocking {
        // given:
        mockOCServerApi.mockHealthcheck(
            responseDef = { res, _ ->
                res.withStatus(418).withBody("I'm a teapot")
            }
        )

        // when:
        val status = openCodeClient.fetchHealthcheckStatus()

        // then:
        assertThat(status).isEqualTo(HttpStatusCode.fromValue(418))
    }

    private fun toSSEBody(vararg events: OCEvent): String = buildString {
        events.forEach { event ->
            append("event: ").append(event.type).append("\n")
            append("data: ").append(jsonMapper.writeValueAsString(event)).append("\n\n")
        }
    }
}
