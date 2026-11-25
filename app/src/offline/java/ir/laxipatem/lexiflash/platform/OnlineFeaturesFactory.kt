package ir.laxipatem.lexiflash.platform

import android.content.Context

/**
 * Offline-flavour implementation of the online feature surface.
 *
 * This build declares no INTERNET permission and links no networking or ML
 * library. Every operation fails fast with [OnlineFeatureUnavailableException],
 * which the UI turns into a clear "not available in this build" message instead
 * of a silent no-op.
 */
object OnlineFeaturesFactory {

    fun create(context: Context): OnlineFeatures = OfflineOnlineFeatures

    private object OfflineOnlineFeatures : OnlineFeatures {

        override val isAvailable: Boolean = false

        override suspend fun fetchCatalog(url: String): Result<List<CatalogEntry>> =
            Result.failure(OnlineFeatureUnavailableException("The online catalogue"))

        override suspend fun downloadDeck(url: String): Result<String> =
            Result.failure(OnlineFeatureUnavailableException("Deck download"))

        override suspend fun isTranslationModelReady(): Boolean = false

        override suspend fun downloadTranslationModel(): Result<Unit> =
            Result.failure(OnlineFeatureUnavailableException("On-device translation"))

        override suspend fun translate(texts: List<String>): Result<List<String>> =
            Result.failure(OnlineFeatureUnavailableException("On-device translation"))
    }
}
