package ir.laxipatem.lexiflash.importer

/**
 * Splits an ordered list of lines into N contiguous parts.
 *
 * Sizing rules
 * ------------
 * * Parts are as close to equal as arithmetic allows: with 10 parts and 105
 *   lines the first five parts get 11 lines and the rest get 10.
 * * No part is ever empty. When there are fewer lines than requested parts the
 *   part count is reduced to the number of lines, so a 6-line file produces 6
 *   single-line parts rather than 10 parts with four empty ones.
 * * Order is preserved, which is what makes part 1 the easiest slice of a deck
 *   sorted by difficulty, and part 1 the earliest slice of a timeline.
 */
object PartSplitter {

    /** Splits [lines] into at most [requestedParts] non-empty contiguous parts. */
    fun <T> split(lines: List<T>, requestedParts: Int): List<List<T>> {
        if (lines.isEmpty()) return emptyList()
        val parts = requestedParts.coerceIn(1, lines.size)
        val baseSize = lines.size / parts
        val remainder = lines.size % parts

        val result = ArrayList<List<T>>(parts)
        var cursor = 0
        for (index in 0 until parts) {
            val size = baseSize + if (index < remainder) 1 else 0
            result += lines.subList(cursor, cursor + size).toList()
            cursor += size
        }
        return result
    }

    /**
     * Splits timed lines by their start time rather than by position, so a file
     * whose cues bunch up in the first few minutes still yields balanced parts.
     *
     * Lines without timing are appended to the last part, keeping part 1 purely
     * chronological.
     */
    fun splitByTimeline(lines: List<ProcessedLine>, requestedParts: Int): List<List<ProcessedLine>> {
        if (lines.isEmpty()) return emptyList()
        if (requestedParts <= 1) return listOf(lines)

        val timed = lines.filter { it.startMs != null }
        val untimed = lines.filter { it.startMs == null }
        if (timed.isEmpty()) return split(lines, requestedParts)

        val parts = requestedParts.coerceIn(1, lines.size)
        val start = timed.minOf { it.startMs!! }
        val end = timed.maxOf { it.startMs!! }
        if (end <= start) return splitWithTail(timed, untimed, parts)

        val span = (end - start + 1).toDouble()
        val buckets = MutableList(parts) { ArrayList<ProcessedLine>() }
        for (line in timed) {
            val startMs = line.startMs ?: continue
            val ratio = (startMs - start).toDouble() / span
            val bucket = (ratio * parts).toInt().coerceIn(0, parts - 1)
            buckets[bucket] += line
        }

        // Re-balance: empty buckets would violate the "no empty part" rule, so
        // fall back to an even positional split of the same chronological order.
        if (buckets.any { it.isEmpty() }) {
            return splitWithTail(timed, untimed, parts)
        }
        return splitWithTail(buckets.flatten(), untimed, parts)
    }

    private fun splitWithTail(
        ordered: List<ProcessedLine>,
        untimed: List<ProcessedLine>,
        parts: Int
    ): List<List<ProcessedLine>> {
        val chunks = split(ordered, parts).toMutableList()
        if (untimed.isEmpty() || chunks.isEmpty()) return chunks
        chunks[chunks.lastIndex] = chunks.last() + untimed
        return chunks
    }

    /**
     * The part count actually used for [totalLines] when [requestedParts] is
     * asked for. Never returns 0 for a non-empty input.
     */
    fun effectivePartCount(totalLines: Int, requestedParts: Int): Int =
        if (totalLines <= 0) 0 else requestedParts.coerceIn(1, totalLines)
}
