package ir.laxipatem.lexiflash.domain.fsrs

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * FSRS-5 scheduler, implemented as pure Kotlin with no Android dependencies.
 *
 * Reference implementation and the algorithm description:
 * https://github.com/open-spaced-repetition/fsrs4anki/wiki/The-Algorithm
 * and the sibling ports py-fsrs / ts-fsrs.
 *
 * VERIFY against the open-spaced-repetition spec before every release: the
 * default weights, the decay/factor constants and the stability-update
 * formulas are the parts most likely to drift between FSRS revisions.
 *
 * Design notes
 * ------------
 * * This is FSRS-**5**, which uses 19 weights. FSRS v4.5 uses 17 weights and is
 *   deliberately *not* implemented here.
 * * Every public function takes an explicit clock value (`nowMs`). There is no
 *   hidden `System.currentTimeMillis()` inside the scheduler, so unit tests are
 *   deterministic.
 * * Same-day (short-term) learning steps are intentionally **not** modelled.
 *   A lapse schedules a short 10-minute interval and nothing below that, which
 *   matches the behaviour described in section 6 of this file.
 */
object Fsrs {

    /** Number of weights used by FSRS-5. */
    const val WEIGHT_COUNT = 19

    /**
     * Default FSRS-5 parameters, copied verbatim from the reference
     * implementation (`DEFAULT_WEIGHTS` in py-fsrs / ts-fsrs).
     */
    val DEFAULT_WEIGHTS: DoubleArray = doubleArrayOf(
        0.40255, // w0  - initial stability for Again
        1.18385, // w1  - initial stability for Hard
        3.173,   // w2  - initial stability for Good
        15.69105,// w3  - initial stability for Easy
        7.1949,  // w4  - initial difficulty base
        0.5345,  // w5  - initial difficulty decay per rating step
        1.4604,  // w6  - difficulty delta scale
        0.0046,  // w7  - difficulty mean-reversion weight
        1.54575, // w8  - recall stability growth
        0.1192,  // w9  - recall stability decay
        1.01925, // w10 - retrievability influence on recall
        1.9395,  // w11 - forget stability base
        0.11,    // w12 - forget stability difficulty exponent
        0.29605, // w13 - forget stability growth exponent
        2.2698,  // w14 - forget stability retrievability influence
        0.2315,  // w15 - Hard penalty
        2.9898,  // w16 - Easy bonus
        0.51655, // w17 - *FSRS-5* short-term stability weight (unused here)
        0.6621   // w18 - *FSRS-5* short-term stability offset (unused here)
    )

    /** Decay constant of the forgetting curve. */
    const val DECAY: Double = -0.5

    /** Scale factor that makes retrievability(S) equal the requested retention. */
    val FACTOR: Double = 19.0 / 81.0

    /** Default target retention used to schedule the next interval. */
    const val DEFAULT_REQUEST_RETENTION: Double = 0.9

    /** Upper bound on any scheduled interval: 100 years. */
    const val DEFAULT_MAXIMUM_INTERVAL: Int = 36500

    /** Interval granted when the learner answers "Again" on a review. */
    const val RELEARNING_MINUTES: Long = 10L

    private const val MILLIS_PER_MINUTE: Long = 60_000L

    /**
     * Retrievability after [elapsedDays] days for a card with [stability].
     *
     * R(t, S) = (1 + FACTOR * t / S) ^ DECAY
     */
    fun retrievability(elapsedDays: Double, stability: Double): Double {
        if (stability <= 0.0) return 0.0
        val t = max(0.0, elapsedDays)
        return (1.0 + FACTOR * t / stability).pow(DECAY)
    }

    /**
     * Interval, in whole days, that brings retrievability down to [retention].
     *
     * I(S) = S / FACTOR * (retention ^ (1 / DECAY) - 1)
     *
     * The result is clamped to `[1, maximumInterval]`.
     */
    fun nextInterval(
        stability: Double,
        retention: Double = DEFAULT_REQUEST_RETENTION,
        maximumInterval: Int = DEFAULT_MAXIMUM_INTERVAL
    ): Int {
        val safeStability = max(0.0, stability)
        val raw = safeStability / FACTOR * (retention.pow(1.0 / DECAY) - 1.0)
        if (!raw.isFinite()) return maximumInterval
        return raw.roundToInt().coerceIn(1, maximumInterval)
    }

    /** Initial stability for the first review of a card, `w[rating - 1]`, min 0.1. */
    fun initStability(rating: Int, weights: DoubleArray = DEFAULT_WEIGHTS): Double {
        requireRating(rating)
        return max(0.1, weights[rating - 1])
    }

    /** Initial difficulty, `clamp(w4 - exp(w5 * (rating - 1)) + 1, 1, 10)`. */
    fun initDifficulty(rating: Int, weights: DoubleArray = DEFAULT_WEIGHTS): Double {
        requireRating(rating)
        val w4 = weights[4]
        val w5 = weights[5]
        return (w4 - exp(w5 * (rating - 1)) + 1.0).coerceIn(1.0, 10.0)
    }

    /**
     * Difficulty after a review.
     *
     * delta = -w6 * (rating - 3)
     * D' = D + delta * (10 - D) / 9
     * D'' = w7 * initDifficulty(Easy) + (1 - w7) * D'   (mean reversion)
     */
    fun nextDifficulty(
        difficulty: Double,
        rating: Int,
        weights: DoubleArray = DEFAULT_WEIGHTS
    ): Double {
        requireRating(rating)
        val delta = -weights[6] * (rating - 3)
        val nextD = difficulty + delta * (10.0 - difficulty) / 9.0
        val meanReversion = weights[7] * initDifficulty(4, weights) + (1.0 - weights[7]) * nextD
        return meanReversion.coerceIn(1.0, 10.0)
    }

    /**
     * Stability after a successful recall.
     *
     * S' = S * (1 + exp(w8) * (11 - D) * S^(-w9) * (exp((1 - R) * w10) - 1)
     *          * hardPenalty * easyBonus)
     */
    fun nextRecallStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
        rating: Int,
        weights: DoubleArray = DEFAULT_WEIGHTS
    ): Double {
        requireRating(rating)
        val hardPenalty = if (rating == Rating.HARD) weights[15] else 1.0
        val easyBonus = if (rating == Rating.EASY) weights[16] else 1.0
        val growth = exp(weights[8]) *
            (11.0 - difficulty) *
            stability.pow(-weights[9]) *
            (exp((1.0 - retrievability) * weights[10]) - 1.0) *
            hardPenalty *
            easyBonus
        return stability * (1.0 + growth)
    }

    /**
     * Stability after a lapse.
     *
     * S'_forget = w11 * D^(-w12) * ((S + 1)^w13 - 1) * exp((1 - R) * w14)
     *
     * The result is capped at the previous stability, so a lapse can never make
     * a card easier than it already was.
     */
    fun nextForgetStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
        weights: DoubleArray = DEFAULT_WEIGHTS
    ): Double {
        val s = max(0.1, stability)
        val forgotten = weights[11] *
            difficulty.pow(-weights[12]) *
            ((s + 1.0).pow(weights[13]) - 1.0) *
            exp((1.0 - retrievability) * weights[14])
        val capped = min(forgotten, s)
        return max(0.1, capped)
    }

    /**
     * Applies a rating to a card.
     *
     * @param memory current memory state, or `null` for a card seen for the first time.
     * @param rating one of [Rating.AGAIN], [Rating.HARD], [Rating.GOOD], [Rating.EASY].
     * @param nowMs wall-clock time of the review, in epoch milliseconds.
     */
    fun review(
        memory: CardMemory?,
        rating: Int,
        nowMs: Long,
        requestRetention: Double = DEFAULT_REQUEST_RETENTION,
        maximumInterval: Int = DEFAULT_MAXIMUM_INTERVAL,
        weights: DoubleArray = DEFAULT_WEIGHTS
    ): ReviewResult {
        requireRating(rating)

        // --- First time the card is answered ---------------------------------
        if (memory == null) {
            val stability = initStability(rating, weights)
            val difficulty = initDifficulty(rating, weights)
            if (rating == Rating.AGAIN) {
                // A failed first exposure is re-shown in ten minutes, not a day.
                val dueAt = nowMs + RELEARNING_MINUTES * MILLIS_PER_MINUTE
                val newMemory = CardMemory(
                    stability = stability,
                    difficulty = difficulty,
                    state = CardState.LEARNING,
                    lastReviewAtMs = nowMs,
                    reps = 1,
                    lapses = 1
                )
                return ReviewResult(
                    newMemory = newMemory,
                    scheduledDays = 0.0,
                    scheduledMinutes = RELEARNING_MINUTES,
                    dueAtMs = dueAt
                )
            }
            val interval = nextInterval(stability, requestRetention, maximumInterval)
            val newMemory = CardMemory(
                stability = stability,
                difficulty = difficulty,
                state = CardState.REVIEW,
                lastReviewAtMs = nowMs,
                reps = 1,
                lapses = 0
            )
            return ReviewResult(
                newMemory = newMemory,
                scheduledDays = interval.toDouble(),
                scheduledMinutes = null,
                dueAtMs = nowMs + interval.toLong() * MILLIS_PER_MINUTE * 60L * 24L
            )
        }

        // --- Subsequent reviews ----------------------------------------------
        val elapsedDays = elapsedDays(memory.lastReviewAtMs, nowMs)
        val currentRetrievability = retrievability(elapsedDays, memory.stability)
        val newDifficulty = nextDifficulty(memory.difficulty, rating, weights)

        if (rating == Rating.AGAIN) {
            val newStability = nextForgetStability(
                difficulty = memory.difficulty,
                stability = memory.stability,
                retrievability = currentRetrievability,
                weights = weights
            )
            val dueAt = nowMs + RELEARNING_MINUTES * MILLIS_PER_MINUTE
            return ReviewResult(
                newMemory = CardMemory(
                    stability = newStability,
                    difficulty = newDifficulty,
                    state = CardState.RELEARNING,
                    lastReviewAtMs = nowMs,
                    reps = memory.reps + 1,
                    lapses = memory.lapses + 1
                ),
                scheduledDays = 0.0,
                scheduledMinutes = RELEARNING_MINUTES,
                dueAtMs = dueAt
            )
        }

        val newStability = nextRecallStability(
            difficulty = memory.difficulty,
            stability = memory.stability,
            retrievability = currentRetrievability,
            rating = rating,
            weights = weights
        )
        val interval = nextInterval(newStability, requestRetention, maximumInterval)
        return ReviewResult(
            newMemory = CardMemory(
                stability = newStability,
                difficulty = newDifficulty,
                state = CardState.REVIEW,
                lastReviewAtMs = nowMs,
                reps = memory.reps + 1,
                lapses = memory.lapses
            ),
            scheduledDays = interval.toDouble(),
            scheduledMinutes = null,
            dueAtMs = nowMs + interval.toLong() * MILLIS_PER_MINUTE * 60L * 24L
        )
    }

    /** Elapsed whole-ish days between two instants; never negative. */
    fun elapsedDays(fromMs: Long?, toMs: Long): Double {
        if (fromMs == null) return 0.0
        val delta = toMs - fromMs
        if (delta <= 0L) return 0.0
        return delta.toDouble() / (MILLIS_PER_MINUTE * 60.0 * 24.0)
    }

    private fun requireRating(rating: Int) {
        require(rating in Rating.MIN..Rating.MAX) {
            "rating must be between ${Rating.MIN} and ${Rating.MAX}, was $rating"
        }
    }
}

/** The four answer buttons, ordered from hardest to easiest. */
object Rating {
    const val AGAIN = 1
    const val HARD = 2
    const val GOOD = 3
    const val EASY = 4
    const val MIN = AGAIN
    const val MAX = EASY

    fun isValid(rating: Int): Boolean = rating in MIN..MAX
}

/** Persisted learning state of a single card. */
object CardState {
    const val NEW = 0
    const val LEARNING = 1
    const val REVIEW = 2
    const val RELEARNING = 3

    /** The value stored in the database after a rating of [Rating.AGAIN]. */
    const val FORGOTTEN = RELEARNING
}

/** Memory snapshot of a card, independent of storage. */
data class CardMemory(
    val stability: Double,
    val difficulty: Double,
    val state: Int,
    val lastReviewAtMs: Long?,
    val reps: Int,
    val lapses: Int
)

/** Outcome of applying one rating. */
data class ReviewResult(
    val newMemory: CardMemory,
    val scheduledDays: Double,
    /** Non-null only for short relearning steps, in minutes. */
    val scheduledMinutes: Long?,
    val dueAtMs: Long
)
