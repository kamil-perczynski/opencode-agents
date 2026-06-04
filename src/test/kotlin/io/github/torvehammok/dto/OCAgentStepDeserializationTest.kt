package io.github.torvehammok.dto

import io.github.torvehammok.JsonMapperFactory
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OCAgentStepDeserializationTest {

    companion object {
        val mapper = JsonMapperFactory.createJsonMapper()
    }

    @Test
    fun `should deserialize output json`() {
        val json = this::class.java.getResource("/output.json")?.readText()
            ?: throw IllegalStateException("output.json not found")

        val steps = mapper.readValue(json, Array<OCAgentStep>::class.java).toList()

        assertTrue(steps.isNotEmpty(), "Expected at least one step")
        println("Deserialized ${steps.size} steps")

        val stepStart = steps.firstOrNull { it.type == "step_start" }
        assertNotNull(stepStart, "Expected at least one step_start")
        assertTrue(stepStart?.part is StepStartPart, "step_start part should be StepStartPart")

        val toolUse = steps.firstOrNull { it.type == "tool_use" }
        assertNotNull(toolUse, "Expected at least one tool_use")
        assertTrue(toolUse?.part is ToolPart, "tool_use part should be ToolPart")
        val toolPart = toolUse?.part as ToolPart
        assertNotNull(toolPart.state, "ToolPart should have state")

        val stepFinish = steps.firstOrNull { it.type == "step_finish" }
        assertNotNull(stepFinish, "Expected at least one step_finish")
        assertTrue(stepFinish?.part is StepFinishPart, "step_finish part should be StepFinishPart")
        val finishPart = stepFinish?.part as StepFinishPart
        assertNotNull(finishPart.tokens, "StepFinishPart should have tokens")

        val text = steps.firstOrNull { it.type == "text" }
        assertNotNull(text, "Expected at least one text")
        assertTrue(text?.part is TextPart, "text part should be TextPart")
    }
}
