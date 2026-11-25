package ir.laxipatem.lexiflash.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Format detection and parsing for the four subtitle formats. */
class SubtitleParserTest {

    // ---------------------------------------------------------------- SRT ---

    private val srt = """
        1
        00:00:01,000 --> 00:00:03,500
        I would like a cup of tea.

        2
        00:00:04,000 --> 00:00:06,000
        Of course, here you are.
    """.trimIndent()

    @Test
    fun `srt parses cues with timings`() {
        val cues = SrtParser().parse(srt)
        assertEquals(2, cues.size)
        assertEquals(1000L, cues[0].startMs)
        assertEquals(3500L, cues[0].endMs)
        assertEquals("I would like a cup of tea.", cues[0].text)
        assertEquals("Of course, here you are.", cues[1].text)
    }

    @Test
    fun `srt joins multi-line cue text with a space`() {
        val input = """
            1
            00:00:01,000 --> 00:00:02,000
            First line
            second line
        """.trimIndent()
        val cues = SrtParser().parse(input)
        assertEquals("First line second line", cues[0].text)
    }

    @Test
    fun `srt accepts a period as the millisecond separator`() {
        val input = """
            1
            00:00:01.250 --> 00:00:02.750
            A sentence with a dot separator.
        """.trimIndent()
        val cues = SrtParser().parse(input)
        assertEquals(1250L, cues[0].startMs)
        assertEquals(2750L, cues[0].endMs)
    }

    @Test
    fun `srt keeps markup so the preprocessor can strip it once`() {
        // Tag removal is the Preprocessor's job: parsing only cuts cues apart, so
        // the same cleanup runs whether text came from SRT, VTT or plain text.
        val input = """
            1
            00:00:01,000 --> 00:00:02,000
            <i>Italic</i> and {\an8}positioned text.
        """.trimIndent()
        val cue = SrtParser().parse(input).single()
        assertTrue("the parser must not mangle the text", cue.text.contains("Italic"))
        val cleaned = Preprocessor().cleanText(cue.text)
        assertTrue("preprocessor leaves no tags: $cleaned", !cleaned.contains("<i>"))
        assertTrue("preprocessor leaves no overrides: $cleaned", !cleaned.contains("{"))
    }

    @Test
    fun `srt detection uses the extension`() {
        assertTrue(SrtParser().canParse("movie.srt", ""))
        assertTrue(SrtParser().canParse("MOVIE.SRT", ""))
        assertTrue(!SrtParser().canParse("movie.vtt", ""))
    }

    @Test
    fun `srt ignores malformed blocks`() {
        val broken = """
            not a number
            no timings here
            Just some text.
        """.trimIndent()
        assertTrue(SrtParser().parse(broken).isEmpty())
    }

    // ---------------------------------------------------------------- VTT ---

    private val vtt = """
        WEBVTT

        00:00:01.000 --> 00:00:03.000
        The train arrives at nine.

        00:00:04.000 --> 00:00:05.500
        We should hurry.
    """.trimIndent()

    @Test
    fun `vtt parses cues`() {
        val cues = VttParser().parse(vtt)
        assertEquals(2, cues.size)
        assertEquals(1000L, cues[0].startMs)
        assertEquals("The train arrives at nine.", cues[0].text)
    }

    @Test
    fun `vtt detection requires the header or extension`() {
        assertTrue(VttParser().canParse("anything.txt", "WEBVTT\n\n00:00.000 --> 00:01.000\nHi"))
        assertTrue(VttParser().canParse("clip.vtt", ""))
        assertTrue(!VttParser().canParse("clip.srt", "1\n00:00:01,000 --> 00:00:02,000\nHi"))
    }

    @Test
    fun `vtt strips inline voice markup without leaking the speaker name`() {
        val input = """
            WEBVTT

            00:00:01.000 --> 00:00:02.000
            <v Mary>Hello there.</v>
        """.trimIndent()
        val cues = VttParser().parse(input)
        assertEquals("Hello there.", cues[0].text)
    }

    @Test
    fun `vtt drops note blocks`() {
        val input = """
            WEBVTT

            NOTE this is a comment

            00:00:01.000 --> 00:00:02.000
            Real text here.
        """.trimIndent()
        val cues = VttParser().parse(input)
        assertEquals(1, cues.size)
        assertEquals("Real text here.", cues[0].text)
    }

    // ---------------------------------------------------------------- ASS ---

    private val ass = """
        [Script Info]
        Title: Test

        [Events]
        Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
        Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,The library closes at six.
        Dialogue: 0,0:00:04.00,0:00:06.00,Default,,0,0,0,,{\i1}Please be quiet.{\i0}
    """.trimIndent()

    @Test
    fun `ass parses dialogue lines`() {
        val cues = AssParser().parse(ass)
        assertEquals(2, cues.size)
        assertEquals(1000L, cues[0].startMs)
        assertEquals("The library closes at six.", cues[0].text)
    }

    @Test
    fun `ass strips override tags`() {
        val cues = AssParser().parse(ass)
        assertEquals("Please be quiet.", cues[1].text)
    }

    @Test
    fun `ass detection requires both the script info and events sections`() {
        assertTrue(AssParser().canParse("x.txt", "[Script Info]\nTitle: y\n\n[Events]\nFormat: ..."))
        // A stray [Script Info] alone is not enough to claim the file.
        assertTrue(!AssParser().canParse("x.txt", "[Script Info]\nTitle: y"))
        assertTrue(AssParser().canParse("x.ass", ""))
        assertTrue(AssParser().canParse("x.ssa", ""))
        assertTrue(!AssParser().canParse("x.srt", "1\n00:00:01,000 --> 00:00:02,000\nHi"))
    }

    @Test
    fun `ass converts hours correctly`() {
        val input = """
            [Script Info]
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,1:02:03.50,1:02:04.50,Default,,0,0,0,,Late line.
        """.trimIndent()
        val cues = AssParser().parse(input)
        assertEquals(((1 * 60 + 2) * 60 + 3) * 1000L + 500L, cues[0].startMs)
    }

    // ---------------------------------------------------------------- TXT ---

    @Test
    fun `txt splits on blank lines`() {
        val cues = TxtParser().parse("First sentence.\n\nSecond sentence.\n\n\nThird sentence.")
        assertEquals(3, cues.size)
        assertNull(cues[0].startMs)
    }

    @Test
    fun `txt strips a list marker but keeps the sentence`() {
        val cues = TxtParser().parse("1. First sentence.\n2. Second sentence.")
        assertEquals(2, cues.size)
        assertEquals("First sentence.", cues[0].text)
    }

    @Test
    fun `txt is the catch-all parser for anything without a marker`() {
        assertTrue(TxtParser().canParse("notes.txt", ""))
        // It accepts an unmarked file even with a foreign extension; ImportService
        // tries the marked parsers first, so this only runs as a last resort.
        assertTrue(TxtParser().canParse("notes.srt", ""))
        assertTrue(TxtParser().canParse("notes.unknown", "just some words on a line"))
        // But real structural markers are refused.
        assertTrue(!TxtParser().canParse("x.srt", "1\n00:00:01,000 --> 00:00:02,000\nHi"))
        assertTrue(!TxtParser().canParse("x.ass", "[Script Info]\n[Events]"))
        assertTrue(!TxtParser().canParse("deck.json", "{\"parts\": []}"))
    }

    // ------------------------------------------------------------ Pipeline ---

    @Test
    fun `every parser rejects an empty file`() {
        for (parser in listOf(SrtParser(), VttParser(), AssParser(), TxtParser())) {
            assertTrue(
                "${parser::class.simpleName} returned cues for empty input",
                parser.parse("").isEmpty()
            )
        }
    }

    @Test
    fun `normalizeNewlines handles CRLF and CR`() {
        assertEquals("a\nb\nc", normalizeNewlines("a\r\nb\rc"))
    }
}
