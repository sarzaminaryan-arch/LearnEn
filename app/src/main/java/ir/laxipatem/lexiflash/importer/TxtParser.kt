package ir.laxipatem.lexiflash.importer

/**
 * Plain text: one sentence per non-empty line, no timing.
 *
 * A blank line ends a paragraph but every non-blank line stays its own cue,
 * because that is what the user typed and what the preprocessor expects.
 */
class TxtParser : SubtitleParser {

    override fun canParse(fileName: String, head: String): Boolean {
        if (fileName.endsWith(".txt", ignoreCase = true)) return true
        // Fallback: content with no recognisable structural marker.
        return head.contains("-->").not() &&
            head.contains("[Script Info]").not() &&
            head.trimStart().startsWith("{").not()
    }

    override fun parse(text: String): List<Cue> {
        val normalized = normalizeNewlines(text)
        return normalized.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { Cue(startMs = null, endMs = null, text = it) }
    }
}
