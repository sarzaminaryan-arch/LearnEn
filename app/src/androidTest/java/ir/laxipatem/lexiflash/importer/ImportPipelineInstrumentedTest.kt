package ir.laxipatem.lexiflash.importer

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.laxipatem.lexiflash.data.db.AppDatabase
import ir.laxipatem.lexiflash.data.repo.DeckRepository
import ir.laxipatem.lexiflash.data.repo.SettingsRepository
import java.nio.charset.Charset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end parser → splitter → real Room tests over the installed test APK assets. */
@RunWith(AndroidJUnit4::class)
class ImportPipelineInstrumentedTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: DeckRepository
    private val service = ImportService()
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
    fun all_supported_file_formats_parse_split_and_save_in_room() = runTest {
        traced("all-supported-formats") {
            val fixtures = listOf(
                "sample.srt", "sample.vtt", "sample.ass", "sample.txt",
                "sample.csv", "sample.tsv", "sample.deck.json"
            )
            fixtures.forEach { name ->
                val bytes = fixture(name)
                val result = service.importSingleFile(bytes, name, name).getOrThrow()
                assertEquals("$name card count", 30, result.cards.size)
                val parts = PartSplitter.split(result.cards, requestedParts = 5)
                assertEquals("$name part count", 5, parts.size)
                val draft = DraftDeck(
                    title = "Instrumented $name",
                    license = "Original test fixture",
                    cards = result.cards,
                    parts = parts,
                    source = name
                )
                val deckId = repository.saveImportedDeck(draft, requestedParts = 5)
                assertEquals("$name persisted card count", 30, database.deckDao().cardCount(deckId))
            }
        }
    }

    @Test
    fun windows1256_bilingual_files_pair_and_save_even_when_persian_track_is_shorter() = runTest {
        traced("bilingual-windows-1256") {
            val english = fixture("english-pair.srt")
            val fullPersian = fixture("persian-pair.srt")
            val decodedPersian = EncodingDetector.decode(fullPersian)
            val firstFiveCues = decodedPersian.split("\n\n").take(5).joinToString("\n\n")
            val shortPersian = firstFiveCues.toByteArray(Charset.forName("windows-1256"))

            val paired = service.importBilingual(
                englishBytes = english,
                englishName = "english-pair.srt",
                persianBytes = shortPersian,
                persianName = "persian-pair.srt",
                fallbackTitle = "Bilingual test"
            ).getOrThrow()

            assertEquals(30, paired.cards.size)
            assertTrue("missing Persian cues should remain pending", paired.pendingCount >= 25)
            val parts = PartSplitter.split(paired.cards, requestedParts = 5)
            val deckId = repository.saveImportedDeck(
                DraftDeck("Bilingual test", "Original test fixture", paired.cards, parts, "pair"),
                requestedParts = 5
            )
            assertEquals(30, database.deckDao().cardCount(deckId))
        }
    }

    @Test
    fun empty_bom_only_invalid_and_very_long_fixtures_return_results_without_uncaught_throwables() = runTest {
        traced("malformed-import-inputs") {
            listOf("empty.txt", "bom-only.txt", "invalid-bytes.srt", "very-long-line.txt").forEach { name ->
                val result = service.importSingleFile(fixture(name), name, name)
                Log.i(TAG, "$name completed isSuccess=${result.isSuccess} failure=${result.exceptionOrNull()}")
                assertTrue("$name should be rejected as unusable input", result.isFailure)
            }

            val noFinalNewline = service.importSingleFile(
                fixture("one-line-no-newline.txt"),
                "one-line-no-newline.txt",
                "No newline"
            ).getOrThrow()
            assertEquals(1, noFinalNewline.cards.size)
        }
    }

    private fun fixture(name: String): ByteArray =
        testContext.assets.open("import-fixtures/$name").use { it.readBytes() }

    private suspend fun <T> traced(name: String, block: suspend () -> T): T = try {
        block()
    } catch (throwable: Throwable) {
        Log.e(TAG, "instrumented import test failed: $name", throwable)
        throw throwable
    }

    private companion object {
        const val TAG = "LexiImportTest"
    }
}
