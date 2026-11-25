package ir.laxipatem.lexiflash.importer

/**
 * WebVTT (.vtt).
 *
 * Handles the `WEBVTT` header, `NOTE`/`STYLE`/`REGION` blocks, optional cue
 * identifiers, cue settings after the timestamp (`align:start position:10%`) and
 * inline markup such as `<c.classname>`, `<v Speaker>` and `<00:00:01.000>`
 * timestamps, which are stripped to their human-readable text.
 */
class VttParser : SubtitleParser {

    private val cueTag = Regex("""</?[a-zA-Z][^>]*>""")
    private val speakerTag = Regex("""<v(\.[^\s>]+)?\s+([^>]*)>""")

    override fun canParse(fileName: String, head: String): Boolean {
        if (fileName.endsWith(".vtt", ignoreCase = true)) return true
        return head.trimStart().startsWith("WEBVTT", ignoreCase = true)
    }

    override fun parse(text: String): List<Cue> {
        val lines = normalizeNewlines(text).split('\n')
        val cues = ArrayList<Cue>()
        var index = 0

        // Skip the header block up to the first blank line.
        if (lines.firstOrNull()?.trim()?.startsWith("WEBVTT", ignoreCase = true) == true) {
            while (index < lines.size && lines[index].isNotBlank()) index++
        }

        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trim()

            if (trimmed.isEmpty()) {
                index++
                continue
            }
            // Whole blocks that are not cues.
            if (trimmed.startsWith("NOTE") ||
                trimmed.startsWith("STYLE") ||
                trimmed.startsWith("REGION")
            ) {
                while (index < lines.size && lines[index].isNotBlank()) index++
                continue
            }

            val timing = TimeCodes.parseRange(line)
            if (timing == null) {
                // Might be a cue identifier; the next line should be the timing.
                if (index + 1 < lines.size && TimeCodes.parseRange(lines[index + 1]) != null) {
                    index++
                    continue
                }
                index++
                continue
            }

            val (start, end) = timing
            index++
            val body = StringBuilder()
            while (index < lines.size && lines[index].isNotBlank()) {
                if (TimeCodes.parseRange(lines[index]) != null) break
                if (body.isNotEmpty()) body.append(' ')
                body.append(lines[index])
                index++
            }
            val cueText = cleanMarkup(body.toString().trim())
            if (cueText.isNotEmpty()) cues += Cue(start, end, cueText)
        }
        return cues
    }

    private fun cleanMarkup(raw: String): String {
        val withoutSpeaker = speakerTag.replace(raw) { match -> match.groupValues[2] + " " }
        return cueTag.replace(withoutSpeaker, "").trim()
    }
}
