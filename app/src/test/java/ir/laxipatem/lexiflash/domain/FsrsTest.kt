package ir.laxipatem.lexiflash.domain

import ir.laxipatem.lexiflash.domain.fsrs.CardState
import ir.laxipatem.lexiflash.domain.fsrs.Fsrs
import ir.laxipatem.lexiflash.domain.fsrs.Rating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioural tests for the FSRS-5 scheduler.
 *
 * The assertions describe the specification rather than the current
 * implementation: if a formula changes, one of these should fail.
 */
class FsrsTest {

    private val day = 86_400_000L
    private val now = 1_700_000_000_000L

    @Test
    fun `first review uses the initial stability of each rating`() {
        val weights = Fsrs.DEFAULT_WEIGHTS
        for (rating in Rating.AGAIN..Rating.EASY) {
            val result = Fsrs.review(memory = null, rating = rating, nowMs = now)
            assertEquals(
                "stability for rating $rating",
                weights[rating - 1],
                result.newMemory.stability,
                1e-9
            )
            assertEquals(1, result.newMemory.reps)
        }
    }

    @Test
    fun `first review difficulty is clamped into one to ten`() {
        for (rating in Rating.AGAIN..Rating.EASY) {
            val result = Fsrs.review(memory = null, rating = rating, nowMs = now)
            assertTrue(result.newMemory.difficulty in 1.0..10.0)
        }
    }

    @Test
    fun `a first again review is relearned in ten minutes`() {
        val result = Fsrs.review(memory = null, rating = Rating.AGAIN, nowMs = now)
        assertEquals(10L, result.scheduledMinutes)
        assertEquals(now + 10 * 60_000L, result.dueAtMs)
        assertEquals(0.0, result.scheduledDays, 1e-9)
        assertEquals(CardState.LEARNING, result.newMemory.state)
        assertEquals(1, result.newMemory.lapses)
    }

    @Test
    fun `retrievability is one at zero elapsed days`() {
        assertEquals(1.0, Fsrs.retrievability(0.0, 10.0), 1e-9)
    }

    @Test
    fun `retrievability equals the target retention at the stability horizon`() {
        val stability = 12.0
        // By construction R(t = S) = (1 + FACTOR)^DECAY == 0.9
        assertEquals(0.9, Fsrs.retrievability(stability, stability), 1e-6)
    }

    @Test
    fun `retrievability decreases monotonically`() {
        var previous = 1.0
        for (days in 0..40) {
            val value = Fsrs.retrievability(days.toDouble(), 8.0)
            assertTrue("R must not increase at day $days", value <= previous + 1e-12)
            previous = value
        }
    }

    @Test
    fun `intervals grow with consecutive good reviews`() {
        var memory: ir.laxipatem.lexiflash.domain.fsrs.CardMemory? = null
        var clock = now
        var previousInterval = 0.0
        repeat(6) {
            val result = Fsrs.review(memory = memory, rating = Rating.GOOD, nowMs = clock)
            memory = result.newMemory
            clock = result.dueAtMs
            if (previousInterval > 0.0) {
                assertTrue(
                    "interval should grow: $previousInterval -> ${result.scheduledDays}",
                    result.scheduledDays > previousInterval
                )
            }
            previousInterval = result.scheduledDays
        }
        assertNotNull(memory)
    }

    @Test
    fun `again after a successful review lowers stability and raises lapses`() {
        val first = Fsrs.review(memory = null, rating = Rating.GOOD, nowMs = now)
        val second = Fsrs.review(
            memory = first.newMemory,
            rating = Rating.AGAIN,
            nowMs = now + 5 * day
        )
        assertTrue(
            "stability must fall: ${first.newMemory.stability} -> ${second.newMemory.stability}",
            second.newMemory.stability <= first.newMemory.stability
        )
        assertEquals(first.newMemory.lapses + 1, second.newMemory.lapses)
        assertEquals(CardState.RELEARNING, second.newMemory.state)
        assertEquals(10L, second.scheduledMinutes)
    }

    @Test
    fun `hard is never longer than good which is never longer than easy`() {
        val memory = Fsrs.review(memory = null, rating = Rating.GOOD, nowMs = now).newMemory
        val reviewAt = now + 4 * day
        val hard = Fsrs.review(memory, Rating.HARD, reviewAt).scheduledDays
        val good = Fsrs.review(memory, Rating.GOOD, reviewAt).scheduledDays
        val easy = Fsrs.review(memory, Rating.EASY, reviewAt).scheduledDays
        assertTrue("hard <= good ($hard, $good)", hard <= good)
        assertTrue("good <= easy ($good, $easy)", good <= easy)
    }

    @Test
    fun `difficulty stays within one and ten over many reviews`() {
        var memory: ir.laxipatem.lexiflash.domain.fsrs.CardMemory? = null
        var clock = now
        for (rating in listOf(1, 4, 1, 4, 1, 4, 2, 3, 1, 4)) {
            val result = Fsrs.review(memory = memory, rating = rating, nowMs = clock)
            assertTrue(
                "difficulty ${result.newMemory.difficulty} out of range",
                result.newMemory.difficulty in 1.0..10.0
            )
            memory = result.newMemory
            clock = result.dueAtMs
        }
    }

    @Test
    fun `intervals are capped at the maximum`() {
        val interval = Fsrs.nextInterval(stability = 1_000_000.0, maximumInterval = 36500)
        assertEquals(36500, interval)
    }

    @Test
    fun `intervals are at least one day`() {
        assertEquals(1, Fsrs.nextInterval(stability = 0.0001))
    }

    @Test
    fun `elapsed days is never negative`() {
        assertEquals(0.0, Fsrs.elapsedDays(now, now - day), 1e-9)
        assertEquals(0.0, Fsrs.elapsedDays(null, now), 1e-9)
        assertEquals(1.0, Fsrs.elapsedDays(now, now + day), 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an out of range rating is rejected`() {
        Fsrs.review(memory = null, rating = 0, nowMs = now)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a rating above four is rejected`() {
        Fsrs.review(memory = null, rating = 5, nowMs = now)
    }

    @Test
    fun `good reviews do not schedule less than a day except on again`() {
        val result = Fsrs.review(memory = null, rating = Rating.GOOD, nowMs = now)
        assertNull(result.scheduledMinutes)
        assertTrue(result.scheduledDays >= 1.0)
    }

    @Test
    fun `the weight array holds the documented nineteen values`() {
        assertEquals(19, Fsrs.DEFAULT_WEIGHTS.size)
        assertEquals(0.40255, Fsrs.DEFAULT_WEIGHTS[0], 1e-9)
        assertEquals(0.6621, Fsrs.DEFAULT_WEIGHTS[18], 1e-9)
        assertEquals(19.0 / 81.0, Fsrs.FACTOR, 1e-12)
        assertEquals(-0.5, Fsrs.DECAY, 1e-12)
    }
}
