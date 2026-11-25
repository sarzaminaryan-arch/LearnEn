package ir.laxipatem.lexiflash.importer

/**
 * Pairs cues from an English file with cues from a Persian file by time overlap.
 *
 * The rule, documented in `docs/DECISIONS.md`:
 * * For each English cue, every Persian cue is a candidate when the overlap
 *   covers at least [minOverlapFraction] of the English cue's duration.
 * * Among candidates, the Persian cue with the largest overlap wins; ties go to
 *   the earlier Persian cue so the result is deterministic.
 * * One Persian cue **may** serve several English cues: subtitles frequently
 *   merge two short English lines into a single Persian line, and re-using the
 *   translation is more useful than dropping it. Later English cues that share
 *   the same Persian cue are flagged as reused so the caller can choose to mark
 *   them pending instead.
 * * When no candidate reaches the overlap threshold, the English cue is left
 *   unpaired and the caller creates a translation-pending card.
 */
class BilingualPairer(
    private val minOverlapFraction: Double = 0.30
) {

    /**
     * Pairs [english] with [persian].
     *
     * @return one [PairedCue] per English cue, in the original order.
     */
    fun pair(english: List<Cue>, persian: List<Cue>): List<PairedCue> {
        if (english.isEmpty()) return emptyList()

        val persianWithTiming = persian.filter { it.startMs != null && it.endMs != null }
        val usedPersian = HashMap<Int, Int>()

        return english.map { englishCue ->
            val best = bestMatch(englishCue, persianWithTiming)
                ?: return@map PairedCue(englishCue, persianText = null, persianCue = null, reused = false)

            val uses = usedPersian.getOrDefault(best.first, 0)
            usedPersian[best.first] = uses + 1
            PairedCue(
                english = englishCue,
                persianText = best.second.text,
                persianCue = best.second,
                reused = uses > 0
            )
        }
    }

    /** Pairs untimed files one-to-one by position, the only sensible fallback. */
    fun pairByPosition(english: List<Cue>, persian: List<Cue>): List<PairedCue> {
        return english.mapIndexed { index, englishCue ->
            val match = persian.getOrNull(index)
            PairedCue(
                english = englishCue,
                persianText = match?.text,
                persianCue = match,
                reused = false
            )
        }
    }

    /** Chooses the Persian cue with the greatest normalised overlap, if any qualifies. */
    private fun bestMatch(englishCue: Cue, persian: List<Cue>): Pair<Int, Cue>? {
        val englishStart = englishCue.startMs ?: return null
        val englishEnd = englishCue.endMs ?: return null
        val englishDuration = (englishEnd - englishStart).coerceAtLeast(1L)

        var bestIndex = -1
        var bestOverlap = 0L
        var bestCue: Cue? = null

        persian.forEachIndexed { index, candidate ->
            val candidateStart = candidate.startMs ?: return@forEachIndexed
            val candidateEnd = candidate.endMs ?: return@forEachIndexed
            if (!englishCue.overlaps(candidate)) return@forEachIndexed
            val overlap = minOf(englishEnd, candidateEnd) - maxOf(englishStart, candidateStart)
            val fraction = overlap.toDouble() / englishDuration.toDouble()
            if (fraction < minOverlapFraction) return@forEachIndexed
            if (overlap > bestOverlap) {
                bestOverlap = overlap
                bestIndex = index
                bestCue = candidate
            }
        }
        val cue = bestCue ?: return null
        return bestIndex to cue
    }

}

/** One English cue and the Persian translation chosen for it. */
data class PairedCue(
    val english: Cue,
    val persianText: String?,
    val persianCue: Cue?,
    /** True when an earlier English cue already consumed this Persian cue. */
    val reused: Boolean
)
