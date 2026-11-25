package ir.laxipatem.lexiflash.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Splitting imported lines into contiguous, non-empty study parts. */
class PartSplitterTest {

    @Test
    fun `splits into the requested number of parts`() {
        val parts = PartSplitter.split((1..100).toList(), 10)
        assertEquals(10, parts.size)
        assertEquals(100, parts.sumOf { it.size })
    }

    @Test
    fun `parts are contiguous and preserve order`() {
        val source = (1..97).toList()
        val flat = PartSplitter.split(source, 7).flatMap { it }
        assertEquals(source, flat)
    }

    @Test
    fun `no part is empty`() {
        for (requested in 1..12) {
            val parts = PartSplitter.split((1..30).toList(), requested)
            assertTrue("requested $requested produced an empty part", parts.all { it.isNotEmpty() })
        }
    }

    @Test
    fun `part sizes differ by at most one`() {
        val parts = PartSplitter.split((1..103).toList(), 10)
        val sizes = parts.map { it.size }
        assertTrue("sizes $sizes", sizes.max() - sizes.min() <= 1)
    }

    @Test
    fun `requesting more parts than lines is clamped`() {
        val parts = PartSplitter.split((1..5).toList(), 10)
        assertEquals(5, parts.size)
    }

    @Test
    fun `a zero or negative request still yields one part`() {
        assertEquals(1, PartSplitter.split((1..10).toList(), 0).size)
        assertEquals(1, PartSplitter.split((1..10).toList(), -3).size)
    }

    @Test
    fun `an empty input yields no parts`() {
        assertTrue(PartSplitter.split(emptyList<Int>(), 10).isEmpty())
    }

    @Test
    fun `effective part count respects the line count`() {
        assertEquals(5, PartSplitter.effectivePartCount(totalLines = 5, requestedParts = 10))
        assertEquals(10, PartSplitter.effectivePartCount(totalLines = 500, requestedParts = 10))
        assertEquals(0, PartSplitter.effectivePartCount(totalLines = 0, requestedParts = 10))
    }

    @Test
    fun `timeline splitting keeps each part contiguous`() {
        val lines = (1..60).map { index ->
            ProcessedLine(
                text = "Line number $index is here.",
                startMs = index * 1_000L,
                endMs = index * 1_000L + 900L
            )
        }
        val parts = PartSplitter.splitByTimeline(lines, 6)
        assertEquals(6, parts.size)
        assertEquals(60, parts.sumOf { it.size })
        val flat = parts.flatMap { it }
        assertEquals(lines.map { it.text }, flat.map { it.text })
    }
}
