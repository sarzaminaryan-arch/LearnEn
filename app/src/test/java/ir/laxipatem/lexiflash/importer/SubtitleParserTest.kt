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
    fun `srt strips basic markup`() {
        val input = """
            1
            00:00:01,000 --> 00:00:02,000
            <i>Italic</i> and {\an8}positioned text.
        """.trimIndent()
        val cues = SrtParser().parse(input)
        assertTrue("markup should be removed: ${cues[0].text}",
            !cues[0].text.contains("<i>") && !cues[0].text.contains("{"))
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
    fun `vtt strips inline voice and class markup`() {
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
    fun `ass detection looks for the script info header`() {
        assertTrue(AssParser().canParse("x.txt", "[Script Info]\nTitle: y"))
        assertTrue(AssParser().canParse("x.ass", ""))
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
    fun `txt keeps a numbered list without losing the text`() {
        val cues = TxtParser().parse("1. First sentence.\n2. Second sentence.")
        assertEquals(2, cues.size)
        assertEquals("First sentence.", cues[0].text)
    }

    @Test
    fun `txt detection matches the extension only`() {
        assertTrue(TxtParser().canParse("notes.txt", ""))
        assertTrue(!TxtParser().canParse("notes.srt", ""))
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
