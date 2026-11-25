package ir.laxipatem.lexiflash.platform

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes deck.json files into `cacheDir/exports` and hands them to other apps
 * through a [FileProvider]-backed `ACTION_SEND` intent.
 *
 * The provider is declared `exported=false` with `grantUriPermissions=true` in
 * the manifest, and the exposed directory is declared in `res/xml/file_paths.xml`.
 * Nothing ever lands in shared external storage, so no storage permission is
 * needed anywhere in the app.
 */
class DeckExporter(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /** Result of a successful export. */
    data class ExportedFile(val file: File, val uri: android.net.Uri)

    /** Writes [json] to a sanitised file name and returns the shareable handle. */
    suspend fun writeDeckFile(deckTitle: String, json: String): Result<ExportedFile> =
        withContext(ioDispatcher) {
            runCatching {
                val directory = File(context.cacheDir, EXPORT_DIRECTORY)
                if (!directory.exists() && !directory.mkdirs()) {
                    throw IOException("The export directory could not be created.")
                }
                // Keep only a bounded, filesystem-safe subset of the title.
                val safeName = deckTitle
                    .map { character ->
                        if (character.isLetterOrDigit() || character == '-' || character == '_') character else '-'
                    }
                    .joinToString("")
                    .trim('-', '_')
                    .take(48)
                    .ifEmpty { "deck" }
                val file = File(directory, "$safeName.json")
                file.writeText(json, Charsets.UTF_8)

                val uri = FileProvider.getUriForFile(
                    context,
                    context.packageName + FILE_PROVIDER_SUFFIX,
                    file
                )
                ExportedFile(file, uri)
            }
        }

    /** Builds the `ACTION_SEND` chooser intent for [exported]. */
    fun shareIntent(exported: ExportedFile, subject: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, exported.uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, subject).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Removes previously exported files; called when the app starts to stay tidy. */
    suspend fun clearCache() = withContext(ioDispatcher) {
        val directory = File(context.cacheDir, EXPORT_DIRECTORY)
        if (directory.exists()) {
            directory.listFiles()?.forEach { it.delete() }
        }
    }

    companion object {
        const val EXPORT_DIRECTORY = "exports"
        const val MIME_TYPE = "application/json"
        const val FILE_PROVIDER_SUFFIX = ".fileprovider"
    }
}
