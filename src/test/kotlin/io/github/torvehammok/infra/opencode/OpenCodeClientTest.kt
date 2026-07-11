package io.github.torvehammok.infra.opencode

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.github.torvehammok.domain.MockOCServerApi
import io.github.torvehammok.domain.OpenCodeAgentsIT
import io.github.torvehammok.infra.opencode.model.OCPromptAsyncRequest
import io.github.torvehammok.infra.opencode.model.OCPromptPart
import io.github.torvehammok.infra.opencode.model.OCSseEvent
import io.github.torvehammok.infra.opencode.model.OCSseInfo
import io.github.torvehammok.infra.opencode.model.OCSsePayload
import io.github.torvehammok.infra.opencode.model.OCSseProperties
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
                            "id" to expectedSessionId,
                            "slug" to "test",
                            "projectID" to "proj_test",
                            "directory" to "/tmp/test",
                            "path" to "/tmp/test",
                            "cost" to 0.0,
                            "title" to "Test Session"
                        )
                    )
                )
            }
        )

        // when:
        val sessionId = openCodeClient.createSession()

        // then:
        assertThat(sessionId).isEqualTo(expectedSessionId)
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
        val thrown = catchThrowable { runBlocking { openCodeClient.createSession() } }
        assertThat(thrown)
            .isInstanceOf(OpenCodeClientException::class.java)
            .hasMessage("Failed to open new session")
    }

    @Test
    fun testPromptAsyncReturnsSuccessAndSplitsModelId(): Unit = runBlocking {
        // given:
        val sessionId = "ses_prompt_123"
        mockOCServerApi.mockPromptAsync(sessionId)

        // when:
        openCodeClient.promptAsync(
            agent = "test-agent",
            prompt = "Hello",
            sessionId = sessionId,
            modelId = "openai/gpt-4"
        )

        // then:
        val requests = mockOCServerApi.findRequests<OCPromptAsyncRequest>(
            urlEqualTo("/session/$sessionId/prompt_async")
        )

        assertThat(requests).satisfiesExactly({ request ->
            assertThat(request.agent).isEqualTo("test-agent")
            assertThat(request.parts).containsExactly(OCPromptPart(type = "text", text = "Hello"))
            assertThat(request.model.providerID).isEqualTo("openai")
            assertThat(request.model.modelID).isEqualTo("gpt-4")
        })
    }

    @Test
    fun testPromptAsyncThrowsOnFailure(): Unit = runBlocking {
        // given:
        val sessionId = "ses_prompt_fail"
        mockOCServerApi.mockPromptAsync(
            sessionId = sessionId,
            responseDef = { res, _ ->
                res.withStatus(503).withBody("Service Unavailable")
            }
        )

        // expect:
        val thrown = catchThrowable {
            runBlocking {
                openCodeClient.promptAsync(
                    agent = "test-agent",
                    prompt = "Hello",
                    sessionId = sessionId,
                    modelId = "openai/gpt-4"
                )
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
                        OCSseEvent(
                            payload = OCSsePayload(
                                properties = OCSseProperties(sessionID = otherSessionId)
                            )
                        ),
                        OCSseEvent(
                            payload = OCSsePayload(
                                properties = OCSseProperties(sessionID = targetSessionId)
                            )
                        ),
                        OCSseEvent(
                            payload = OCSsePayload(
                                properties = OCSseProperties(
                                    sessionID = targetSessionId,
                                    info = OCSseInfo(finish = "stop")
                                )
                            )
                        )
                    )
                )
            }
        )

        val capturedEvents = mutableListOf<OCSseEvent>()

        // when:
        val job = launch {
            openCodeClient.subscribeSessionEvents(targetSessionId) { event ->
                capturedEvents.add(event)
                if (event.payload?.properties?.info?.finish == "stop") {
                    this.cancel()
                }
            }
        }

        withTimeout(4.seconds) { job.join() }

        // then:
        assertThat(capturedEvents).hasSize(2)
        assertThat(capturedEvents.first().payload?.properties?.sessionID).isEqualTo(targetSessionId)
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

    private fun toSSEBody(vararg events: OCSseEvent): String = buildString {
        events.forEach { event ->
            append("data: ").append(jsonMapper.writeValueAsString(event)).append("\n\n")
        }
    }
}
