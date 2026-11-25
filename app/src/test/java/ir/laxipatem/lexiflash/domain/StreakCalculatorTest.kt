package ir.laxipatem.lexiflash.domain

import ir.laxipatem.lexiflash.domain.stats.StreakCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for consecutive-day streak arithmetic. */
class StreakCalculatorTest {

    private val today = 20_000L

    @Test
    fun `no activity means no streak`() {
        assertEquals(0, StreakCalculator.currentStreak(emptyList(), today))
        assertEquals(0, StreakCalculator.longestStreak(emptyList()))
    }

    @Test
    fun `activity today gives a streak of one`() {
        assertEquals(1, StreakCalculator.currentStreak(listOf(today), today))
    }

    @Test
    fun `a run ending today counts every day`() {
        val days = listOf(today - 3, today - 2, today - 1, today)
        assertEquals(4, StreakCalculator.currentStreak(days, today))
    }

    @Test
    fun `a streak survives when the last activity was yesterday`() {
        val days = listOf(today - 2, today - 1)
        assertEquals(2, StreakCalculator.currentStreak(days, today))
    }

    @Test
    fun `a gap of two days breaks the streak`() {
        val days = listOf(today - 5, today - 4, today - 3)
        assertEquals(0, StreakCalculator.currentStreak(days, today))
    }

    @Test
    fun `only the most recent run counts`() {
        val days = listOf(today - 10, today - 9, today - 8, today - 2, today - 1)
        assertEquals(2, StreakCalculator.currentStreak(days, today))
    }

    @Test
    fun `duplicate days do not inflate the streak`() {
        val days = listOf(today, today, today - 1, today - 1)
        assertEquals(2, StreakCalculator.currentStreak(days, today))
    }

    @Test
    fun `longest streak finds the best historical run`() {
        val days = listOf(100L, 101L, 102L, 103L, 200L, 201L)
        assertEquals(4, StreakCalculator.longestStreak(days))
    }

    @Test
    fun `longest streak of a single day is one`() {
        assertEquals(1, StreakCalculator.longestStreak(listOf(42L)))
    }

    @Test
    fun `longest streak ignores unsorted input`() {
        assertEquals(3, StreakCalculator.longestStreak(listOf(5L, 3L, 4L, 9L)))
    }

    @Test
    fun `reviewed today and yesterday are reported separately`() {
        val days = listOf(today - 1)
        assertFalse(StreakCalculator.reviewedToday(days, today))
        assertTrue(StreakCalculator.reviewedYesterday(days, today))

        val withToday = days + today
        assertTrue(StreakCalculator.reviewedToday(withToday, today))
    }
}
