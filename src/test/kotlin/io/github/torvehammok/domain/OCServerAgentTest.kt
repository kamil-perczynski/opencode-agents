package io.github.torvehammok.domain

import com.github.tomakehurst.wiremock.WireMockServer
import io.github.ktor_batterypack.core.problemdetail.ProblemDetail
import io.github.torvehammok.domain.dto.OCThread
import io.github.torvehammok.domain.dto.OCThreadMessage
import io.github.torvehammok.domain.dto.OcAgentResponse
import io.github.torvehammok.domain.dto.ThreadSession
import io.github.torvehammok.infra.opencode.OpenCodeHealth
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.stubbing.Scenario
import io.github.torvehammok.infra.opencode.model.OCPromptRequest
import java.math.BigDecimal
import kotlin.time.Duration.Companion.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.ktor.plugin.koin

class OCServerAgentTest : OpenCodeAgentsIT() {

    private val wiremock: WireMockServer = application.koin().get()
    private val ocServerAgent: OCServerAgent = application.koin().get()
    private val mockOCServerApi: MockOCServerApi = application.koin().get()
    private val openCodeHealth: OpenCodeHealth = application.koin().get()

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

        val response = runInNewSession(
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
        val response = runInNewSession(someOCThread())

        // then:
        assertThat(response.response).isEqualTo("Oops! The agent has broken down. Rest assured - we will fix it. Just try again later.")
        assertThat(response.cost).isEqualByComparingTo("0.0125")
    }

    @Test
    fun testCodeModeExecuteToolCall(): Unit = runBlocking {
        // given:
        val sessionId = nextSessionId()
        val code = """await tools.github.search_code({ q: \"OCServerAgent\" })"""

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
                        """{"id":"evt_2","type":"session.tool.input.started","data":{"sessionID":"$sessionId","id":"call_1","name":"execute"}}""",
                        """{"id":"evt_3","type":"session.tool.called","data":{"sessionID":"$sessionId","id":"call_1","input":{"code":"$code"},"executed":true}}""",
                        """{"id":"evt_4","type":"session.text.ended","data":{"sessionID":"$sessionId","text":"done"}}""",
                        """{"id":"evt_5","type":"session.execution.succeeded","data":{"sessionID":"$sessionId"}}"""
                    )
                )
            }
        )
        mockOCServerApi.mockPrompt(sessionId)

        // when:
        val response = runInNewSession(someOCThread())

        // then:
        assertThat(response.response).isEqualTo("done")
        assertThat(response.toolsInvocations).isEqualTo(1)
    }

    @Test
    fun testStalledSessionIsInterruptedAndServerRestartRequested(): Unit = runBlocking {
        // given:
        val sessionId = nextSessionId()
        mockSession(sessionId)

        // and: the session goes silent, while the connection stays alive with heartbeats
        mockOCServerApi.mockSessionSSE(
            responseDef = { res, _ ->
                res.withBody(
                    sseEvents(
                        """{"id":"evt_1","type":"server.connected","data":{}}""",
                        """{"id":"evt_2","type":"session.execution.started","data":{"sessionID":"$sessionId"}}""",
                        """{"id":"evt_3","type":"session.tool.input.started","data":{"sessionID":"$sessionId","id":"call_1","name":"webfetch"}}"""
                    ) + heartbeats(30)
                ).withChunkedDribbleDelay(30, 2000)
            }
        )
        mockOCServerApi.mockActiveSessions(sessionId)
        mockOCServerApi.mockInterrupt(sessionId)

        val restartRequest = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(3.seconds) { openCodeHealth.restartRequests.first() }
        }

        // when:
        val response = runInNewSession(someOCThread())

        // then:
        assertThat(response.response).isEqualTo("The agent got stuck and has been stopped. Try again in a minute.")
        assertThat(response.duration.inWholeMilliseconds).isLessThan(2000)
        assertThat(restartRequest.await().reason).contains(sessionId)
        wiremock.verify(postRequestedFor(urlPathEqualTo("/api/session/$sessionId/interrupt")))
    }

    @Test
    fun testReconnectsAfterEventStreamDrop(): Unit = runBlocking {
        // given:
        val sessionId = nextSessionId()
        mockSession(sessionId)

        // and: the first connection drops mid-session, the second delivers the rest
        mockOCServerApi.mockSessionSSE(
            mappingDef = { mapping, _ -> mapping.inScenario("sse").whenScenarioStateIs(Scenario.STARTED).willSetStateTo("reconnected") },
            responseDef = { res, _ ->
                res.withBody(
                    sseEvents(
                        """{"id":"evt_1","type":"server.connected","data":{}}""",
                        """{"id":"evt_2","type":"session.execution.started","data":{"sessionID":"$sessionId"}}"""
                    )
                )
            }
        )
        mockOCServerApi.mockSessionSSE(
            mappingDef = { mapping, _ -> mapping.inScenario("sse").whenScenarioStateIs("reconnected") },
            responseDef = { res, _ ->
                res.withBody(
                    sseEvents(
                        """{"id":"evt_3","type":"server.connected","data":{}}""",
                        """{"id":"evt_4","type":"session.text.ended","data":{"sessionID":"$sessionId","text":"done"}}""",
                        """{"id":"evt_5","type":"session.execution.succeeded","data":{"sessionID":"$sessionId"}}"""
                    )
                )
            }
        )
        mockOCServerApi.mockActiveSessions(sessionId)

        // when:
        val response = runInNewSession(someOCThread())

        // then:
        assertThat(response.response).isEqualTo("done")
        wiremock.verify(2, getRequestedFor(urlPathEqualTo("/api/event")))
    }

    @Test
    fun testRecoversResultMissedWhileDisconnected(): Unit = runBlocking {
        // given:
        val sessionId = nextSessionId()
        mockSession(sessionId)

        // and: the session finishes while the event stream is disconnected
        mockOCServerApi.mockSessionSSE(
            mappingDef = { mapping, _ -> mapping.inScenario("sse").whenScenarioStateIs(Scenario.STARTED).willSetStateTo("reconnected") },
            responseDef = { res, _ ->
                res.withBody(
                    sseEvents(
                        """{"id":"evt_1","type":"server.connected","data":{}}""",
                        """{"id":"evt_2","type":"session.execution.started","data":{"sessionID":"$sessionId"}}"""
                    )
                )
            }
        )
        mockOCServerApi.mockSessionSSE(
            mappingDef = { mapping, _ -> mapping.inScenario("sse").whenScenarioStateIs("reconnected") },
            responseDef = { res, _ ->
                res.withBody(sseEvents("""{"id":"evt_3","type":"server.connected","data":{}}""") + heartbeats(20))
                    .withChunkedDribbleDelay(20, 1500)
            }
        )
        mockOCServerApi.mockActiveSessions()
        mockOCServerApi.mockMessages(
            sessionId = sessionId,
            responseDef = { res, _ ->
                res.withBody(
                    """
                    {"data":[
                      {"id":"msg_3","type":"idle","outcome":"succeeded"},
                      {"id":"msg_2","type":"assistant","content":[{"type":"reasoning","text":"thinking"},{"type":"text","text":"recovered"}]},
                      {"id":"msg_1","type":"user","text":"question"}
                    ]}
                    """.trimIndent()
                )
            }
        )

        // when:
        val response = runInNewSession(someOCThread())

        // then:
        assertThat(response.response).isEqualTo("recovered")
    }

    @Test
    fun testDeadConnectionIsReconnectedWithoutInterruptingSession(): Unit = runBlocking {
        // given:
        val sessionId = nextSessionId()
        mockSession(sessionId)

        // and: the first connection stops delivering anything, heartbeats included
        mockOCServerApi.mockSessionSSE(
            mappingDef = { mapping, _ -> mapping.inScenario("sse").whenScenarioStateIs(Scenario.STARTED).willSetStateTo("reconnected") },
            responseDef = { res, _ ->
                res.withBody(
                    sseEvents(
                        """{"id":"evt_1","type":"server.connected","data":{}}""",
                        """{"id":"evt_2","type":"session.execution.started","data":{"sessionID":"$sessionId"}}"""
                    ) + heartbeats(2) + " ".repeat(4000)
                ).withChunkedDribbleDelay(10, 1800)
            }
        )
        mockOCServerApi.mockSessionSSE(
            mappingDef = { mapping, _ -> mapping.inScenario("sse").whenScenarioStateIs("reconnected") },
            responseDef = { res, _ ->
                res.withBody(
                    sseEvents(
                        """{"id":"evt_3","type":"server.connected","data":{}}""",
                        """{"id":"evt_4","type":"session.text.ended","data":{"sessionID":"$sessionId","text":"done"}}""",
                        """{"id":"evt_5","type":"session.execution.succeeded","data":{"sessionID":"$sessionId"}}"""
                    )
                )
            }
        )
        mockOCServerApi.mockActiveSessions(sessionId)
        mockOCServerApi.mockInterrupt(sessionId)

        // when:
        val response = runInNewSession(someOCThread())

        // then:
        assertThat(response.response).isEqualTo("done")
        wiremock.verify(0, postRequestedFor(urlPathEqualTo("/api/session/$sessionId/interrupt")))
    }

    @Test
    fun testHangingPromptRequestFailsTheSession(): Unit = runBlocking {
        // given:
        val sessionId = nextSessionId()
        mockSession(sessionId)
        mockOCServerApi.mockSessionSSE(
            responseDef = { res, _ ->
                res.withBody(sseEvents("""{"id":"evt_1","type":"server.connected","data":{}}""") + heartbeats(20))
                    .withChunkedDribbleDelay(20, 2000)
            }
        )
        mockOCServerApi.mockPrompt(sessionId, responseDef = { res, _ -> res.withFixedDelay(1500) })

        // when:
        val response = runInNewSession(someOCThread())

        // then:
        assertThat(response.response).isEqualTo("Oops! The agent has broken down. Rest assured - we will fix it. Just try again later.")
        assertThat(response.duration.inWholeMilliseconds).isLessThan(1500)
    }

    @Test
    fun testReusesThreadSessionAndSendsOnlyNewMessages(): Unit = runBlocking {
        // given: the thread session has seen the thread up to the bot answer
        val sessionId = nextSessionId()
        val previous = ThreadSession(sessionId = sessionId, lastTs = "100.000002", costTotal = BigDecimal("0.5"))

        mockOCServerApi.mockActiveSessions()
        mockOCServerApi.mockPrompt(sessionId)
        mockOCServerApi.mockSessionSSE(
            responseDef = { res, _ ->
                res.withBody(
                    sseEvents(
                        """{"id":"evt_1","type":"server.connected","data":{}}""",
                        """{"id":"evt_2","type":"session.usage.updated","data":{"sessionID":"$sessionId","cost":0.75}}""",
                        """{"id":"evt_3","type":"session.text.ended","data":{"sessionID":"$sessionId","text":"answer"}}""",
                        """{"id":"evt_4","type":"session.execution.succeeded","data":{"sessionID":"$sessionId"}}"""
                    )
                )
            }
        )

        // when:
        val run = ocServerAgent.run(threadWithHistory(), previous)

        // then: the session is reused
        assertThat(run.response.response).isEqualTo("answer")
        wiremock.verify(0, postRequestedFor(urlPathEqualTo("/api/session")))

        // and: only messages the session has not seen are sent, without the system prompt
        val prompt = mockOCServerApi.findRequests<OCPromptRequest>(urlEqualTo("/api/session/$sessionId/prompt")).single().text
        assertThat(prompt)
            .contains("colleague comment", "follow-up")
            .doesNotContain("first question", "bot answer", "TOOL GUIDELINES")

        // and: cost covers this turn only, the session is kept for the next message
        assertThat(run.response.cost).isEqualByComparingTo("0.25")
        assertThat(run.session?.sessionId).isEqualTo(sessionId)
        assertThat(run.session?.lastTs).isEqualTo("100.000010")
        assertThat(run.session?.costTotal).isEqualByComparingTo("0.75")
    }

    @Test
    fun testStartsNewSessionWhenThreadSessionIsGone(): Unit = runBlocking {
        // given: the thread session no longer exists on the server
        val goneSessionId = nextSessionId()
        val newSessionId = nextSessionId()
        val previous = ThreadSession(sessionId = goneSessionId, lastTs = "100.000002", costTotal = BigDecimal.ZERO)

        mockOCServerApi.mockActiveSessions()
        mockOCServerApi.mockPrompt(goneSessionId, responseDef = { res, _ -> res.withStatus(404) })
        mockSession(newSessionId)
        mockOCServerApi.mockSessionSSE(responseDef = { res, _ -> res.withBody(succeededEvents(newSessionId, "fresh answer")) })

        // when:
        val run = ocServerAgent.run(threadWithHistory(), previous)

        // then: a new session gets the whole thread
        assertThat(run.response.response).isEqualTo("fresh answer")
        assertThat(run.session?.sessionId).isEqualTo(newSessionId)

        val prompt = mockOCServerApi.findRequests<OCPromptRequest>(urlEqualTo("/api/session/$newSessionId/prompt")).single().text
        assertThat(prompt).contains("TOOL GUIDELINES", "first question", "follow-up")
    }

    @Test
    fun testStartsNewSessionWhenThreadSessionIsStillRunning(): Unit = runBlocking {
        // given: the thread session is still running, e.g. its interrupt has failed
        val runningSessionId = nextSessionId()
        val newSessionId = nextSessionId()
        val previous = ThreadSession(sessionId = runningSessionId, lastTs = "100.000002", costTotal = BigDecimal.ZERO)

        mockOCServerApi.mockActiveSessions(runningSessionId)
        mockSession(newSessionId)
        mockOCServerApi.mockSessionSSE(responseDef = { res, _ -> res.withBody(succeededEvents(newSessionId, "fresh answer")) })

        // when:
        val run = ocServerAgent.run(threadWithHistory(), previous)

        // then:
        assertThat(run.session?.sessionId).isEqualTo(newSessionId)
        wiremock.verify(0, postRequestedFor(urlPathEqualTo("/api/session/$runningSessionId/prompt")))
    }

    @Test
    fun testMissedResultIsNotTakenFromPreviousTurn(): Unit = runBlocking {
        // given: a reused session, its prompt creates message msg_prompt
        val sessionId = nextSessionId()
        val previous = ThreadSession(sessionId = sessionId, lastTs = "100.000002", costTotal = BigDecimal.ZERO)

        mockOCServerApi.mockActiveSessions()
        mockOCServerApi.mockPrompt(sessionId, responseDef = { res, _ -> res.withBody("""{"data":{"id":"msg_prompt"}}""") })

        // and: the event stream drops, the reconnected one misses the end of the turn
        mockOCServerApi.mockSessionSSE(
            mappingDef = { mapping, _ -> mapping.inScenario("sse").whenScenarioStateIs(Scenario.STARTED).willSetStateTo("reconnected") },
            responseDef = { res, _ ->
                res.withBody(
                    sseEvents(
                        """{"id":"evt_1","type":"server.connected","data":{}}""",
                        """{"id":"evt_2","type":"session.execution.started","data":{"sessionID":"$sessionId"}}"""
                    )
                )
            }
        )
        mockOCServerApi.mockSessionSSE(
            mappingDef = { mapping, _ -> mapping.inScenario("sse").whenScenarioStateIs("reconnected") },
            responseDef = { res, _ ->
                res.withBody(sseEvents("""{"id":"evt_3","type":"server.connected","data":{}}""") + heartbeats(20))
                    .withChunkedDribbleDelay(20, 1500)
            }
        )

        // and: the only result in the session belongs to the previous turn
        mockOCServerApi.mockMessages(
            sessionId = sessionId,
            responseDef = { res, _ ->
                res.withBody(
                    """
                    {"data":[
                      {"id":"msg_prompt","type":"user","text":"follow-up"},
                      {"id":"msg_2","type":"idle","outcome":"succeeded"},
                      {"id":"msg_1","type":"assistant","content":[{"type":"text","text":"stale answer"}]}
                    ]}
                    """.trimIndent()
                )
            }
        )

        // when:
        val run = ocServerAgent.run(threadWithHistory(), previous)

        // then:
        assertThat(run.response.response).isEqualTo("Oops! The agent has broken down. Rest assured - we will fix it. Just try again later.")
        assertThat(run.session).isNull()
    }

    @Test
    fun testFailedTurnDropsThreadSession(): Unit = runBlocking {
        // given:
        val sessionId = nextSessionId()
        mockSession(sessionId)
        mockOCServerApi.mockSessionSSE(
            responseDef = { res, _ ->
                res.withBody(
                    sseEvents(
                        """{"id":"evt_1","type":"server.connected","data":{}}""",
                        """{"id":"evt_2","type":"session.execution.failed","data":{"sessionID":"$sessionId","error":{"message":"Rate limited"}}}"""
                    )
                )
            }
        )

        // when:
        val run = ocServerAgent.run(someOCThread(), previous = null)

        // then:
        assertThat(run.session).isNull()
    }

    @Test
    fun testEstimatesCostFromTokensWhenProviderReportsNone(): Unit = runBlocking {
        // given: a subscription provider reports cost 0
        val sessionId = nextSessionId()
        mockSession(sessionId)
        mockOCServerApi.mockSessionSSE(
            responseDef = { res, _ -> res.withBody(usageEvents(sessionId, cost = "0", input = 3648, cacheRead = 779904)) }
        )

        // when:
        val run = agentWithPricing(DEEPSEEK_FLASH_PRICING).run(someOCThread(), previous = null)

        // then: estimated at list price
        assertThat(run.response.cost).isEqualByComparingTo("0.003043")
        assertThat(run.session?.costTotal).isEqualByComparingTo("0.003043")
    }

    @Test
    fun testReportedCostWinsOverEstimate(): Unit = runBlocking {
        // given:
        val sessionId = nextSessionId()
        mockSession(sessionId)
        mockOCServerApi.mockSessionSSE(
            responseDef = { res, _ -> res.withBody(usageEvents(sessionId, cost = "0.0125", input = 3648, cacheRead = 779904)) }
        )

        // when:
        val run = agentWithPricing(DEEPSEEK_FLASH_PRICING).run(someOCThread(), previous = null)

        // then:
        assertThat(run.response.cost).isEqualByComparingTo("0.0125")
    }

    @Test
    fun testEstimatedCostOfReusedSessionCoversThisTurnOnly(): Unit = runBlocking {
        // given: the previous turns of the thread were estimated at 0.002
        val sessionId = nextSessionId()
        val previous = ThreadSession(sessionId = sessionId, lastTs = "100.000002", costTotal = BigDecimal("0.002"))

        mockOCServerApi.mockActiveSessions()
        mockOCServerApi.mockPrompt(sessionId)
        mockOCServerApi.mockSessionSSE(
            responseDef = { res, _ -> res.withBody(usageEvents(sessionId, cost = "0", input = 3648, cacheRead = 779904)) }
        )

        // when:
        val run = agentWithPricing(DEEPSEEK_FLASH_PRICING).run(threadWithHistory(), previous)

        // then:
        assertThat(run.response.cost).isEqualByComparingTo("0.001043")
        assertThat(run.session?.costTotal).isEqualByComparingTo("0.003043")
    }

    private fun agentWithPricing(pricing: ModelPricing): OCServerAgent {
        return OCServerAgent(
            openCodeClient = application.koin().get(),
            opencodeProps = application.koin().get(),
            openCodeHealth = openCodeHealth,
            modelPricingProvider = object : ModelPricingProvider {
                override suspend fun findPricing(modelId: String): ModelPricing = pricing
            }
        )
    }

    private suspend fun runInNewSession(thread: OCThread): OcAgentResponse {
        return ocServerAgent.run(thread, previous = null).response
    }

    private fun mockSession(sessionId: String) {
        mockOCServerApi.mockCreateSession(
            responseDef = { res, json ->
                val session = someCreateSessionResponse().let { it.copy(data = it.data.copy(id = sessionId)) }
                res.withBody(json.writeValueAsString(session))
            }
        )
        mockOCServerApi.mockPrompt(sessionId)
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
        val response = runInNewSession(
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
        val response = runInNewSession(
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

private fun heartbeats(count: Int): String = ": heartbeat\n\n".repeat(count)

private val DEEPSEEK_FLASH_PRICING = ModelPricing(
    input = BigDecimal("0.15"),
    output = BigDecimal("0.6"),
    cacheRead = BigDecimal("0.003"),
    cacheWrite = BigDecimal.ZERO
)

// with output 252 and reasoning 8: 3648 input + 779904 cache read cost 0.003042912 at DEEPSEEK_FLASH_PRICING
private fun usageEvents(sessionId: String, cost: String, input: Long, cacheRead: Long): String = sseEvents(
    """{"id":"evt_1","type":"server.connected","data":{}}""",
    """{"id":"evt_2","type":"session.usage.updated","data":{"sessionID":"$sessionId","cost":$cost,"tokens":{"input":$input,"output":252,"reasoning":8,"cache":{"read":$cacheRead,"write":0}}}}""",
    """{"id":"evt_3","type":"session.text.ended","data":{"sessionID":"$sessionId","text":"answer"}}""",
    """{"id":"evt_4","type":"session.execution.succeeded","data":{"sessionID":"$sessionId"}}"""
)

private fun succeededEvents(sessionId: String, text: String): String = sseEvents(
    """{"id":"evt_1","type":"server.connected","data":{}}""",
    """{"id":"evt_2","type":"session.text.ended","data":{"sessionID":"$sessionId","text":"$text"}}""",
    """{"id":"evt_3","type":"session.execution.succeeded","data":{"sessionID":"$sessionId"}}"""
)

private fun threadWithHistory(): OCThread = OCThread(
    channelId = "404981234",
    threadTs = "100.000001",
    messages = listOf(
        OCThreadMessage(text = "first question", ts = "100.000001", user = "U1", isBot = false),
        OCThreadMessage(text = "bot answer", ts = "100.000002", user = "B1", isBot = true),
        OCThreadMessage(text = "colleague comment", ts = "100.000003", user = "U2", isBot = false),
        OCThreadMessage(text = "follow-up", ts = "100.000010", user = "U1", isBot = false)
    )
)

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
