package ir.laxipatem.lexiflash.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Round-trip test for the real Room schema on a real device or emulator.
 *
 * The JVM tests can only exercise pure Kotlin, so this is the one place that
 * proves the entities, the converter and the LEFT JOIN in `observePartCards`
 * agree with each other. A schema change that breaks the join fails here rather
 * than on a learner's phone.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseRoundTripTest {

    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            // Room turns SQLite foreign keys on by default; it is stated here so
            // the cascade test below is not relying on an implicit default.
            .build()
        database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun deck_and_cards_survive_a_round_trip() = runTest {
        val deckDao = database.deckDao()
        val deckId = deckDao.insertDeck(deck(partCount = 1))
        deckDao.upsertCards(listOf(card(deckId, id = "c1"), card(deckId, id = "c2")))

        assertEquals(2, deckDao.cardsOfPart(deckId, partIndex = 1).size)
        assertEquals("First sentence.", deckDao.cardsOfPart(deckId, 1).first().english)
    }

    @Test
    fun a_card_without_state_still_appears_in_the_part() = runTest {
        val deckDao = database.deckDao()
        val deckId = deckDao.insertDeck(deck(partCount = 1))
        deckDao.upsertCards(listOf(card(deckId, id = "c1"), card(deckId, id = "c2")))
        // Only one of the two cards has been reviewed.
        database.reviewDao().upsertState(state(cardId = "c1", dueAt = 500L, seen = true))

        val rows = deckDao.observePartCards(deckId, partIndex = 1).first()
        assertEquals("both cards must be listed", 2, rows.size)
        val reviewed = rows.single { it.id == "c1" }
        val fresh = rows.single { it.id == "c2" }
        assertEquals(500L, reviewed.dueAt)
        assertNull("an unseen card has no memory state", fresh.dueAt)
    }

    @Test
    fun cardsWithStateOfPart_matches_the_observable_query() = runTest {
        val deckDao = database.deckDao()
        val deckId = deckDao.insertDeck(deck(partCount = 2))
        deckDao.upsertCards(
            listOf(
                card(deckId, id = "p1a", partIndex = 1),
                card(deckId, id = "p1b", partIndex = 1),
                card(deckId, id = "p2a", partIndex = 2)
            )
        )
        database.reviewDao().upsertState(state(cardId = "p1a", dueAt = 100L, seen = true))

        val oneShot = deckDao.cardsWithStateOfPart(deckId, partIndex = 1)
        val observed = deckDao.observePartCards(deckId, partIndex = 1).first()

        assertEquals(2, oneShot.size)
        assertEquals(
            "the suspend query and the Flow must return the same rows",
            observed.map { it.id }.sorted(),
            oneShot.map { it.id }.sorted()
        )
    }

    @Test
    fun reviews_are_logged_and_counted() = runTest {
        val deckDao = database.deckDao()
        val reviewDao = database.reviewDao()
        val deckId = deckDao.insertDeck(deck(partCount = 1))
        deckDao.upsertCards(listOf(card(deckId, id = "c1")))

        reviewDao.insertLog(
            ReviewLogEntity(
                cardId = "c1",
                rating = 3,
                reviewedAt = 1_000L,
                elapsedDays = 0.0,
                scheduledDays = 1.0,
                stateBefore = 0
            )
        )

        assertEquals(1, reviewDao.observeTotalReviews().first())

        // A card only counts as due once it has been seen: unseen cards join the
        // study queue through the new-card limit instead, so they must not be
        // counted here.
        assertEquals("an unseen card is not due", 0, deckDao.observeDueCount(now = 5_000L).first())

        database.reviewDao().upsertState(state(cardId = "c1", dueAt = 4_000L, seen = true))
        assertEquals("a seen card past its due date is due", 1, deckDao.observeDueCount(now = 5_000L).first())
        assertEquals("a seen card before its due date is not", 0, deckDao.observeDueCount(now = 1_000L).first())
    }

    @Test
    fun deleting_a_deck_cascades_to_its_cards() = runTest {
        val deckDao = database.deckDao()
        val deckId = deckDao.insertDeck(deck(partCount = 1))
        deckDao.upsertCards(listOf(card(deckId, id = "c1")))

        deckDao.deleteDeck(deckId)

        assertEquals(0, deckDao.cardsOfPart(deckId, partIndex = 1).size)
        assertTrue(deckDao.observeDecksWithProgress().first().isEmpty())
    }

    private fun deck(partCount: Int) = DeckEntity(
        key = "test-deck",
        title = "Test deck",
        sourceLang = "en",
        targetLang = "fa",
        license = "CC0-1.0",
        version = 1,
        partCount = partCount,
        isCore = false,
        createdAt = 0L
    )

    private fun card(deckId: Long, id: String, partIndex: Int = 1) = CardEntity(
        id = id,
        deckId = deckId,
        partIndex = partIndex,
        orderInPart = 0,
        startMs = null,
        english = "First sentence.",
        persian = "جمله اول.",
        ipa = null,
        vocabJson = "[]",
        note = null,
        difficulty = null,
        reviewed = false,
        source = null,
        license = null,
        translatePending = false
    )

    private fun state(cardId: String, dueAt: Long, seen: Boolean) = CardStateEntity(
        cardId = cardId,
        stability = 1.0,
        difficulty = 5.0,
        state = 1,
        lastReviewAt = 0L,
        dueAt = dueAt,
        reps = 1,
        lapses = 0,
        seen = seen
    )
}
