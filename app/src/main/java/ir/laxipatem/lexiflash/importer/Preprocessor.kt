package ir.laxipatem.lexiflash.importer

/**
 * Turns raw cues into clean, deduplicated, merged sentences.
 *
 * The cleaning rules are deliberately conservative: anything that might be real
 * dialogue is kept. What gets removed is markup, sound annotations, speaker
 * labels, HTML entities and stray whitespace.
 */
class Preprocessor(
    /** Inclusive bounds on the word count of a usable sentence. */
    private val minWords: Int = 3,
    private val maxWords: Int = 30,
    /** Merge a split sentence when the gap to the next cue is at most this. */
    private val mergeGapMs: Long = 1_500L
) {

    private val htmlTag = Regex("""<[^>]{1,80}>""")
    private val assOverride = Regex("""\{[^}]{0,80}\}""")
    private val bracketedNoise = Regex("""\[[^\]]{0,40}]""")
    private val parentheticalNoise = Regex("""\((?i:laughs?|music|sighs?|applause|noise|inaudible|crosstalk|chuckles|sniffles|gasps?)\)""")
    private val musicSymbols = Regex("""[♪♫♬]""")
    private val speakerLabel = Regex("""^\s*[-–—]?\s*[A-Z][A-Z0-9 .'#]{1,20}(?:\s*\[[^\]]{0,30}])?\s*:\s*""")
    private val multiWhitespace = Regex("""\s{2,}""")
    private val htmlEntity = Regex("""&(?:amp|lt|gt|quot|apos|nbsp|#\d{1,5}|#x[0-9a-fA-F]{1,4});""")
    private val sentenceEnd = Regex("""[.!?…؟]["'”’)\]]?\s*$""")

    /** Cleans one string: strips markup, noise and speaker labels. */
    fun cleanText(raw: String): String {
        var text = raw
        text = htmlTag.replace(text, " ")
        text = assOverride.replace(text, " ")
        text = htmlEntity.replace(text) { match ->
            when (match.value.lowercase()) {
                "&amp;" -> "&"
                "&lt;" -> "<"
                "&gt;" -> ">"
                "&quot;" -> "\""
                "&apos;" -> "'"
                "&nbsp;" -> " "
                else -> " "
            }
        }
        text = bracketedNoise.replace(text, " ")
        text = parentheticalNoise.replace(text, " ")
        text = musicSymbols.replace(text, " ")
        text = speakerLabel.replace(text, "")
        text = multiWhitespace.replace(text.trim(), " ")
        return text.trim().trim('-', '–', '—').trim()
    }

    /** Word count used by the length filter; splits on any whitespace. */
    fun wordCount(text: String): Int = text.split(Regex("""\s+""")).count { it.isNotBlank() }

    /**
     * True when [text] is worth keeping as a flashcard.
     *
     * Rejects empty text, text that is only punctuation, sentences outside the
     * configured word bounds and strings with no letter at all.
     */
    fun isUsable(text: String): Boolean {
        if (text.isBlank()) return false
        if (text.none { it.isLetter() }) return false
        val words = wordCount(text)
        return words in minWords..maxWords
    }

    /** True when [text] happens to look like a complete sentence. */
    fun looksComplete(text: String): Boolean = sentenceEnd.containsMatchIn(text.trim())

    /**
     * Cleans cues, merges sentences broken across cue boundaries, drops
     * unusable lines and removes duplicates.
     *
     * When a cue has no timing, ordering is preserved and no merging occurs,
     * because there is no time gap to reason about.
     */
    fun process(cues: List<Cue>): List<ProcessedLine> {
        val cleaned = cues.mapNotNull { cue ->
            val text = cleanText(cue.text)
            if (text.isEmpty()) null else CleanCue(cue.startMs, cue.endMs, text)
        }

        val merged = mergeSplitSentences(cleaned)

        val seen = HashSet<String>()
        val result = ArrayList<ProcessedLine>(merged.size)
        for (cue in merged) {
            if (!isUsable(cue.text)) continue
            val fingerprint = dedupeKey(cue.text)
            if (fingerprint.isEmpty()) continue
            if (!seen.add(fingerprint)) continue
            result += ProcessedLine(
                text = cue.text,
                startMs = cue.startMs,
                endMs = cue.endMs
            )
        }
        return result
    }

    private data class CleanCue(val startMs: Long?, val endMs: Long?, val text: String)

    private fun mergeSplitSentences(cues: List<CleanCue>): List<CleanCue> {
        if (cues.isEmpty()) return cues
        val output = ArrayList<CleanCue>(cues.size)
        var pending: CleanCue? = null

        for (cue in cues) {
            val current = pending
            if (current == null) {
                pending = cue
                continue
            }
            val gap = gapBetween(current, cue)
            val shouldMerge = !looksComplete(current.text) &&
                gap != null &&
                gap <= mergeGapMs
            pending = if (shouldMerge) {
                CleanCue(
                    startMs = current.startMs,
                    endMs = cue.endMs,
                    text = multiWhitespace.replace("${current.text} ${cue.text}".trim(), " ")
                )
            } else {
                output += current
                cue
            }
        }
        pending?.let { output += it }
        return output
    }

    private fun gapBetween(first: CleanCue, second: CleanCue): Long? {
        val firstEnd = first.endMs ?: return null
        val secondStart = second.startMs ?: return null
        return secondStart - firstEnd
    }

    /** Lower-cased, punctuation-free key used to drop duplicates. */
    fun dedupeKey(text: String): String =
        text.lowercase()
            .replace(Regex("""[\p{P}\p{S}]"""), " ")
            .replace(multiWhitespace, " ")
            .trim()
}

/** A cleaned and validated line, ready to become a card. */
data class ProcessedLine(
    val text: String,
    val startMs: Long?,
    val endMs: Long?
)
