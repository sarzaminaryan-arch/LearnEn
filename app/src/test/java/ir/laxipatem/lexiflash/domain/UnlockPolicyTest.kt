package ir.laxipatem.lexiflash.domain

import ir.laxipatem.lexiflash.domain.unlock.UnlockPolicy
import ir.laxipatem.lexiflash.domain.unlock.UnlockPolicy.PartAccess
import ir.laxipatem.lexiflash.domain.unlock.UnlockPolicy.PartStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the sequential part unlocking rules. */
class UnlockPolicyTest {

    private fun part(index: Int, cards: Int = 10, seen: Int = 0, score: Int? = null) =
        PartStatus(partIndex = index, cardCount = cards, seenCount = seen, bestScorePercent = score)

    private fun complete(index: Int) = part(index, seen = 10, score = 95)

    @Test
    fun `part one is always open`() {
        val access = UnlockPolicy.compute(listOf(part(1), part(2)), unlockAll = false)
        assertEquals(PartAccess.OPEN, access[0])
        assertEquals(PartAccess.LOCKED, access[1])
    }

    @Test
    fun `passing a part opens the next one`() {
        val access = UnlockPolicy.compute(listOf(complete(1), part(2)), unlockAll = false)
        assertEquals(PartAccess.COMPLETED, access[0])
        assertEquals(PartAccess.OPEN, access[1])
    }

    @Test
    fun `studying everything without a quiz pass does not open the next part`() {
        val access = UnlockPolicy.compute(
            listOf(part(1, seen = 10, score = null), part(2)),
            unlockAll = false
        )
        assertEquals(PartAccess.OPEN, access[0])
        assertEquals(PartAccess.LOCKED, access[1])
    }

    @Test
    fun `a quiz pass without studying everything does not open the next part`() {
        val access = UnlockPolicy.compute(
            listOf(part(1, seen = 9, score = 100), part(2)),
            unlockAll = false
        )
        assertEquals(PartAccess.LOCKED, access[1])
    }

    @Test
    fun `exactly eighty percent is enough`() {
        val access = UnlockPolicy.compute(
            listOf(part(1, seen = 10, score = 80), part(2)),
            unlockAll = false
        )
        assertEquals(PartAccess.COMPLETED, access[0])
        assertEquals(PartAccess.OPEN, access[1])
    }

    @Test
    fun `seventy nine percent is not enough`() {
        val access = UnlockPolicy.compute(
            listOf(part(1, seen = 10, score = 79), part(2)),
            unlockAll = false
        )
        assertEquals(PartAccess.OPEN, access[0])
        assertEquals(PartAccess.LOCKED, access[1])
    }

    @Test
    fun `unlock all opens every part`() {
        val parts = listOf(part(1), part(2), part(3), part(4))
        val access = UnlockPolicy.compute(parts, unlockAll = true)
        assertTrue(access.none { it == PartAccess.LOCKED })
    }

    @Test
    fun `unlocking is sequential and never skips a part`() {
        val parts = listOf(complete(1), complete(2), part(3), part(4), complete(5))
        val access = UnlockPolicy.compute(parts, unlockAll = false)
        assertEquals(
            listOf(
                PartAccess.COMPLETED,
                PartAccess.COMPLETED,
                PartAccess.OPEN,
                PartAccess.LOCKED,
                PartAccess.LOCKED
            ),
            access
        )
    }

    @Test
    fun `parts are evaluated in index order regardless of input order`() {
        val shuffled = listOf(complete(2), part(1), complete(3), part(8))
        val ordered = listOf(part(1), complete(2), complete(3), part(8))
        assertEquals(
            UnlockPolicy.compute(ordered, false),
            UnlockPolicy.compute(shuffled, false)
        )
    }

    @Test
    fun `an empty part never blocks progress`() {
        val access = UnlockPolicy.compute(
            listOf(part(1, cards = 0), part(2)),
            unlockAll = false
        )
        assertEquals(PartAccess.OPEN, access[1])
    }

    @Test
    fun `an empty deck returns no access entries`() {
        assertTrue(UnlockPolicy.compute(emptyList(), unlockAll = false).isEmpty())
    }

    @Test
    fun `completion ratio is clamped`() {
        assertEquals(0f, UnlockPolicy.completionRatio(part(1, cards = 0, seen = 0)), 0f)
        assertEquals(0.5f, UnlockPolicy.completionRatio(part(1, cards = 10, seen = 5)), 1e-6f)
        assertEquals(1f, UnlockPolicy.completionRatio(part(1, cards = 10, seen = 30)), 1e-6f)
    }

    @Test
    fun `the pass threshold is eighty percent`() {
        assertEquals(80, UnlockPolicy.PASS_PERCENT)
    }

    @Test
    fun `passed requires a positive card count`() {
        assertFalse(UnlockPolicy.passed(part(1, cards = 0, seen = 0, score = 100)))
        assertTrue(UnlockPolicy.passed(part(1, cards = 5, seen = 5, score = 100)))
    }
}
