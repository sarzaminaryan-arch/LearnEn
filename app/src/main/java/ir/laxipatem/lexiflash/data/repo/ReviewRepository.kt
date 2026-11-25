package ir.laxipatem.lexiflash.data.repo

import android.content.Context
import ir.laxipatem.lexiflash.data.db.AppDatabase
import ir.laxipatem.lexiflash.data.db.CardStateEntity
import ir.laxipatem.lexiflash.data.db.ReviewLogEntity
import ir.laxipatem.lexiflash.data.model.JsonParser
import ir.laxipatem.lexiflash.data.model.VocabJson
import ir.laxipatem.lexiflash.domain.fsrs.CardMemory
import ir.laxipatem.lexiflash.domain.fsrs.Fsrs
import ir.laxipatem.lexiflash.domain.fsrs.Rating
import ir.laxipatem.lexiflash.domain.fsrs.ReviewResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Applies ratings through FSRS and persists the outcome atomically.
 *
 * Every review writes two rows in one transaction: the card's new memory state
 * and an immutable log entry. If either write fails the transaction rolls back,
 * so the queue and the history can never disagree.
 */
class ReviewRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val reviewDao get() = database.reviewDao()
    private val deckDao get() = database.deckDao()

    /** Cards due at [now], oldest due first. */
    suspend fun dueCards(now: Long, limit: Int = DEFAULT_QUEUE_LIMIT) =
        withContext(ioDispatcher) { deckDao.dueCards(now, limit) }

    suspend fun loadState(cardId: String): CardStateEntity? =
        withContext(ioDispatcher) { reviewDao.stateOf(cardId) }

    /**
     * Records one rating.
     *
     * @param cardId the card that was answered.
     * @param rating 1 Again, 2 Hard, 3 Good, 4 Easy.
     * @param nowMs the moment the learner answered.
     */
    suspend fun submitReview(
        cardId: String,
        rating: Int,
        nowMs: Long
    ): Result<ReviewOutcome> = withContext(ioDispatcher) {
        runCatching {
            require(Rating.isValid(rating)) { "rating out of range: $rating" }
            val existing = reviewDao.stateOf(cardId)
            val memory = existing?.toMemory()
            val stateBefore = existing?.state ?: 0
            val elapsedDays = Fsrs.elapsedDays(existing?.lastReviewAt ?: nowMs, nowMs)

            val result: ReviewResult = Fsrs.review(memory = memory, rating = rating, nowMs = nowMs)

            val state = CardStateEntity(
                cardId = cardId,
                stability = result.newMemory.stability,
                difficulty = result.newMemory.difficulty,
                state = result.newMemory.state,
                lastReviewAt = nowMs,
                dueAt = result.dueAtMs,
                reps = result.newMemory.reps,
                lapses = result.newMemory.lapses,
                seen = true
            )
            val log = ReviewLogEntity(
                cardId = cardId,
                rating = rating,
                reviewedAt = nowMs,
                elapsedDays = elapsedDays,
                scheduledDays = result.scheduledDays,
                stateBefore = stateBefore
            )
            reviewDao.recordReview(state, log)
            ReviewOutcome(result = result, stateBefore = stateBefore, elapsedDays = elapsedDays)
        }
    }

    /**
     * Turns a card's vocabulary column into meaning strings for the feedback UI.
     * Malformed JSON yields an empty list rather than an exception.
     */
    fun vocabularyOf(vocabJson: String): List<VocabJson> {
        if (vocabJson.isBlank()) return emptyList()
        val root = JsonParser.parseOrNull(vocabJson) ?: return emptyList()
        return root.asArrayOrNull().orEmpty().mapNotNull { item ->
            val word = item.string("word") ?: return@mapNotNull null
            VocabJson(word = word, meaning = item.string("meaning"))
        }
    }

    private fun CardStateEntity.toMemory() = CardMemory(
        stability = stability,
        difficulty = difficulty,
        state = state,
        lastReviewAtMs = lastReviewAt,
        reps = reps,
        lapses = lapses
    )

    companion object {
        const val DEFAULT_QUEUE_LIMIT = 500
    }
}

/** Everything the study screen needs after one rating. */
data class ReviewOutcome(
    val result: ReviewResult,
    val stateBefore: Int,
    val elapsedDays: Double
)
