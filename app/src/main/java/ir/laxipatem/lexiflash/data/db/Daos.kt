package ir.laxipatem.lexiflash.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Projection used by the home screen deck list. */
data class DeckWithProgress(
    val id: Long,
    val key: String,
    val title: String,
    val isCore: Boolean,
    val partCount: Int,
    val license: String,
    val cardCount: Int,
    val seenCount: Int
)

/** Projection used by the per-part tiles on the deck screen. */
data class PartSummary(
    val partIndex: Int,
    val cardCount: Int,
    val seenCount: Int
)

/** Projection used when a part is opened for study. */
data class CardWithState(
    val id: String,
    val deckId: Long,
    val partIndex: Int,
    val orderInPart: Int,
    val english: String,
    val persian: String?,
    val ipa: String?,
    val vocabJson: String,
    val note: String?,
    val startMs: Long?,
    val translatePending: Boolean,
    val stability: Double?,
    val difficulty: Double?,
    val state: Int?,
    val lastReviewAt: Long?,
    val dueAt: Long?,
    val reps: Int?,
    val lapses: Int?,
    val seen: Boolean?
)

@Dao
interface DeckDao {

    @Query("SELECT * FROM decks ORDER BY isCore DESC, createdAt ASC")
    suspend fun decks(): List<DeckEntity>

    @Query("SELECT * FROM decks WHERE id = :deckId")
    suspend fun deckById(deckId: Long): DeckEntity?

    @Query("SELECT * FROM decks WHERE key = :key LIMIT 1")
    suspend fun deckByKey(key: String): DeckEntity?

    @Query(
        """
        SELECT d.id AS id,
               d.`key` AS `key`,
               d.title AS title,
               d.isCore AS isCore,
               d.partCount AS partCount,
               d.license AS license,
               (SELECT COUNT(*) FROM cards c WHERE c.deckId = d.id) AS cardCount,
               (SELECT COUNT(*) FROM cards c
                  INNER JOIN card_states s ON s.cardId = c.id
                  WHERE c.deckId = d.id AND s.seen = 1) AS seenCount
        FROM decks d
        ORDER BY d.isCore DESC, d.createdAt ASC
        """
    )
    fun observeDecksWithProgress(): Flow<List<DeckWithProgress>>

    @Query(
        """
        SELECT c.partIndex AS partIndex,
               COUNT(c.id) AS cardCount,
               SUM(CASE WHEN s.seen = 1 THEN 1 ELSE 0 END) AS seenCount
        FROM cards c
        LEFT JOIN card_states s ON s.cardId = c.id
        WHERE c.deckId = :deckId
        GROUP BY c.partIndex
        ORDER BY c.partIndex ASC
        """
    )
    fun observePartSummaries(deckId: Long): Flow<List<PartSummary>>

    @Query(
        """
        SELECT c.partIndex AS partIndex,
               COUNT(c.id) AS cardCount,
               SUM(CASE WHEN s.seen = 1 THEN 1 ELSE 0 END) AS seenCount
        FROM cards c
        LEFT JOIN card_states s ON s.cardId = c.id
        WHERE c.deckId = :deckId
        GROUP BY c.partIndex
        ORDER BY c.partIndex ASC
        """
    )
    suspend fun partSummaries(deckId: Long): List<PartSummary>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDeck(deck: DeckEntity): Long

    @Query("UPDATE decks SET title = :title, version = :version, partCount = :partCount, license = :license WHERE id = :deckId")
    suspend fun updateDeckMeta(deckId: Long, title: String, version: Int, partCount: Int, license: String)

    @Query("DELETE FROM decks WHERE id = :deckId")
    suspend fun deleteDeck(deckId: Long)

    @Query("DELETE FROM decks")
    suspend fun deleteAllDecks()

    // --- Cards --------------------------------------------------------------

    @Upsert
    suspend fun upsertCards(cards: List<CardEntity>)

    @Query("SELECT * FROM cards WHERE deckId = :deckId AND partIndex = :partIndex ORDER BY orderInPart ASC")
    suspend fun cardsOfPart(deckId: Long, partIndex: Int): List<CardEntity>

    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY partIndex ASC, orderInPart ASC")
    suspend fun cardsOfDeck(deckId: Long): List<CardEntity>

    @Query("SELECT id FROM cards WHERE deckId = :deckId")
    suspend fun cardIdsOfDeck(deckId: Long): List<String>

    @Query("DELETE FROM cards WHERE deckId = :deckId")
    suspend fun deleteCardsOfDeck(deckId: Long)

    @Query("DELETE FROM cards WHERE id = :cardId")
    suspend fun deleteCard(cardId: String)

    @Query("UPDATE cards SET persian = :persian, translatePending = 0, reviewed = :reviewed WHERE id = :cardId")
    suspend fun updateTranslation(cardId: String, persian: String, reviewed: Boolean)

    @Query("UPDATE cards SET reviewed = 1 WHERE id = :cardId")
    suspend fun markReviewed(cardId: String)

    @Query("SELECT COUNT(*) FROM cards WHERE deckId = :deckId")
    suspend fun cardCount(deckId: Long): Int

    // --- Study queues --------------------------------------------------------

    @Query(
        """
        SELECT c.id AS id, c.deckId AS deckId, c.partIndex AS partIndex, c.orderInPart AS orderInPart,
               c.english AS english, c.persian AS persian, c.ipa AS ipa, c.vocabJson AS vocabJson,
               c.note AS note, c.startMs AS startMs, c.translatePending AS translatePending,
               s.stability AS stability, s.difficulty AS difficulty, s.state AS state,
               s.lastReviewAt AS lastReviewAt, s.dueAt AS dueAt, s.reps AS reps,
               s.lapses AS lapses, s.seen AS seen
        FROM cards c
        LEFT JOIN card_states s ON s.cardId = c.id
        WHERE c.deckId = :deckId AND c.partIndex = :partIndex
        ORDER BY c.orderInPart ASC
        """
    )
    fun observePartCards(deckId: Long, partIndex: Int): Flow<List<CardWithState>>

    @Query(
        """
        SELECT c.id AS id, c.deckId AS deckId, c.partIndex AS partIndex, c.orderInPart AS orderInPart,
               c.english AS english, c.persian AS persian, c.ipa AS ipa, c.vocabJson AS vocabJson,
               c.note AS note, c.startMs AS startMs, c.translatePending AS translatePending,
               s.stability AS stability, s.difficulty AS difficulty, s.state AS state,
               s.lastReviewAt AS lastReviewAt, s.dueAt AS dueAt, s.reps AS reps,
               s.lapses AS lapses, s.seen AS seen
        FROM cards c
        INNER JOIN card_states s ON s.cardId = c.id
        WHERE s.seen = 1 AND s.dueAt <= :now
        ORDER BY s.dueAt ASC
        LIMIT :limit
        """
    )
    suspend fun dueCards(now: Long, limit: Int): List<CardWithState>

    @Query(
        """
        SELECT COUNT(*) FROM card_states s
        INNER JOIN cards c ON c.id = s.cardId
        WHERE s.seen = 1 AND s.dueAt <= :now
        """
    )
    fun observeDueCount(now: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM card_states s
        INNER JOIN cards c ON c.id = s.cardId
        WHERE s.seen = 1 AND s.dueAt <= :now
        """
    )
    suspend fun dueCount(now: Long): Int

    @Transaction
    suspend fun replaceDeckCards(deckId: Long, cards: List<CardEntity>) {
        upsertCards(cards)
    }
}

@Dao
interface ReviewDao {

    @Query("SELECT * FROM card_states WHERE cardId = :cardId")
    suspend fun stateOf(cardId: String): CardStateEntity?

    @Upsert
    suspend fun upsertState(state: CardStateEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLog(log: ReviewLogEntity): Long

    /** Writes the new memory state and the log entry as one atomic unit. */
    @Transaction
    suspend fun recordReview(state: CardStateEntity, log: ReviewLogEntity) {
        upsertState(state)
        insertLog(log)
    }

    @Query("SELECT * FROM card_states WHERE seen = 1 ORDER BY lastReviewAt ASC")
    suspend fun allSeenStates(): List<CardStateEntity>

    @Query("SELECT reviewedAt FROM review_logs WHERE reviewedAt >= :since")
    suspend fun reviewTimestampsSince(since: Long): List<Long>

    @Query("SELECT COUNT(*) FROM review_logs WHERE reviewedAt >= :since")
    suspend fun reviewCountSince(since: Long): Long

    @Query("SELECT COUNT(*) FROM review_logs WHERE reviewedAt >= :since AND rating >= 2")
    suspend fun successfulReviewCountSince(since: Long): Long

    @Query("SELECT COUNT(*) FROM review_logs")
    fun observeTotalReviews(): Flow<Int>

    @Query("SELECT COUNT(*) FROM review_logs WHERE reviewedAt >= :since")
    suspend fun countSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM card_states WHERE seen = 1 AND lastReviewAt >= :since")
    suspend fun cardsReviewedSince(since: Long): Int

    @Query("DELETE FROM review_logs")
    suspend fun deleteAllLogs()

    @Query("DELETE FROM card_states")
    suspend fun deleteAllStates()
}

@Dao
interface QuizDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertResult(result: QuizResultEntity): Long

    @Query("SELECT MAX(score * 100 / CASE WHEN total = 0 THEN 1 ELSE total END) FROM quiz_results WHERE deckId = :deckId AND partIndex = :partIndex")
    suspend fun bestScore(deckId: Long, partIndex: Int): Int?

    @Query("SELECT * FROM quiz_results WHERE deckId = :deckId ORDER BY takenAt DESC")
    suspend fun resultsForDeck(deckId: Long): List<QuizResultEntity>

    @Query("DELETE FROM quiz_results")
    suspend fun deleteAllResults()
}
