package ir.laxipatem.lexiflash.importer

/**
 * SubRip (.srt).
 *
 * Tolerated quirks:
 * * CRLF, LF and lone CR line endings.
 * * A missing or duplicated sequence number.
 * * `,` or `.` as the millisecond separator.
 * * Blank lines inside a cue's text (the next cue is detected by its timing line).
 */
class SrtParser : SubtitleParser {

    override fun canParse(fileName: String, head: String): Boolean {
        if (fileName.endsWith(".srt", ignoreCase = true)) return true
        if (head.contains("WEBVTT", ignoreCase = true)) return false
        return head.lineSequence().any { it.contains("-->") }
    }

    override fun parse(text: String): List<Cue> {
        val lines = normalizeNewlines(text).split('\n')
        val cues = ArrayList<Cue>()
        var index = 0

        while (index < lines.size) {
            val line = lines[index]
            val timing = TimeCodes.parseRange(line)
            if (timing == null) {
                index++
                continue
            }

            val (start, end) = timing
            index++
            val body = StringBuilder()
            while (index < lines.size) {
                val candidate = lines[index]
                if (TimeCodes.parseRange(candidate) != null) break
                // A sequence number followed by a timing line starts the next cue.
                if (candidate.isBlank() &&
                    index + 2 < lines.size &&
                    lines[index + 1].trim().toIntOrNull() != null &&
                    TimeCodes.parseRange(lines[index + 2]) != null
                ) {
                    index += 2
                    break
                }
                if (body.isNotEmpty()) body.append(' ')
                body.append(candidate)
                index++
            }
            val cueText = body.toString().trim()
            if (cueText.isNotEmpty()) cues += Cue(start, end, cueText)
        }
        return cues
    }
}

internal fun normalizeNewlines(text: String): String =
    text.replace("\r\n", "\n").replace('\r', '\n').removePrefix("\uFEFF")
