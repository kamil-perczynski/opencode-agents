package io.github.torvehammok.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CodeModeCallsTest {

    @Test
    fun testDotNotation() {
        // given:
        val code = """const r = await tools.github.search_code({ q: "OCServerAgent" })"""

        // expect:
        assertThat(extractCodeModeCalls(code)).containsExactly("github.search_code")
    }

    @Test
    fun testBracketNotation() {
        // given:
        val code = """await tools["github-mcp"]['get_file_contents']({ path: "README.md" })"""

        // expect:
        assertThat(extractCodeModeCalls(code)).containsExactly("github-mcp.get_file_contents")
    }

    @Test
    fun testMixedNotationAndMultipleCalls() {
        // given:
        val code = """
            const files = await tools.github["search_code"]({ q: "foo" })
            for (const f of files) {
                await tools [ "javadocs" ] . get_javadoc_symbol ({ symbol: f })
            }
            await tools.read.read({ path: "a" })
        """.trimIndent()

        // expect:
        assertThat(extractCodeModeCalls(code))
            .containsExactly("github.search_code", "javadocs.get_javadoc_symbol", "read.read")
    }

    @Test
    fun testNoCalls() {
        // given:
        val code = """const tools = 1; console.log(mytools.github.search_code, tools.github)"""

        // expect:
        assertThat(extractCodeModeCalls(code)).isEmpty()
    }
}
