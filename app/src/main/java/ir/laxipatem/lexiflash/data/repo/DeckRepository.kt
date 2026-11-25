package ir.laxipatem.lexiflash.data.repo

import android.content.Context
import androidx.room.withTransaction
import ir.laxipatem.lexiflash.data.db.AppDatabase
import ir.laxipatem.lexiflash.data.db.CardEntity
import ir.laxipatem.lexiflash.data.db.Converters
import ir.laxipatem.lexiflash.data.db.DeckDao
import ir.laxipatem.lexiflash.data.db.DeckEntity
import ir.laxipatem.lexiflash.data.db.DeckWithProgress
import ir.laxipatem.lexiflash.data.db.PartSummary
import ir.laxipatem.lexiflash.data.model.DeckCardJson
import ir.laxipatem.lexiflash.data.model.DeckJson
import ir.laxipatem.lexiflash.data.model.DeckJsonCodec
import ir.laxipatem.lexiflash.data.model.DeckPartJson
import ir.laxipatem.lexiflash.importer.DraftDeck
import ir.laxipatem.lexiflash.importer.ImportService
import ir.laxipatem.lexiflash.importer.PartSplitter
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Owns decks and their cards: the bundled core deck, imported decks and the
 * online download path.
 *
 * The core deck is versioned. On first launch, and whenever the bundled
 * `core_deck.json` carries a higher version than the one recorded in settings,
 * the deck is re-read from assets. Re-import preserves study progress: existing
 * cards are updated in place and only genuinely new cards are inserted.
 */
class DeckRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val settings: SettingsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val deckDao: DeckDao get() = database.deckDao()

    val decks: Flow<List<DeckWithProgress>> = deckDao.observeDecksWithProgress()

    fun partSummaries(deckId: Long): Flow<List<PartSummary>> = deckDao.observePartSummaries(deckId)

    suspend fun deck(deckId: Long): DeckEntity? = withContext(ioDispatcher) { deckDao.deckById(deckId) }

    suspend fun coreDeck(): DeckEntity? = withContext(ioDispatcher) { deckDao.deckByKey(CORE_DECK_KEY) }

    // --- Core deck seeding ---------------------------------------------------

    /**
     * Ensures the bundled core deck is present and up to date.
     *
     * Falls back to the sample deck when the generated deck is missing or
     * malformed, so the app always has something to study. Returns the number of
     * cards now in the core deck.
     */
    suspend fun ensureCoreDeck(): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            val storedVersion = settings.current().coreDeckVersion
            val existing = deckDao.deckByKey(CORE_DECK_KEY)
            if (existing != null && existing.version >= storedVersion && storedVersion > 0) {
                return@runCatching deckDao.cardCount(existing.id)
            }

            val loaded = readAsset(CORE_DECK_ASSET)
                ?.let { DeckJsonCodec.parseResult(it).getOrNull() }
                ?: readAsset(SAMPLE_DECK_ASSET)
                    ?.let { DeckJsonCodec.parseResult(it).getOrNull() }
                ?: throw IOException("Neither the core deck nor the sample deck could be read.")

            val count = writeDeck(
                key = CORE_DECK_KEY,
                deck = loaded,
                isCore = true,
                preserveProgress = true
            )
            settings.setCoreDeckVersion(loaded.version)
            count
        }
    }

    /** Reads a text asset, returning null when it does not exist. */
    private fun readAsset(path: String): String? = try {
        context.assets.open(path).bufferedReader().use { it.readText() }
    } catch (_: IOException) {
        null
    }

    /**
     * Writes [deck] under [key], updating an existing deck in place.
     *
     * Card ids are stable, so an upgrade keeps every `card_states` row that
     * still matches a card and only drops state for cards that disappeared,
     * which the foreign key cascade handles.
     */
    private suspend fun writeDeck(
        key: String,
        deck: DeckJson,
        isCore: Boolean,
        preserveProgress: Boolean
    ): Int = database.withTransaction {
        val existing = deckDao.deckByKey(key)
        val deckId: Long
        if (existing == null) {
            deckId = deckDao.insertDeck(
                DeckEntity(
                    key = key,
                    title = deck.title,
                    sourceLang = deck.sourceLang,
                    targetLang = deck.targetLang,
                    license = deck.license,
                    version = deck.version,
                    partCount = deck.parts.size,
                    isCore = isCore,
                    createdAt = System.currentTimeMillis()
                )
            )
        } else {
            deckId = existing.id
            deckDao.updateDeckMeta(
                deckId = deckId,
                title = deck.title,
                version = deck.version,
                partCount = deck.parts.size,
                license = deck.license
            )
            if (!preserveProgress) {
                deckDao.deleteCardsOfDeck(deckId)
            }
        }

        val incomingIds = HashSet<String>()
        val entities = ArrayList<CardEntity>(deck.cardCount)
        deck.parts.forEach { part ->
            part.cards.forEachIndexed { order, card ->
                incomingIds += card.id
                entities += card.toEntity(deckId, part.index, order)
            }
        }

        // Remove cards that vanished from the deck definition.
        if (preserveProgress) {
            val currentIds = deckDao.cardIdsOfDeck(deckId)
            val removed = currentIds.filter { it !in incomingIds }
            removed.forEach { deckDao.deleteCard(it) }
        }

        deckDao.upsertCards(entities)
        entities.size
    }

    private fun DeckCardJson.toEntity(deckId: Long, partIndex: Int, order: Int) = CardEntity(
        id = id,
        deckId = deckId,
        partIndex = partIndex,
        orderInPart = order,
        startMs = startMs,
        english = english,
        persian = persian,
        ipa = ipa,
        vocabJson = Converters.encodeVocab(vocab),
        note = note,
        difficulty = difficulty,
        reviewed = reviewed,
        source = source,
        license = license,
        translatePending = persian.isNullOrBlank()
    )

    // --- Imported decks ------------------------------------------------------

    /**
     * Persists a deck assembled by the import wizard.
     *
     * @return the new deck's id.
     */
    suspend fun saveImportedDeck(draft: DraftDeck, requestedParts: Int = 10): Long =
        withContext(ioDispatcher) {
            database.withTransaction {
                val parts = if (draft.parts.isNotEmpty()) {
                    draft.parts
                } else {
                    PartSplitter.split(draft.cards, requestedParts)
                }
                val deckId = deckDao.insertDeck(
                    DeckEntity(
                        key = "import-" + UUID.randomUUID().toString(),
                        title = draft.title,
                        sourceLang = "en",
                        targetLang = "fa",
                        license = draft.license,
                        version = 1,
                        partCount = parts.size,
                        isCore = false,
                        createdAt = System.currentTimeMillis()
                    )
                )
                val entities = ArrayList<CardEntity>(draft.cardCount)
                parts.forEachIndexed { partIndex, cards ->
                    cards.forEachIndexed { order, card ->
                        entities += CardEntity(
                            id = "imp-" + UUID.randomUUID().toString(),
                            deckId = deckId,
                            partIndex = partIndex + 1,
                            orderInPart = order,
                            startMs = card.startMs,
                            english = card.english,
                            persian = card.persian,
                            ipa = null,
                            vocabJson = "[]",
                            note = if (card.machineTranslated) MACHINE_TRANSLATED_NOTE else null,
                            difficulty = null,
                            reviewed = !card.machineTranslated && card.persian != null,
                            source = draft.source,
                            license = draft.license,
                            translatePending = card.pendingTranslation
                        )
                    }
                }
                deckDao.upsertCards(entities)
                deckId
            }
        }

    /** Saves a deck parsed from downloaded or user-supplied deck.json text. */
    suspend fun saveDeckFromJson(jsonText: String): Result<Long> = withContext(ioDispatcher) {
        runCatching {
            val deck = DeckJsonCodec.parse(jsonText)
            val key = "dl-" + UUID.randomUUID().toString()
            writeDeck(key = key, deck = deck, isCore = false, preserveProgress = false)
            deckDao.deckByKey(key)?.id
                ?: throw IOException("The downloaded deck could not be stored.")
        }
    }

    suspend fun deleteDeck(deckId: Long) = withContext(ioDispatcher) { deckDao.deleteDeck(deckId) }

    suspend fun deleteAll() = withContext(ioDispatcher) {
        database.withTransaction {
            deckDao.deleteAllDecks()
            database.reviewDao().deleteAllLogs()
            database.reviewDao().deleteAllStates()
            database.quizDao().deleteAllResults()
        }
        settings.clearAll()
    }

    // --- Cards for study -----------------------------------------------------

    fun partCards(deckId: Long, partIndex: Int) = deckDao.observePartCards(deckId, partIndex)

    suspend fun updateTranslation(cardId: String, persian: String, reviewed: Boolean) =
        withContext(ioDispatcher) { deckDao.updateTranslation(cardId, persian, reviewed) }

    suspend fun removeCard(cardId: String) = withContext(ioDispatcher) { deckDao.deleteCard(cardId) }

    /** All cards of a deck, used to build export files and quizzes. */
    suspend fun cardsOfDeck(deckId: Long) = withContext(ioDispatcher) { deckDao.cardsOfDeck(deckId) }

    suspend fun cardsOfPart(deckId: Long, partIndex: Int) =
        withContext(ioDispatcher) { deckDao.cardsOfPart(deckId, partIndex) }

    /** Rebuilds a deck.json document from the stored cards. */
    suspend fun exportDeck(deckId: Long): Result<String> = withContext(ioDispatcher) {
        runCatching {
            val deck = deckDao.deckById(deckId) ?: throw IOException("That deck no longer exists.")
            val cards = deckDao.cardsOfDeck(deckId)
            if (cards.isEmpty()) throw IOException("That deck has no cards to export.")

            val parts = cards.groupBy { it.partIndex }.toSortedMap().map { (partIndex, partCards) ->
                DeckPartJson(
                    index = partIndex,
                    cards = partCards.sortedBy { it.orderInPart }.map { card ->
                        DeckCardJson(
                            id = card.id,
                            english = card.english,
                            persian = card.persian,
                            ipa = card.ipa,
                            vocab = Converters.decodeVocab(card.vocabJson),
                            note = card.note,
                            difficulty = card.difficulty,
                            reviewed = card.reviewed,
                            source = card.source,
                            license = card.license,
                            startMs = card.startMs
                        )
                    }
                )
            }
            val document = DeckJson(
                version = deck.version.coerceAtLeast(1),
                title = deck.title,
                sourceLang = deck.sourceLang,
                targetLang = deck.targetLang,
                license = deck.license,
                parts = parts
            )
            DeckJsonCodec.encode(document)
        }
    }

    /**
     * Best quiz percentage per part for [deckId].
     *
     * Read on demand rather than observed, because a quiz result only changes
     * while the learner is inside the quiz flow; the home screen re-reads this
     * map whenever the part summaries change.
     */
    suspend fun bestQuizScores(deckId: Long): Map<Int, Int?> = withContext(ioDispatcher) {
        val dao = database.quizDao()
        deckDao.cardsOfDeck(deckId)
            .map { it.partIndex }
            .distinct()
            .sorted()
            .associateWith { partIndex -> dao.bestScore(deckId, partIndex) }
    }

    /** Groups imported cards into parts for preview and saving. */
    fun splitIntoParts(cards: List<ImportService.DraftCard>, requestedParts: Int): List<List<ImportService.DraftCard>> =
        PartSplitter.split(cards, requestedParts)

    companion object {
        const val CORE_DECK_KEY = "core"
        const val CORE_DECK_ASSET = "content/core_deck.json"
        const val SAMPLE_DECK_ASSET = "content/sample_deck.json"
        const val MACHINE_TRANSLATED_NOTE = "machine-translated"
    }
}
