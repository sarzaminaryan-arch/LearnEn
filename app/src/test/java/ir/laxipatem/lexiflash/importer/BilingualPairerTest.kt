package ir.laxipatem.lexiflash.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Time-overlap pairing between an English and a Persian subtitle track. */
class BilingualPairerTest {

    private val pairer = BilingualPairer()

    @Test
    fun `identical timings pair one to one`() {
        val english = listOf(
            Cue(0L, 2_000L, "Good morning everyone."),
            Cue(2_000L, 4_000L, "The bus is late again.")
        )
        val persian = listOf(
            Cue(0L, 2_000L, "صبح بخیر به همه."),
            Cue(2_000L, 4_000L, "اتوبوس دوباره دیر است.")
        )
        val pairs = pairer.pair(english, persian)
        assertEquals(2, pairs.size)
        assertEquals("صبح بخیر به همه.", pairs[0].persianText)
        assertEquals("اتوبوس دوباره دیر است.", pairs[1].persianText)
    }

    @Test
    fun `overlap above thirty percent pairs`() {
        val english = listOf(Cue(0L, 10_000L, "A rather long English line here."))
        // 4s of a 10s span is 40% overlap.
        val persian = listOf(Cue(6_000L, 10_000L, "یک جمله‌ی فارسی کوتاه."))
        val pairs = pairer.pair(english, persian)
        assertNotNull(pairs[0].persianText)
    }

    @Test
    fun `overlap below thirty percent does not pair`() {
        val english = listOf(Cue(0L, 10_000L, "A rather long English line here."))
        // 2s of a 10s span is 20% overlap.
        val persian = listOf(Cue(8_000L, 10_000L, "یک جمله‌ی فارسی کوتاه."))
        val pairs = pairer.pair(english, persian)
        assertNull(pairs[0].persianText)
    }

    @Test
    fun `a persian cue already used is flagged as reused`() {
        val english = listOf(
            Cue(0L, 2_000L, "The first English line."),
            Cue(2_100L, 4_000L, "The second English line.")
        )
        val persian = listOf(Cue(0L, 4_000L, "یک خط فارسی برای هر دو."))
        val pairs = pairer.pair(english, persian)
        assertEquals(2, pairs.size)
        assertTrue("second pairing should be marked reused", pairs[1].reused)
    }

    @Test
    fun `an empty persian track leaves translations null`() {
        val pairs = pairer.pair(listOf(Cue(0L, 1_000L, "Only English here.")), emptyList())
        assertEquals(1, pairs.size)
        assertNull(pairs[0].persianText)
    }

    @Test
    fun `an empty english track yields nothing`() {
        assertTrue(pairer.pair(emptyList(), listOf(Cue(0L, 1_000L, "فقط فارسی"))).isEmpty())
    }

    @Test
    fun `positional pairing maps equal-length tracks`() {
        val english = listOf(Cue(null, null, "One."), Cue(null, null, "Two."))
        val persian = listOf(Cue(null, null, "یک."), Cue(null, null, "دو."))
        val pairs = pairer.pairByPosition(english, persian)
        assertEquals(2, pairs.size)
        assertEquals("یک.", pairs[0].persianText)
        assertEquals("دو.", pairs[1].persianText)
    }

    @Test
    fun `positional pairing stops at the shorter track`() {
        val english = listOf(Cue(null, null, "One."), Cue(null, null, "Two."))
        val persian = listOf(Cue(null, null, "یک."))
        assertEquals(1, pairer.pairByPosition(english, persian).size)
    }

    @Test
    fun `pairing preserves english order`() {
        val english = listOf(
            Cue(0L, 1_000L, "Alpha line is here."),
            Cue(1_000L, 2_000L, "Beta line is here."),
            Cue(2_000L, 3_000L, "Gamma line is here.")
        )
        val persian = listOf(
            Cue(2_000L, 3_000L, "خط گاما اینجاست."),
            Cue(0L, 1_000L, "خط آلفا اینجاست."),
            Cue(1_000L, 2_000L, "خط بتا اینجاست.")
        )
        val pairs = pairer.pair(english, persian)
        assertEquals(
            listOf("Alpha line is here.", "Beta line is here.", "Gamma line is here."),
            pairs.map { it.english.text }
        )
        assertEquals("خط آلفا اینجاست.", pairs[0].persianText)
    }

    @Test
    fun `overlap fraction is configurable`() {
        val strict = BilingualPairer(minOverlapFraction = 0.9)
        val english = listOf(Cue(0L, 10_000L, "A rather long English line here."))
        val persian = listOf(Cue(0L, 5_000L, "یک جمله‌ی فارسی کوتاه."))
        assertNull("50 percent overlap should fail a 90 percent rule", strict.pair(english, persian)[0].persianText)
    }
}
