package ir.laxipatem.lexiflash.importer

/**
 * Timestamp parsing shared by the subtitle formats. Every function returns
 * milliseconds, or null when the token is not a timestamp at all.
 */
internal object TimeCodes {

    private val CLOCK = Regex("""(\d{1,3}):(\d{1,2}):(\d{1,2})[.,](\d{1,3})""")
    private val SHORT_CLOCK = Regex("""(\d{1,2}):(\d{1,2})[.,](\d{1,3})""")

    /** Parses `HH:MM:SS,mmm`, `HH:MM:SS.mmm`, `MM:SS.mmm` or plain seconds. */
    fun parse(raw: String): Long? {
        val token = raw.trim()
        if (token.isEmpty()) return null

        CLOCK.find(token)?.let { match ->
            val (hours, minutes, seconds, millis) = match.destructured
            return toMillis(hours.toLong(), minutes.toLong(), seconds.toLong(), millis)
        }
        SHORT_CLOCK.find(token)?.let { match ->
            val (minutes, seconds, millis) = match.destructured
            return toMillis(0, minutes.toLong(), seconds.toLong(), millis)
        }
        // ASS writes `H:MM:SS.cc` with a two-digit centisecond field.
        ASS_TIME.find(token)?.let { match ->
            val (hours, minutes, seconds, centis) = match.destructured
            val millis = centis.padEnd(3, '0').take(3)
            return toMillis(hours.toLong(), minutes.toLong(), seconds.toLong(), millis)
        }
        return token.toDoubleOrNull()?.takeIf { it >= 0 }?.let { (it * 1000).toLong() }
    }

    private val ASS_TIME = Regex("""(\d{1,2}):(\d{1,2}):(\d{1,2})\.(\d{1,2})""")

    private fun toMillis(hours: Long, minutes: Long, seconds: Long, millis: String): Long {
        val ms = millis.padEnd(3, '0').take(3).toLong()
        return ((hours * 3600) + (minutes * 60) + seconds) * 1000 + ms
    }

    /** Parses a `start --> end` SRT/VTT timing line into a pair, or null. */
    fun parseRange(line: String): Pair<Long?, Long?>? {
        val arrow = line.indexOf("-->")
        if (arrow < 0) return null
        val startToken = line.substring(0, arrow).trim()
        val rest = line.substring(arrow + 3).trim()
        // VTT allows cue settings after the end timestamp: "00:01.000 --> 00:02.000 align:start".
        val endToken = rest.substringBefore(' ').trim()
        return parse(startToken) to parse(endToken)
    }
}
