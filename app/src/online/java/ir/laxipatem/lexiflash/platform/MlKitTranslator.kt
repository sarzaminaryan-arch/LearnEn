package ir.laxipatem.lexiflash.platform

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * On-device English→Persian translation using ML Kit.
 *
 * The model is never downloaded implicitly: [downloadModel] runs only after the
 * learner confirms the one-time download in the import wizard. Nothing ever
 * leaves the device, and every translated card is flagged as machine translated
 * and unreviewed.
 */
class MlKitTranslator {

    private val options: TranslatorOptions = TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.ENGLISH)
        .setTargetLanguage(TranslateLanguage.PERSIAN)
        .build()

    /** Returns a shared client; ML Kit caches the underlying model. */
    private fun client(): Translator = Translation.getClient(options)

    suspend fun isModelDownloaded(): Boolean {
        val client = client()
        return try {
            suspendCancellableCoroutine { continuation ->
                client.downloadModelIfNeeded(DownloadConditions.Builder().build())
                    .addOnSuccessListener { if (continuation.isActive) continuation.resume(true) }
                    .addOnFailureListener { if (continuation.isActive) continuation.resume(false) }
            }
        } finally {
            client.close()
        }
    }

    /** Downloads the translation model. Call only after explicit user consent. */
    suspend fun downloadModel(): Result<Unit> {
        val client = client()
        return try {
            suspendCancellableCoroutine { continuation ->
                client.downloadModelIfNeeded(DownloadConditions.Builder().build())
                    .addOnSuccessListener { if (continuation.isActive) continuation.resume(Result.success(Unit)) }
                    .addOnFailureListener { error ->
                        if (continuation.isActive) continuation.resume(Result.failure(error))
                    }
            }
        } finally {
            client.close()
        }
    }

    /**
     * Translates [texts] in order. The model must already be downloaded; a
     * missing model surfaces as a readable failure rather than an implicit
     * multi-megabyte download behind the learner's back.
     */
    suspend fun translate(texts: List<String>): Result<List<String>> {
        if (texts.isEmpty()) return Result.success(emptyList())
        val client = client()
        return try {
            val translated = ArrayList<String>(texts.size)
            for (text in texts) {
                val result = suspendCancellableCoroutine { continuation ->
                    client.translate(text)
                        .addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(Result.success(value)) }
                        .addOnFailureListener { error -> if (continuation.isActive) continuation.resume(Result.failure(error)) }
                }
                result.fold(
                    onSuccess = { translated += it },
                    onFailure = { return Result.failure(it) }
                )
            }
            Result.success(translated)
        } finally {
            client.close()
        }
    }

    /** Releases the ML Kit client held by this translator, if any. */
    fun close() = Unit
}
