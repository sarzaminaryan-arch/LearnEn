package ir.laxipatem.lexiflash.domain.unlock

/**
 * Decides which parts of a deck are reachable.
 *
 * Rules
 * -----
 * 1. Part 1 is always open.
 * 2. Part N + 1 opens once every card in part N has been *seen* at least once
 *    **and** the best quiz score for part N is at least [PASS_PERCENT] percent.
 * 3. Quiz attempts are unlimited; the best score is what counts.
 * 4. The "unlock all parts" setting opens everything regardless of the rules.
 *
 * The function is pure so it can be unit tested without Android or a database.
 */
object UnlockPolicy {

    /** Minimum quiz percentage that unlocks the next part. */
    const val PASS_PERCENT: Int = 80

    /** Per-part input to [compute]. */
    data class PartStatus(
        val partIndex: Int,
        val cardCount: Int,
        val seenCount: Int,
        /** Best quiz percentage recorded for this part, or null when never taken. */
        val bestScorePercent: Int?
    )

    /** Reachability of a single part. */
    enum class PartAccess { LOCKED, OPEN, COMPLETED }

    /**
     * Returns one [PartAccess] per part, ordered by [PartStatus.partIndex].
     *
     * Callers that hold parts in the same ascending order (the normal case)
     * can index the result directly. A part counts as [PartAccess.COMPLETED]
     * when its own quiz has been passed; it stays [PartAccess.OPEN] while it is
     * reachable but not yet passed.
     */
    fun compute(parts: List<PartStatus>, unlockAll: Boolean): List<PartAccess> {
        if (parts.isEmpty()) return emptyList()

        val sorted = parts.sortedBy { it.partIndex }
        val result = MutableList(sorted.size) { PartAccess.LOCKED }

        for (position in sorted.indices) {
            val part = sorted[position]
            val reachable = unlockAll || position == 0 || isUnlockedBy(sorted[position - 1])
            result[position] = when {
                !reachable -> PartAccess.LOCKED
                passed(part) -> PartAccess.COMPLETED
                else -> PartAccess.OPEN
            }
        }
        return result
    }

    /** True when the user has satisfied the requirements of [part]. */
    fun passed(part: PartStatus): Boolean =
        part.cardCount > 0 &&
            part.seenCount >= part.cardCount &&
            (part.bestScorePercent ?: -1) >= PASS_PERCENT

    /**
     * True when [part] unlocks the part that follows it.
     *
     * A part with no cards never blocks progress, because there would be no way
     * to satisfy the requirement. This keeps imported decks with a truncated
     * last part usable.
     */
    fun isUnlockedBy(part: PartStatus): Boolean =
        part.cardCount == 0 || passed(part)

    /** Completion ratio in `[0.0, 1.0]` used by the progress screen. */
    fun completionRatio(part: PartStatus): Float {
        if (part.cardCount <= 0) return 0f
        val seen = part.seenCount.coerceIn(0, part.cardCount)
        return seen.toFloat() / part.cardCount.toFloat()
    }
}
