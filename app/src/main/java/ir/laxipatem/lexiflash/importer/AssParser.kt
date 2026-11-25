package ir.laxipatem.lexiflash.importer

/**
 * Advanced SubStation Alpha (.ass / .ssa).
 *
 * The `[Events]` section's `Format:` line defines the column order, so the
 * `Text` column is located by name instead of by position. That matters because
 * `Text` may itself contain commas, and because some files add extra columns.
 * Override blocks such as `{\an8}` are removed and `\N`/`\n` become spaces.
 */
class AssParser : SubtitleParser {

    private val overrideBlock = Regex("""\{[^}]*\}""")

    override fun canParse(fileName: String, head: String): Boolean {
        if (fileName.endsWith(".ass", ignoreCase = true) || fileName.endsWith(".ssa", ignoreCase = true)) {
            return true
        }
        return head.contains("[Script Info]", ignoreCase = true) && head.contains("[Events]", ignoreCase = true)
    }

    override fun parse(text: String): List<Cue> {
        val lines = normalizeNewlines(text).split('\n')
        val cues = ArrayList<Cue>()
        var inEvents = false
        var columns = listOf("Layer", "Start", "End", "Style", "Name", "MarginL", "MarginR", "MarginV", "Effect", "Text")
        var startIndex = 1
        var endIndex = 2
        var textIndex = 9

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.startsWith("[")) {
                inEvents = line.startsWith("[Events]", ignoreCase = true)
                continue
            }
            if (!inEvents) continue

            if (line.startsWith("Format:", ignoreCase = true)) {
                columns = line.substringAfter(':').split(',').map { it.trim() }
                startIndex = columns.indexOfFirst { it.equals("Start", ignoreCase = true) }.takeIf { it >= 0 } ?: 1
                endIndex = columns.indexOfFirst { it.equals("End", ignoreCase = true) }.takeIf { it >= 0 } ?: 2
                textIndex = columns.indexOfFirst { it.equals("Text", ignoreCase = true) }.takeIf { it >= 0 } ?: columns.lastIndex
                continue
            }
            if (!line.startsWith("Dialogue:", ignoreCase = true) && !line.startsWith("Comment:", ignoreCase = true)) {
                continue
            }
            // Comments carry no visible text and would duplicate dialogue lines.
            if (line.startsWith("Comment:", ignoreCase = true)) continue

            val payload = line.substringAfter(':')
            // Only split into as many fields as the format defines, so that any
            // commas inside the Text column stay part of the text.
            val fields = payload.split(',', limit = columns.size)
            if (fields.size <= textIndex) continue

            val start = TimeCodes.parse(fields.getOrNull(startIndex).orEmpty())
            val end = TimeCodes.parse(fields.getOrNull(endIndex).orEmpty())
            val body = cleanText(fields[textIndex])
            if (body.isNotEmpty()) cues += Cue(start, end, body)
        }
        return cues
    }

    private fun cleanText(raw: String): String {
        val withoutOverrides = overrideBlock.replace(raw, "")
        return withoutOverrides
            .replace("\\N", " ")
            .replace("\\n", " ")
            .replace("\\h", " ")
            .replace(Regex("""<[^>]+>"""), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
    }
}
