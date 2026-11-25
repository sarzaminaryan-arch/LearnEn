package ir.laxipatem.lexiflash.data.repo

import android.content.Context
import ir.laxipatem.lexiflash.data.db.AppDatabase
import ir.laxipatem.lexiflash.domain.stats.StreakCalculator
import java.util.TimeZone
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Aggregate numbers shown on the progress screen. */
data class StudyStats(
    val streakDays: Int,
    val longestStreakDays: Int,
    val accuracyPercent: Int,
    val dueNow: Int,
    val reviewsToday: Int,
    val totalReviews: Long
)

/**
 * Read-only statistics over the review log.
 *
 * Definitions used here and mirrored in `docs/REVIEW_NEEDED.md`:
 * * **Accuracy** is the share of reviews rated Hard, Good or Easy among the
 *   reviews of the last 30 days. A rating of Again counts as a miss.
 * * **Streak** counts consecutive local calendar days with at least one review.
 *   Studying yesterday but not yet today keeps the streak alive.
 * * **Daily goal progress** counts distinct cards reviewed since local midnight.
 */
class StatsRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val reviewDao get() = database.reviewDao()
    private val deckDao get() = database.deckDao()

    val totalReviews: Flow<Int> = reviewDao.observeTotalReviews()

    fun dueCount(now: Long): Flow<Int> = deckDao.observeDueCount(now)

    /** Recomputes the aggregate snapshot. Cheap enough to call on screen entry. */
    suspend fun snapshot(now: Long): StudyStats = withContext(ioDispatcher) {
        val windowStart = now - ACCURACY_WINDOW_DAYS * MILLIS_PER_DAY
        val totalInWindow = reviewDao.reviewCountSince(windowStart)
        val goodInWindow = reviewDao.successfulReviewCountSince(windowStart)
        val accuracy = if (totalInWindow == 0L) 0 else ((goodInWindow * 100) / totalInWindow).toInt()

        val activeDays = activeEpochDays()
        val today = epochDay(now)
        val reviewsToday = reviewDao.cardsReviewedSince(startOfLocalDay(now))

        StudyStats(
            streakDays = StreakCalculator.currentStreak(activeDays, today),
            longestStreakDays = StreakCalculator.longestStreak(activeDays),
            accuracyPercent = accuracy,
            dueNow = deckDao.dueCount(now),
            reviewsToday = reviewsToday,
            totalReviews = reviewDao.reviewCountSince(0)
        )
    }

    /** Epoch-day numbers for every day that has at least one review. */
    suspend fun activeEpochDays(): Set<Long> {
        val since = System.currentTimeMillis() - MAX_LOOKBACK_DAYS * MILLIS_PER_DAY
        val timestamps = reviewDao.reviewTimestampsSince(since)
        val zone = TimeZone.getDefault()
        return timestamps.mapTo(HashSet()) { timestamp ->
            val offset = zone.getOffset(timestamp)
            Math.floorDiv(timestamp + offset, MILLIS_PER_DAY)
        }
    }

    private fun epochDay(timestamp: Long): Long {
        val offset = TimeZone.getDefault().getOffset(timestamp)
        return Math.floorDiv(timestamp + offset, MILLIS_PER_DAY)
    }

    private fun startOfLocalDay(timestamp: Long): Long {
        val zone = TimeZone.getDefault()
        val offset = zone.getOffset(timestamp)
        return Math.floorDiv(timestamp + offset, MILLIS_PER_DAY) * MILLIS_PER_DAY - offset
    }

    companion object {
        const val ACCURACY_WINDOW_DAYS = 30L
        const val MAX_LOOKBACK_DAYS = 400L
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
