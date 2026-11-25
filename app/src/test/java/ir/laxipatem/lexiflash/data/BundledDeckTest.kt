package ir.laxipatem.lexiflash.data

import ir.laxipatem.lexiflash.data.model.DeckJsonCodec
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the decks shipped inside the APK.
 *
 * The app falls back to `sample_deck.json` whenever the generated core deck is
 * absent, so a malformed sample deck would leave a fresh install with nothing to
 * study. These tests parse the real asset files with the production parser.
 */
class BundledDeckTest {

    private val assetsDir = File("src/main/assets/content")

    private fun readAsset(name: String): String {
        val file = File(assetsDir, name)
        assertTrue("missing asset ${file.absolutePath}", file.isFile)
        return file.readText(Charsets.UTF_8)
    }

    @Test
    fun `the sample deck exists`() {
        assertTrue(File(assetsDir, "sample_deck.json").isFile)
    }

    @Test
    fun `the sample deck parses with the production parser`() {
        val deck = DeckJsonCodec.parse(readAsset("sample_deck.json"))
        assertEquals(1, deck.version)
        assertEquals("en", deck.sourceLang)
        assertEquals("fa", deck.targetLang)
        assertTrue("sample deck should hold at least 20 cards", deck.cardCount >= 20)
    }

    @Test
    fun `the sample deck has at least two parts`() {
        val deck = DeckJsonCodec.parse(readAsset("sample_deck.json"))
        assertTrue(deck.parts.size >= 2)
    }

    @Test
    fun `every sample card carries both sentences`() {
        val deck = DeckJsonCodec.parse(readAsset("sample_deck.json"))
        for (card in deck.allCards()) {
            assertTrue("card ${card.id} has no English", card.english.isNotBlank())
            assertTrue("card ${card.id} has no Persian", !card.persian.isNullOrBlank())
        }
    }

    @Test
    fun `every sample card has a Persian translation containing Persian letters`() {
        val deck = DeckJsonCodec.parse(readAsset("sample_deck.json"))
        for (card in deck.allCards()) {
            val persian = card.persian.orEmpty()
            assertTrue(
                "card ${card.id} Persian looks untranslated: $persian",
                persian.any { it in '\u0600'..'\u06FF' }
            )
        }
    }

    @Test
    fun `sample card ids are unique`() {
        val deck = DeckJsonCodec.parse(readAsset("sample_deck.json"))
        val ids = deck.allCards().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `sample difficulty values are within range`() {
        val deck = DeckJsonCodec.parse(readAsset("sample_deck.json"))
        for (card in deck.allCards()) {
            card.difficulty?.let {
                assertTrue("card ${card.id} difficulty $it", it in 0.0..1.0)
            }
        }
    }

    @Test
    fun `the generated core deck parses when it is present`() {
        val core = File(assetsDir, "core_deck.json")
        org.junit.Assume.assumeTrue("core_deck.json has not been generated", core.isFile)
        val deck = DeckJsonCodec.parse(core.readText(Charsets.UTF_8))
        assertTrue("core deck should hold at least 300 cards", deck.cardCount >= 300)
        assertTrue("core deck should have 10 parts", deck.parts.size == 10)
        for (part in deck.parts) {
            assertTrue(
                "part ${part.index} is too small: ${part.cards.size}",
                part.cards.size >= 30
            )
        }
    }

    @Test
    fun `the sample deck round trips through the writer unchanged`() {
        val original = DeckJsonCodec.parse(readAsset("sample_deck.json"))
        val encoded = DeckJsonCodec.encode(original)
        val reparsed = DeckJsonCodec.parse(encoded)
        assertEquals(original.cardCount, reparsed.cardCount)
        assertEquals(original.parts.size, reparsed.parts.size)
        assertEquals(
            original.allCards().map { it.english },
            reparsed.allCards().map { it.english }
        )
    }
}
