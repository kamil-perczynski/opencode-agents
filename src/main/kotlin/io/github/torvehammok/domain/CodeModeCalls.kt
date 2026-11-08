package io.github.torvehammok.domain

/**
 * Name of the single tool exposed by opencode Code Mode - real tool calls happen inside its `code` snippet.
 */
const val CODE_MODE_TOOL = "execute"

// segment is either `.name` or `["name"]` / `['name']`
private const val SEGMENT = """(?:\s*\.\s*([A-Za-z_$][\w$]*)|\s*\[\s*["']([^"']+)["']\s*])"""

private val CODE_MODE_CALL = Regex("""\btools$SEGMENT$SEGMENT\s*\(""")

/**
 * Extracts `namespace.tool` names of the calls made from a Code Mode snippet,
 * e.g. `tools.github.search_code(...)` or `tools["github"]["search_code"](...)`.
 */
fun extractCodeModeCalls(code: String): List<String> {
    return CODE_MODE_CALL.findAll(code)
        .map { match ->
            val (nsDot, nsBracket, toolDot, toolBracket) = match.destructured
            "${nsDot.ifEmpty { nsBracket }}.${toolDot.ifEmpty { toolBracket }}"
        }
        .toList()
}
