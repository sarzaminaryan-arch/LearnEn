package ir.laxipatem.lexiflash.domain.stats

/**
 * Day-streak arithmetic, expressed on plain epoch-day numbers so it stays pure
 * and testable. Callers are responsible for converting timestamps to local days.
 */
object StreakCalculator {

    /**
     * Length of the run of consecutive days ending at [todayEpochDay].
     *
     * A streak survives when the most recent activity happened **today or
     * yesterday**; that way a learner who has not studied yet today still sees
     * yesterday's streak instead of zero.
     *
     * @param activeEpochDays distinct days on which at least one review happened.
     * @param todayEpochDay the learner's current local day.
     */
    fun currentStreak(activeEpochDays: Collection<Long>, todayEpochDay: Long): Int {
        if (activeEpochDays.isEmpty()) return 0
        val days = activeEpochDays.toSortedSet()
        val last = days.last()
        // Nothing for more than a day: the run is broken.
        if (last < todayEpochDay - 1) return 0

        var streak = 1
        var cursor = last
        while (days.contains(cursor - 1)) {
            streak++
            cursor--
        }
        return streak
    }

    /** Longest run of consecutive days anywhere in [activeEpochDays]. */
    fun longestStreak(activeEpochDays: Collection<Long>): Int {
        if (activeEpochDays.isEmpty()) return 0
        val days = activeEpochDays.toSortedSet()
        var best = 1
        var run = 1
        var previous: Long? = null
        for (day in days) {
            val prev = previous
            run = when {
                prev == null -> 1
                day == prev + 1 -> run + 1
                else -> 1
            }
            if (run > best) best = run
            previous = day
        }
        return best
    }

    /** True when [activeEpochDays] contains the day before [todayEpochDay]. */
    fun reviewedYesterday(activeEpochDays: Collection<Long>, todayEpochDay: Long): Boolean =
        activeEpochDays.contains(todayEpochDay - 1)

    /** True when the learner has already studied on [todayEpochDay]. */
    fun reviewedToday(activeEpochDays: Collection<Long>, todayEpochDay: Long): Boolean =
        activeEpochDays.contains(todayEpochDay)
}
