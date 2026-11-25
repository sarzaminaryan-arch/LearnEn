package ir.laxipatem.lexiflash.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.laxipatem.lexiflash.AppContainer
import ir.laxipatem.lexiflash.data.repo.StudyStats
import ir.laxipatem.lexiflash.data.repo.UserSettings
import ir.laxipatem.lexiflash.domain.unlock.UnlockPolicy
import ir.laxipatem.lexiflash.ui.home.PartUiModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Outcome of an export attempt, shown as a banner on the progress screen. */
data class ExportUiState(
    val uri: String? = null,
    val error: Boolean = false,
    val isExporting: Boolean = false
)

/** Immutable state of the progress screen. */
data class ProgressUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val stats: StudyStats = StudyStats(0, 0, 0, 0, 0, 0),
    val dailyGoal: Int = UserSettings.DEFAULT_DAILY_GOAL,
    val parts: List<PartUiModel> = emptyList(),
    val deckTitle: String = "",
    val deckId: Long? = null
)

/**
 * Aggregates the numbers behind the progress screen.
 *
 * The snapshot is recomputed whenever the review log changes, because every
 * metric here derives from it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(private val container: AppContainer) : ViewModel() {

    private val now = MutableStateFlow(System.currentTimeMillis())

    private val selectedDeck = container.decks.decks.map { decks ->
        decks.firstOrNull { it.isCore } ?: decks.firstOrNull()
    }

    private val partsFlow = combine(
        selectedDeck,
        container.settings.settings.map { it.unlockAllParts }
    ) { deck, unlockAll -> deck to unlockAll }
        .flatMapLatest { (deck, unlockAll) ->
            if (deck == null) {
                flowOf(emptyList())
            } else {
                combine(
                    container.decks.partSummaries(deck.id),
                    quizScoresFlow(deck.id)
                ) { summaries, scores ->
                    val statuses = summaries.map { summary ->
                        UnlockPolicy.PartStatus(
                            partIndex = summary.partIndex,
                            cardCount = summary.cardCount,
                            seenCount = summary.seenCount,
                            bestScorePercent = scores[summary.partIndex]
                        )
                    }
                    val access = UnlockPolicy.compute(statuses, unlockAll)
                    summaries.mapIndexed { index, summary ->
                        PartUiModel(
                            index = summary.partIndex,
                            cardCount = summary.cardCount,
                            seenCount = summary.seenCount,
                            access = access.getOrElse(index) { UnlockPolicy.PartAccess.LOCKED },
                            bestScorePercent = scores[summary.partIndex]
                        )
                    }
                }
            }
        }

    private fun quizScoresFlow(deckId: Long): kotlinx.coroutines.flow.Flow<Map<Int, Int?>> =
        flow { emit(container.decks.bestQuizScores(deckId)) }

    val uiState: StateFlow<ProgressUiState> = combine(
        partsFlow,
        container.settings.settings,
        selectedDeck
    ) { parts, settings, deck ->
        Triple(parts, settings, deck)
    }.flatMapLatest { (parts, settings, deck) ->
        flow<ProgressUiState> {
            val stats = runCatching { container.stats.snapshot(now.value) }
                .getOrElse { StudyStats(0, 0, 0, 0, 0, 0) }
            emit(
                ProgressUiState(
                    isLoading = false,
                    stats = stats,
                    dailyGoal = settings.dailyGoal,
                    parts = parts,
                    deckTitle = deck?.title.orEmpty(),
                    deckId = deck?.id
                )
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = ProgressUiState()
    )

    private val _exportState = MutableStateFlow(ExportUiState())
    val exportState: StateFlow<ExportUiState> = _exportState.asStateFlow()

    /**
     * Writes the selected deck to the app cache and publishes a shareable URI.
     *
     * The file is a standard deck.json document, so it can be re-imported on
     * another device without any conversion step.
     */
    fun exportDeck() {
        val state = uiState.value
        val deckId = state.deckId ?: return
        if (_exportState.value.isExporting) return
        viewModelScope.launch {
            _exportState.update { it.copy(isExporting = true, error = false, uri = null) }
            runCatching {
                val json = container.decks.exportDeck(deckId).getOrThrow()
                val title = state.deckTitle.ifBlank { DEFAULT_EXPORT_NAME }
                container.exporter.writeDeckFile(title, json).getOrThrow()
            }.onSuccess { exported ->
                _exportState.update { ExportUiState(uri = exported.uri.toString()) }
            }.onFailure {
                _exportState.update { ExportUiState(error = true) }
            }
        }
    }

    /**
     * Subject line for the share sheet: the deck title the learner sees, or a
     * stable fallback when the deck has no title yet.
     */
    val exportSubject: String
        get() = uiState.value.deckTitle.ifBlank { DEFAULT_EXPORT_NAME }

    /** Clears the export banner after it has been shown. */
    fun clearExportResult() {
        _exportState.value = ExportUiState()
    }

    /** Recomputes the snapshot when the screen is revisited. */
    fun refresh() {
        now.value = System.currentTimeMillis()
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Fallback file name when a deck has no title. */
        const val DEFAULT_EXPORT_NAME = "lexiflash-deck"
    }
}
