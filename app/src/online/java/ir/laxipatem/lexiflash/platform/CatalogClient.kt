package ir.laxipatem.lexiflash.platform

import android.util.Log
import ir.laxipatem.lexiflash.data.model.JsonParser
import ir.laxipatem.lexiflash.data.model.JsonValue
import ir.laxipatem.lexiflash.online.CatalogDefaults
import java.io.IOException

/**
 * Reads the remote deck catalogue.
 *
 * Catalogue format (documented in `docs/DECK_FORMAT.md`):
 * ```json
 * { "version": 1,
 *   "decks": [ { "id": "...", "title": "...", "description": "...",
 *                "url": "https://.../deck.json", "card_count": 300 } ] }
 * ```
 * Every entry whose URL is not HTTPS is skipped rather than rejected, so one
 * bad row cannot take the whole catalogue down.
 */
class CatalogClient {

    suspend fun fetch(url: String): Result<List<CatalogEntry>> =
        HttpFetcher.getText(url, CatalogDefaults.MAX_DOWNLOAD_BYTES).mapCatching { response ->
            parseCatalogue(response.body)
        }

    suspend fun downloadDeck(url: String): Result<String> =
        HttpFetcher.getText(url, CatalogDefaults.MAX_DOWNLOAD_BYTES).mapCatching { response ->
            if (response.body.isBlank()) {
                throw IOException("The downloaded deck file is empty.")
            }
            response.body
        }

    /** Parses and validates catalogue JSON; throws [IOException] on bad input. */
    fun parseCatalogue(text: String): List<CatalogEntry> {
        Log.d("LexiImport", "catalog parse starting chars=${text.length}")
        val root = JsonParser.parseOrNull(text)
            ?: throw IOException("The catalogue is not valid JSON.")
        if (root !is JsonValue.Obj) throw IOException("The catalogue root must be a JSON object.")

        val version = root.int("version") ?: 1
        if (version <= 0 || version > SUPPORTED_CATALOGUE_VERSION) {
            throw IOException(
                "This catalogue uses schema version $version, but this app supports up to " +
                    "$SUPPORTED_CATALOGUE_VERSION."
            )
        }

        val decks = root.array("decks") ?: throw IOException("The catalogue is missing its \"decks\" array.")
        val entries = decks.mapNotNull { raw ->
            if (raw !is JsonValue.Obj) return@mapNotNull null
            val id = raw.string("id")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val title = raw.string("title")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val deckUrl = raw.string("url")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            if (!deckUrl.startsWith("https://", ignoreCase = true)) return@mapNotNull null
            CatalogEntry(
                id = id,
                title = title,
                description = raw.string("description")?.trim().orEmpty(),
                url = deckUrl,
                cardCount = raw.int("card_count")?.takeIf { it >= 0 } ?: 0
            )
        }
        Log.d("LexiImport", "catalog parse finished entries=${entries.size}")
        return entries
    }

    companion object {
        const val SUPPORTED_CATALOGUE_VERSION = 1
    }
}
