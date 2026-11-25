package ir.laxipatem.lexiflash.platform

/**
 * Contract for everything that needs the network or a downloaded ML model.
 *
 * The two product flavours ship different implementations of the same factory
 * signature (`OnlineFeaturesFactory.create(context)`):
 *
 * * `offline`: a no-op stub where [isAvailable] is false and every call fails
 *   with [UnsupportedOperationException]. It has no network code at all.
 * * `online`: a real implementation using `HttpURLConnection` and ML Kit.
 *
 * Because the two source sets reuse the same package and class names, exactly
 * one of them is compiled into any given build.
 */
interface OnlineFeatures {

    /** False for the offline flavour, true for the online flavour. */
    val isAvailable: Boolean

    /** Fetches and validates the deck catalogue at [url]. */
    suspend fun fetchCatalog(url: String): Result<List<CatalogEntry>>

    /**
     * Downloads a deck document and returns its **raw text**. Validation is the
     * caller's job so that a malformed remote file produces a readable error
     * inside the import flow rather than inside the HTTP layer.
     */
    suspend fun downloadDeck(url: String): Result<String>

    /** True when the English→Persian translation model is already on the device. */
    suspend fun isTranslationModelReady(): Boolean

    /** Downloads the translation model. Must only run after explicit consent. */
    suspend fun downloadTranslationModel(): Result<Unit>

    /** Translates [texts] English→Persian on device, in order. */
    suspend fun translate(texts: List<String>): Result<List<String>>

    /** Releases any held resources. Called from the application on shutdown. */
    fun close() = Unit
}

/** One deck offered by the remote catalogue. */
data class CatalogEntry(
    val id: String,
    val title: String,
    val description: String,
    val url: String,
    val cardCount: Int
)

/** Raised by the offline stub so a caller can distinguish "not shipped" from a real error. */
class OnlineFeatureUnavailableException(feature: String) :
    UnsupportedOperationException("$feature is not available in the offline build.")
