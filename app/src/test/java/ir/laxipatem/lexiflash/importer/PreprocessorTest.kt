package ir.laxipatem.lexiflash.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Text cleaning, merging and filtering before pairing. */
class PreprocessorTest {

    private val preprocessor = Preprocessor()

    @Test
    fun `strips html tags`() {
        assertEquals("Hello world.", preprocessor.cleanText("<i>Hello</i> world."))
    }

    @Test
    fun `strips ass override blocks`() {
        assertEquals("Styled text.", preprocessor.cleanText("{\\an8}Styled {\\b1}text."))
    }

    @Test
    fun `removes bracketed sound cues`() {
        assertEquals("He left.", preprocessor.cleanText("[door slams] He left."))
    }

    @Test
    fun `removes parenthetical sound words`() {
        assertEquals("That is funny.", preprocessor.cleanText("That is funny. (laughs)"))
    }

    @Test
    fun `removes music symbols`() {
        assertEquals("La la.", preprocessor.cleanText("♪ La la. ♫"))
    }

    @Test
    fun `removes a speaker label`() {
        assertEquals("Where are you going?", preprocessor.cleanText("MARY: Where are you going?"))
    }

    @Test
    fun `decodes common html entities`() {
        assertEquals("Tom & Jerry.", preprocessor.cleanText("Tom &amp; Jerry."))
    }

    @Test
    fun `collapses repeated whitespace`() {
        assertEquals("One two three.", preprocessor.cleanText("One    two\t three."))
    }

    @Test
    fun `word count ignores extra spaces`() {
        assertEquals(3, preprocessor.wordCount("  one   two three "))
    }

    @Test
    fun `too short lines are unusable`() {
        assertFalse(preprocessor.isUsable("Yes."))
    }

    @Test
    fun `too long lines are unusable`() {
        val long = (1..40).joinToString(" ") { "word$it" } + "."
        assertFalse(preprocessor.isUsable(long))
    }

    @Test
    fun `a normal sentence is usable`() {
        assertTrue(preprocessor.isUsable("This is a perfectly ordinary sentence."))
    }

    @Test
    fun `sentence completeness is detected`() {
        assertTrue(preprocessor.looksComplete("This ends properly."))
        assertTrue(preprocessor.looksComplete("A question?"))
        assertTrue(preprocessor.looksComplete("Persian question؟"))
        assertFalse(preprocessor.looksComplete("this does not end well"))
    }

    @Test
    fun `dedupe key ignores case and punctuation`() {
        assertEquals(
            preprocessor.dedupeKey("Hello, World!"),
            preprocessor.dedupeKey("hello world")
        )
    }

    @Test
    fun `process drops unusable cues and records timings`() {
        val cues = listOf(
            Cue(0L, 1_000L, "Yes."),
            Cue(1_000L, 3_000L, "This sentence is long enough to keep."),
            // A wide gap keeps the music cue from being merged onto the previous
            // line; it then disappears because only music symbols remain.
            Cue(9_000L, 10_000L, "♪ ♫ ♪")
        )
        val lines = preprocessor.process(cues)
        assertEquals(1, lines.size)
        assertEquals("This sentence is long enough to keep.", lines[0].text)
        assertEquals(1_000L, lines[0].startMs)
    }

    @Test
    fun `process merges a split sentence`() {
        val cues = listOf(
            Cue(0L, 1_000L, "I was going to the"),
            Cue(1_200L, 2_500L, "market when I met her.")
        )
        val lines = preprocessor.process(cues)
        assertEquals(1, lines.size)
        assertEquals("I was going to the market when I met her.", lines[0].text)
    }

    @Test
    fun `process does not merge across a long gap`() {
        val cues = listOf(
            Cue(0L, 1_000L, "The first complete thought is"),
            Cue(9_000L, 10_000L, "another complete thought here.")
        )
        val lines = preprocessor.process(cues)
        assertEquals(2, lines.size)
    }

    @Test
    fun `process removes duplicates`() {
        val cues = listOf(
            Cue(0L, 1_000L, "The very same sentence appears twice."),
            Cue(2_000L, 3_000L, "The very same sentence appears twice.")
        )
        assertEquals(1, preprocessor.process(cues).size)
    }

    @Test
    fun `process keeps order`() {
        val cues = listOf(
            Cue(0L, 1_000L, "The first sentence in the file."),
            Cue(2_000L, 3_000L, "The second sentence in the file."),
            Cue(4_000L, 5_000L, "The third sentence in the file.")
        )
        val lines = preprocessor.process(cues)
        assertEquals(listOf("first", "second", "third"), lines.map { it.text.split(" ")[1] })
    }
}
