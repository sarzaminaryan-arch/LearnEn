package ir.laxipatem.lexiflash.importer

/** A single timed text unit extracted from a subtitle or text file. */
data class Cue(
    val startMs: Long?,
    val endMs: Long?,
    val text: String
) {
    val durationMs: Long?
        get() = if (startMs != null && endMs != null && endMs >= startMs) endMs - startMs else null

    /** True when this cue and [other] share any time at all. */
    fun overlaps(other: Cue): Boolean {
        val aStart = startMs ?: return false
        val aEnd = endMs ?: return false
        val bStart = other.startMs ?: return false
        val bEnd = other.endMs ?: return false
        return aStart < bEnd && bStart < aEnd
    }
}

/**
 * Contract for every supported input format.
 *
 * `canParse` receives the file name and the first few kilobytes of decoded text,
 * so detection can rely on the extension where the format is unambiguous and on
 * the content where it is not (WebVTT and SRT share a shape, ASS announces
 * itself with `[Script Info]`).
 */
interface SubtitleParser {
    fun canParse(fileName: String, head: String): Boolean
    fun parse(text: String): List<Cue>
}
