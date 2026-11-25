package ir.laxipatem.lexiflash.platform

import android.content.Context

/**
 * Online-flavour implementation of the online feature surface.
 *
 * This build declares the INTERNET permission and links ML Kit. Even so, nothing
 * is fetched automatically: every method here is only reached from an explicit
 * user action, and the translation model is only downloaded after consent.
 */
object OnlineFeaturesFactory {

    fun create(context: Context): OnlineFeatures = RealOnlineFeatures()

    private class RealOnlineFeatures : OnlineFeatures {

        private val catalog = CatalogClient()
        private val translator = MlKitTranslator()

        override val isAvailable: Boolean = true

        override suspend fun fetchCatalog(url: String): Result<List<CatalogEntry>> =
            catalog.fetch(url)

        override suspend fun downloadDeck(url: String): Result<String> =
            catalog.downloadDeck(url)

        override suspend fun isTranslationModelReady(): Boolean =
            translator.isModelDownloaded()

        override suspend fun downloadTranslationModel(): Result<Unit> =
            translator.downloadModel()

        override suspend fun translate(texts: List<String>): Result<List<String>> =
            translator.translate(texts)

        override fun close() = translator.close()
    }
}
