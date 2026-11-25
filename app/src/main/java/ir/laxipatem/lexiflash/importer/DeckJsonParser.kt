package ir.laxipatem.lexiflash.importer

import ir.laxipatem.lexiflash.data.model.DeckJson
import ir.laxipatem.lexiflash.data.model.DeckJsonCodec
import ir.laxipatem.lexiflash.data.model.DeckValidationException

/**
 * Reads a deck.json document and flattens it into cues for the shared import
 * pipeline. Card ids, IPA, vocabulary and notes survive the round trip.
 */
class DeckJsonParser : SubtitleParser {

    /** Parsed deck, available after a successful [parse] call. */
    var lastDeck: DeckJson? = null
        private set

    override fun canParse(fileName: String, head: String): Boolean {
        if (fileName.endsWith(".json", ignoreCase = true)) return true
        val trimmed = head.trimStart()
        return trimmed.startsWith("{") && trimmed.contains("\"parts\"")
    }

    override fun parse(text: String): List<Cue> {
        val deck = try {
            DeckJsonCodec.parse(text)
        } catch (e: DeckValidationException) {
            throw IllegalArgumentException(e.message ?: "Invalid deck file", e)
        }
        lastDeck = deck
        return deck.parts.flatMap { part ->
            part.cards.map { card ->
                Cue(
                    startMs = card.startMs,
                    endMs = null,
                    text = if (card.persian.isNullOrBlank()) {
                        card.english
                    } else {
                        "${card.english}\t${card.persian}"
                    }
                )
            }
        }
    }
}
