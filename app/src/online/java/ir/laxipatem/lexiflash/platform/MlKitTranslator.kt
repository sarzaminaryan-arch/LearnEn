package ir.laxipatem.lexiflash.platform

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * On-device English→Persian translation using ML Kit.
 *
 * Two rules shape this class:
 *
 * 1. **Nothing is downloaded implicitly.** Checking whether the model is ready
 *    asks the model manager; it never triggers a transfer. The one-time model
 *    download only happens from [downloadModel], which the import wizard calls
 *    after the learner explicitly agrees to it.
 * 2. **Nothing leaves the device.** ML Kit translates offline once the model is
 *    present, and every card it produces is flagged as machine translated and
 *    unreviewed by the caller.
 */
class MlKitTranslator {

    private val options: TranslatorOptions = TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.ENGLISH)
        .setTargetLanguage(TranslateLanguage.PERSIAN)
        .build()

    /**
     * The Persian model. English→Persian needs the target-side model on disk;
     * checking the English one would report "ready" for a pair that still has
     * a download pending.
     */
    private val persianModel: TranslateRemoteModel = TranslateRemoteModel.Builder(
        TranslateLanguage.PERSIAN
    ).build()

    /** Returns a client; ML Kit shares the underlying model between clients. */
    private fun client(): Translator = Translation.getClient(options)

    /**
     * True when the English model is already on the device.
     *
     * This asks [RemoteModelManager] rather than calling `downloadModelIfNeeded`,
     * so a status check can never start a multi-megabyte download behind the
     * learner's back.
     */
    suspend fun isModelDownloaded(): Boolean = try {
        // isModelDownloaded reports what is on disk; unlike downloadModelIfNeeded
        // it cannot start a transfer. That distinction is the whole point: a
        // status check must never begin a 30 MB download on its own.
        suspendCancellableCoroutine<Boolean> { continuation ->
            RemoteModelManager.getInstance()
                .isModelDownloaded(persianModel)
                .addOnSuccessListener { downloaded ->
                    if (continuation.isActive) continuation.resume(downloaded)
                }
                .addOnFailureListener {
                    // An unreadable model directory just means "not ready yet".
                    if (continuation.isActive) continuation.resume(false)
                }
        }
    } catch (error: Exception) {
        false
    }

    /**
     * Downloads the translation model. Call only after explicit user consent.
     *
     * The `Result` is built outside the coroutine bridge: `Result` is an inline
     * class, and using it as the coroutine's type argument makes Kotlin unable to
     * infer or materialise it reliably.
     */
    suspend fun downloadModel(): Result<Unit> {
        val client = client()
        return try {
            suspendCancellableCoroutine<Unit> { continuation ->
                client.downloadModelIfNeeded(DownloadConditions.Builder().build())
                    .addOnSuccessListener {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                    .addOnFailureListener { error ->
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
            }
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            // The model stays on disk; only the client handle is released.
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
                translated += translateOne(client, text)
            }
            Result.success(translated)
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            // Each call builds its own client, so it must release it; the model
            // itself is cached by ML Kit and stays downloaded.
            client.close()
        }
    }

    private suspend fun translateOne(client: Translator, text: String): String =
        suspendCancellableCoroutine<String> { continuation ->
            client.translate(text)
                .addOnSuccessListener { value ->
                    if (continuation.isActive) continuation.resume(value)
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
        }

    /** Releases the ML Kit client held by this translator, if any. */
    fun close() = Unit
}
