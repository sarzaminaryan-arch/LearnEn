package ir.laxipatem.lexiflash.importer

import ir.laxipatem.lexiflash.data.model.DeckJsonCodec
import ir.laxipatem.lexiflash.data.model.DeckValidationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CSV import and the deck.json interchange format. */
class CsvAndDeckJsonTest {

    // ----------------------------------------------------------------- CSV ---

    @Test
    fun `csv reads two columns separated by a tab`() {
        val csv = "english\tpersian\nHello there friend.\tسلام دوست من."
        val cues = CsvParser().parse(csv)
        assertEquals(1, cues.size)
        assertEquals("Hello there friend.\tسلام دوست من.", cues[0].text)
    }

    @Test
    fun `csv reads comma separated values`() {
        val csv = "Hello there friend.,سلام دوست من."
        val cues = CsvParser().parse(csv)
        assertEquals(1, cues.size)
        assertEquals("Hello there friend.\tسلام دوست من.", cues[0].text)
    }

    @Test
    fun `csv reads semicolon separated values`() {
        val csv = "Hello there friend.;سلام دوست من."
        val cues = CsvParser().parse(csv)
        assertEquals(1, cues.size)
    }

    @Test
    fun `csv drops a recognised header row`() {
        val csv = "english,persian\nHello there friend.,سلام دوست من."
        assertEquals(1, CsvParser().parse(csv).size)
    }

    @Test
    fun `csv keeps a row whose first cell is only English`() {
        val csv = "Just one column here."
        val cues = CsvParser().parse(csv)
        assertEquals("Just one column here.", cues[0].text)
    }

    @Test
    fun `csv handles quoted fields containing the delimiter`() {
        val csv = "\"Hello, my friend.\",\"سلام، دوست من.\""
        val cues = CsvParser().parse(csv)
        assertEquals(1, cues.size)
        assertEquals("Hello, my friend.\tسلام، دوست من.", cues[0].text)
    }

    @Test
    fun `csv handles quoted fields containing newlines`() {
        val csv = "\"Hello there,\nmy friend.\",\"سلام دوست من.\""
        val cues = CsvParser().parse(csv)
        assertEquals(1, cues.size)
        assertTrue("keeps the second half of the field: ${cues[0].text}",
            cues[0].text.contains("my friend."))
        assertTrue("keeps the Persian column: ${cues[0].text}",
            cues[0].text.contains("سلام دوست من."))
    }

    @Test
    fun `csv does not tear a quoted newline into two cards`() {
        // Regression: splitting on newlines before honouring quotes produced two
        // half-sentences instead of one bilingual row.
        val csv = "\"First part,\nsecond part.\",\"بخش اول،\nبخش دوم.\""
        assertEquals(1, CsvParser().parse(csv).size)
    }

    @Test
    fun `csv skips blank lines`() {
        val csv = "First row here.\n\n\nSecond row here."
        assertEquals(2, CsvParser().parse(csv).size)
    }

    @Test
    fun `csv detection matches the extension`() {
        assertTrue(CsvParser().canParse("pairs.csv", ""))
        assertTrue(!CsvParser().canParse("pairs.srt", ""))
    }

    @Test
    fun `csv escapes a doubled quote`() {
        val row = CsvParser().splitRow("\"He said \"\"hi\"\" to me.\",other", ',')
        assertEquals("He said \"hi\" to me.", row[0])
        assertEquals("other", row[1])
    }

    // ------------------------------------------------------------ deck.json ---

    private val minimalDeck = """
        {
          "version": 1,
          "title": "Test deck",
          "source_lang": "en",
          "target_lang": "fa",
          "license": "CC BY 2.0 FR",
          "parts": [
            {
              "index": 1,
              "cards": [
                { "id": "a", "english": "The first card.", "persian": "کارت اول.", "vocab": [] },
                { "id": "b", "english": "The second card.", "persian": "کارت دوم.", "vocab": [] }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `deck json parses into the model`() {
        val deck = DeckJsonCodec.parse(minimalDeck)
        assertEquals(1, deck.version)
        assertEquals("Test deck", deck.title)
        assertEquals("en", deck.sourceLang)
        assertEquals("fa", deck.targetLang)
        assertEquals(1, deck.parts.size)
        assertEquals(2, deck.cardCount)
    }

    @Test
    fun `deck json parser produces cues`() {
        val parser = DeckJsonParser()
        val cues = parser.parse(minimalDeck)
        assertEquals(2, cues.size)
        assertEquals("The first card.\tکارت اول.", cues[0].text)
        assertNotNull(parser.lastDeck)
    }

    @Test
    fun `deck json detection trusts the extension, then sniffs the content`() {
        // The extension wins: a .json file is a deck candidate whatever it holds,
        // and a malformed one produces a readable validation error on parse.
        assertTrue(DeckJsonParser().canParse("deck.json", ""))
        assertTrue(DeckJsonParser().canParse("x.json", minimalDeck))
        assertTrue(DeckJsonParser().canParse("x.json", "{\"a\": 1}"))

        // Without the extension, the content has to announce a parts array.
        assertTrue(DeckJsonParser().canParse("upload.txt", minimalDeck))
        assertTrue(!DeckJsonParser().canParse("upload.txt", "{\"a\": 1}"))
        assertTrue(!DeckJsonParser().canParse("upload.txt", "just words"))
    }

    @Test(expected = DeckValidationException::class)
    fun `deck json rejects a missing root`() {
        DeckJsonCodec.parse("")
    }

    @Test(expected = DeckValidationException::class)
    fun `deck json rejects invalid json`() {
        DeckJsonCodec.parse("{not json}")
    }

    @Test(expected = DeckValidationException::class)
    fun `deck json rejects a future version`() {
        DeckJsonCodec.parse(minimalDeck.replace("\"version\": 1", "\"version\": 99"))
    }

    @Test(expected = DeckValidationException::class)
    fun `deck json rejects a missing title`() {
        DeckJsonCodec.parse(minimalDeck.replace("\"title\": \"Test deck\",", ""))
    }

    @Test(expected = DeckValidationException::class)
    fun `deck json rejects a missing parts array`() {
        DeckJsonCodec.parse("""{"version":1,"title":"T","source_lang":"en","target_lang":"fa","license":"x"}""")
    }

    @Test
    fun `deck json round trips through the writer`() {
        val deck = DeckJsonCodec.parse(minimalDeck)
        val written = DeckJsonCodec.toJson(deck).toJsonString()
        val reparsed = DeckJsonCodec.parse(written)
        assertEquals(deck.cardCount, reparsed.cardCount)
        assertEquals(deck.title, reparsed.title)
        assertEquals(deck.parts[0].cards[1].english, reparsed.parts[0].cards[1].english)
    }

    @Test
    fun `deck json keeps optional fields when present`() {
        val withExtras = """
            {
              "version": 1,
              "title": "T",
              "source_lang": "en",
              "target_lang": "fa",
              "license": "CC BY 2.0 FR",
              "parts": [ { "index": 1, "cards": [
                { "id": "a", "english": "Sentence here.", "persian": "جمله اینجاست.",
                  "ipa": "ˈsɛntəns", "difficulty": 0.4, "reviewed": true,
                  "vocab": [{"word":"sentence","meaning":"جمله"}], "note":"A2" }
              ] } ]
            }
        """.trimIndent()
        val card = DeckJsonCodec.parse(withExtras).parts[0].cards[0]
        assertEquals("ˈsɛntəns", card.ipa)
        assertEquals(0.4, card.difficulty!!, 1e-9)
        assertTrue(card.reviewed)
        assertEquals("sentence", card.vocab[0].word)
        assertEquals("جمله", card.vocab[0].meaning)
        assertEquals("A2", card.note)
    }

    @Test
    fun `a card without persian is kept for translation`() {
        val pending = minimalDeck.replace("\"persian\": \"کارت اول.\", ", "")
        val cue = DeckJsonParser().parse(pending)[0]
        assertEquals("The first card.", cue.text)
        assertNull(DeckJsonParser().parse(pending)[0].startMs)
    }

    @Test
    fun `parseResult reports failure without throwing`() {
        val result = DeckJsonCodec.parseResult("{broken")
        assertTrue(result.isFailure)
    }

    @Test
    fun `parseResult reports success for a valid deck`() {
        assertTrue(DeckJsonCodec.parseResult(minimalDeck).isSuccess)
    }
}
