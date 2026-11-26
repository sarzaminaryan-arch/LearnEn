package ir.laxipatem.lexiflash.platform

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.laxipatem.lexiflash.data.db.AppDatabase
import ir.laxipatem.lexiflash.data.repo.DeckRepository
import ir.laxipatem.lexiflash.data.repo.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Online-only catalogue parser and downloaded-deck persistence regression tests. */
@RunWith(AndroidJUnit4::class)
class CatalogImportInstrumentedTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: DeckRepository
    private val testContext: Context
        get() = InstrumentationRegistry.getInstrumentation().context
    private val appContext: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DeckRepository(
            context = appContext,
            database = database,
            settings = SettingsRepository(appContext),
            ioDispatcher = Dispatchers.IO
        )
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun checked_in_catalogue_parses_and_real_catalogue_deck_is_saved_to_room() = runTest {
        traced("real-catalogue-and-deck") {
            val catalogueText = fixture("import-fixtures/catalog/catalog.json")
            val entries = CatalogClient().parseCatalogue(catalogueText)
            assertEquals(3, entries.size)
            assertTrue(entries.all { it.url.startsWith("https://") })

            val deckJson = fixture("import-fixtures/catalog/everyday-conversation.json")
            val saved = repository.saveDeckFromJson(deckJson)
            assertTrue("real catalogue deck should validate and save: ${saved.exceptionOrNull()}", saved.isSuccess)
            val deckId = saved.getOrThrow()
            assertEquals(30, database.deckDao().cardCount(deckId))
        }
    }

    @Test
    fun malformed_catalogue_and_downloaded_deck_documents_are_rejected() = runTest {
        traced("malformed-catalogue-and-decks") {
            listOf(
                "not json",
                "[]",
                "{\"version\":1}",
                "{\"version\":999,\"decks\":[]}"
            ).forEach { malformed ->
                val result = runCatching { CatalogClient().parseCatalogue(malformed) }
                assertTrue("catalogue should fail validation: $malformed", result.isFailure)
            }

            val duplicateIds = """
                {"version":1,"title":"Duplicate IDs","source_lang":"en","target_lang":"fa","parts":[
                  {"index":1,"cards":[
                    {"id":"same","english":"This sentence has enough words.","persian":null},
                    {"id":"same","english":"Another sentence has enough words.","persian":null}
                  ]}
                ]}
            """.trimIndent()
            val malformedDecks = listOf(
                """{"version":1,"source_lang":"en","target_lang":"fa","parts":[]}""",
                """{"version":1,"title":null,"source_lang":"en","target_lang":"fa","parts":[{"index":1,"cards":[]}] }""",
                duplicateIds,
                """{"version":1,"title":"Null card","source_lang":"en","target_lang":"fa","parts":[{"index":1,"cards":[null]}]}"""
            )
            malformedDecks.forEach { malformed ->
                val result = repository.saveDeckFromJson(malformed)
                assertTrue("deck should fail validation: $malformed", result.isFailure)
            }
        }
    }

    private fun fixture(name: String): String =
        testContext.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private suspend fun <T> traced(name: String, block: suspend () -> T): T = try {
        block()
    } catch (throwable: Throwable) {
        Log.e(TAG, "instrumented catalog test failed: $name", throwable)
        throw throwable
    }

    private companion object {
        const val TAG = "LexiImportTest"
    }
}
