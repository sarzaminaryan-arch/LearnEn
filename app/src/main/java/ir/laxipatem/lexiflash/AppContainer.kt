package ir.laxipatem.lexiflash

import android.content.Context
import ir.laxipatem.lexiflash.data.db.AppDatabase
import ir.laxipatem.lexiflash.data.repo.DeckRepository
import ir.laxipatem.lexiflash.data.repo.ReviewRepository
import ir.laxipatem.lexiflash.data.repo.SettingsRepository
import ir.laxipatem.lexiflash.data.repo.StatsRepository
import ir.laxipatem.lexiflash.importer.ImportService
import ir.laxipatem.lexiflash.platform.DeckExporter
import ir.laxipatem.lexiflash.platform.OnlineFeatures
import ir.laxipatem.lexiflash.platform.OnlineFeaturesFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manual dependency graph.
 *
 * The project deliberately avoids Hilt/Dagger and every other annotation
 * processor beyond Room: the graph is small, the construction order is obvious,
 * and a hand-written container keeps the build fast and the release APK free of
 * generated wiring code.
 *
 * Everything here is created once per process and lives as long as the
 * Application instance.
 */
class AppContainer(private val context: Context) {

    /** Process-scoped coroutine scope for work that outlives a screen. */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.build(context) }

    val settings: SettingsRepository by lazy { SettingsRepository(context) }

    val decks: DeckRepository by lazy { DeckRepository(context, database, settings) }

    val reviews: ReviewRepository by lazy { ReviewRepository(context, database) }

    val stats: StatsRepository by lazy { StatsRepository(context, database) }

    val importService: ImportService by lazy { ImportService() }

    val exporter: DeckExporter by lazy { DeckExporter(context) }

    /**
     * The network/translation surface. Resolved through the flavour-specific
     * factory, so the offline build links a stub that can never touch a socket.
     */
    val onlineFeatures: OnlineFeatures by lazy { OnlineFeaturesFactory.create(context) }

    /** Seeds the bundled deck and clears stale export files at startup. */
    fun warmUp() {
        applicationScope.launch {
            decks.ensureCoreDeck()
            exporter.clearCache()
        }
    }

    fun shutdown() {
        onlineFeatures.close()
    }
}
