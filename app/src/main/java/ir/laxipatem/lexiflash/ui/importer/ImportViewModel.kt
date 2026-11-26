package ir.laxipatem.lexiflash.ui.importer

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.laxipatem.lexiflash.AppContainer
import ir.laxipatem.lexiflash.importer.DraftDeck
import ir.laxipatem.lexiflash.importer.ImportService
import ir.laxipatem.lexiflash.importer.PartSplitter
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which step of the wizard is showing. */
enum class ImportStep { PICK, PREVIEW }

/** Immutable state of the import wizard. */
data class ImportUiState(
    val step: ImportStep = ImportStep.PICK,
    val disclaimerAccepted: Boolean = false,
    val isWorking: Boolean = false,
    val errorMessage: String? = null,
    val deckTitle: String = "",
    val cards: List<ImportService.DraftCard> = emptyList(),
    val requestedParts: Int = DEFAULT_PARTS,
    val detectedFormat: String = "",
    val isSaved: Boolean = false,
    val onlineAvailable: Boolean = false,
    val isTranslating: Boolean = false,
    val translatedCount: Int = 0
) {
    val cardCount: Int get() = cards.size
    val pendingCount: Int get() = cards.count { it.pendingTranslation }
    val partCount: Int get() = PartSplitter.effectivePartCount(cards.size, requestedParts)

    companion object {
        const val DEFAULT_PARTS = 10
        const val MIN_PARTS = 1
        const val MAX_PARTS = 20
    }
}

/**
 * Drives the import wizard: pick a file, preview and edit the result, save.
 *
 * File reading goes through the Storage Access Framework URI the caller supplies,
 * so the app never needs a storage permission. All heavy work runs on the IO
 * dispatcher and every failure surfaces as a message on the screen.
 */
class ImportViewModel(
    private val container: AppContainer,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ImportUiState(onlineAvailable = container.onlineFeatures.isAvailable)
    )
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    /** Marks the copyright disclaimer as seen; done once, then remembered. */
    fun acceptDisclaimer() {
        viewModelScope.launch {
            container.settings.acceptImportDisclaimer()
            _uiState.update { it.copy(disclaimerAccepted = true) }
        }
    }

    /** Reads the saved disclaimer flag so the dialog only appears once. */
    fun refreshDisclaimer() {
        viewModelScope.launch {
            val accepted = container.settings.current().importDisclaimerAccepted
            _uiState.update { it.copy(disclaimerAccepted = accepted) }
        }
    }

    /** Imports a single subtitle, text, CSV or deck.json file. */
    fun importSingleFile(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, errorMessage = null) }
            runCatching {
                val bytes = readBytes(uri)
                container.importService.importSingleFile(bytes, fileName, fallbackTitle = fileName)
                    .getOrThrow()
            }.onSuccess { result ->
                Log.d("LexiImport", "enter preview single cards=${result.cards.size} format=${result.detectedFormat}")
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        step = ImportStep.PREVIEW,
                        deckTitle = result.deckTitle,
                        cards = result.cards,
                        detectedFormat = result.detectedFormat,
                        requestedParts = defaultPartsFor(result.cards.size)
                    )
                }
            }.onFailure { error ->
                Log.e("LexiImport", "file import failed", error)
                _uiState.update {
                    it.copy(isWorking = false, errorMessage = error.message ?: "import-failed")
                }
            }
        }
    }

    /** Imports a pair of English and Persian subtitle files. */
    fun importBilingual(englishUri: Uri, englishName: String, persianUri: Uri, persianName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, errorMessage = null) }
            runCatching {
                val englishBytes = readBytes(englishUri)
                val persianBytes = readBytes(persianUri)
                container.importService.importBilingual(
                    englishBytes = englishBytes,
                    englishName = englishName,
                    persianBytes = persianBytes,
                    persianName = persianName,
                    fallbackTitle = englishName
                ).getOrThrow()
            }.onSuccess { result ->
                Log.d("LexiImport", "enter preview bilingual cards=${result.cards.size} paired=${result.pairedCount} pending=${result.pendingCount}")
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        step = ImportStep.PREVIEW,
                        deckTitle = result.deckTitle,
                        cards = result.cards,
                        detectedFormat = "pair",
                        requestedParts = defaultPartsFor(result.cards.size)
                    )
                }
            }.onFailure { error ->
                Log.e("LexiImport", "file import failed", error)
                _uiState.update {
                    it.copy(isWorking = false, errorMessage = error.message ?: "import-failed")
                }
            }
        }
    }

    fun setDeckTitle(title: String) = _uiState.update { it.copy(deckTitle = title.take(80)) }

    /** Adjusts the requested part count, clamped to the allowed range. */
    fun setPartCount(count: Int) = _uiState.update {
        it.copy(
            requestedParts = count.coerceIn(
                ImportUiState.MIN_PARTS,
                ImportUiState.MAX_PARTS
            )
        )
    }

    /** Edits one card's Persian translation. */
    fun editTranslation(index: Int, persian: String) = _uiState.update { state ->
        if (index !in state.cards.indices) return@update state
        val updated = state.cards.toMutableList()
        val existing = updated[index]
        val trimmed = persian.trim()
        updated[index] = existing.copy(
            persian = trimmed.takeIf { it.isNotEmpty() },
            pendingTranslation = trimmed.isEmpty()
        )
        state.copy(cards = updated)
    }

    /** Removes a card from the draft. */
    fun removeCard(index: Int) = _uiState.update { state ->
        if (index !in state.cards.indices) return@update state
        state.copy(cards = state.cards.filterIndexed { position, _ -> position != index })
    }

    /**
     * Translates the cards that still lack Persian text, using the on-device
     * model. Only available in the online build and only after consent.
     */
    fun translatePending() {
        val state = _uiState.value
        if (!state.onlineAvailable || state.isTranslating) return
        val pendingIndexes = state.cards.indices.filter { state.cards[it].pendingTranslation }
        if (pendingIndexes.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isTranslating = true, errorMessage = null) }
            runCatching {
                val pendingTexts = pendingIndexes.map { state.cards[it].english }
                val modelReady = container.onlineFeatures.isTranslationModelReady()
                if (!modelReady) {
                    container.onlineFeatures.downloadTranslationModel().getOrThrow()
                }
                container.onlineFeatures.translate(pendingTexts).getOrThrow()
            }.onSuccess { translations ->
                _uiState.update { current ->
                    val updated = current.cards.toMutableList()
                    pendingIndexes.forEachIndexed { position, cardIndex ->
                        val translated = translations.getOrNull(position)?.trim().orEmpty()
                        if (translated.isNotEmpty() && cardIndex in updated.indices) {
                            updated[cardIndex] = updated[cardIndex].copy(
                                persian = translated,
                                pendingTranslation = false,
                                machineTranslated = true
                            )
                        }
                    }
                    current.copy(
                        isTranslating = false,
                        cards = updated,
                        translatedCount = translations.size
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isTranslating = false, errorMessage = error.message ?: "translate-failed")
                }
            }
        }
    }

    /** Writes the draft deck to the database. */
    fun save() {
        val state = _uiState.value
        if (state.cards.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, errorMessage = null) }
            val parts = PartSplitter.split(state.cards, state.partCount)
            val draft = DraftDeck(
                title = state.deckTitle.ifBlank { "Imported deck" },
                license = "User supplied",
                cards = state.cards,
                parts = parts,
                source = state.detectedFormat
            )
            Log.d("LexiImport", "Room save starting cards=${draft.cardCount} parts=${draft.partCount}")
            runCatching {
                container.decks.saveImportedDeck(draft, state.partCount)
            }.onSuccess {
                Log.d("LexiImport", "Room save finished successfully")
                _uiState.update { it.copy(isWorking = false, isSaved = true) }
            }.onFailure { error ->
                Log.e("LexiImport", "Room save failed", error)
                _uiState.update {
                    it.copy(isWorking = false, errorMessage = error.message ?: "save-failed")
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    /** Returns to the pick step, discarding the draft. */
    fun restart() = _uiState.update {
        ImportUiState(
            disclaimerAccepted = it.disclaimerAccepted,
            onlineAvailable = it.onlineAvailable
        )
    }

    private suspend fun readBytes(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().also { bytes ->
                Log.d("LexiImport", "read completed uri=$uri bytes=${bytes.size}")
            }
        } ?: throw IOException("The selected file could not be opened.")
    }

    /** Small files use fewer parts so a part is never a single card. */
    private fun defaultPartsFor(cardCount: Int): Int = when {
        cardCount <= 20 -> 2
        cardCount <= 60 -> 5
        else -> ImportUiState.DEFAULT_PARTS
    }
}
