package ir.laxipatem.lexiflash.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.laxipatem.lexiflash.AppContainer
import ir.laxipatem.lexiflash.data.repo.UserSettings
import ir.laxipatem.lexiflash.domain.unlock.UnlockPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One deck row on the home screen. */
data class DeckUiModel(
    val id: Long,
    val title: String,
    val isCore: Boolean,
    val license: String,
    val cardCount: Int,
    val seenCount: Int,
    val partCount: Int
) {
    val completionRatio: Float
        get() = if (cardCount <= 0) 0f else seenCount.toFloat() / cardCount.toFloat()
}

/** One part tile: its number, size and lock state. */
data class PartUiModel(
    val index: Int,
    val cardCount: Int,
    val seenCount: Int,
    val access: UnlockPolicy.PartAccess,
    val bestScorePercent: Int?
) {
    val completionRatio: Float
        get() = if (cardCount <= 0) 0f else seenCount.toFloat() / cardCount.toFloat()
}

/** Everything the home screen renders. */
data class HomeUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val decks: List<DeckUiModel> = emptyList(),
    val selectedDeckId: Long? = null,
    val parts: List<PartUiModel> = emptyList()
)

/**
 * Loads decks and the parts of the selected deck, applies [UnlockPolicy] and
 * exposes a single immutable state object.
 *
 * Loading the parts of *one* deck at a time keeps the query count constant no
 * matter how many decks the learner has imported.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val selectedDeckId = MutableStateFlow<Long?>(null)

    private val deckRows = container.decks.decks.map { decks ->
        decks.map { deck ->
            DeckUiModel(
                id = deck.id,
                title = deck.title,
                isCore = deck.isCore,
                license = deck.license,
                cardCount = deck.cardCount,
                seenCount = deck.seenCount,
                partCount = deck.partCount
            )
        }
    }

    /** All decks plus the resolved selection, defaulting to the core deck. */
    private val deckListAndSelection = combine(deckRows, selectedDeckId) { decks, requested ->
        val selected = when {
            decks.isEmpty() -> null
            requested == null -> decks.firstOrNull { it.isCore } ?: decks.first()
            else -> decks.firstOrNull { it.id == requested } ?: decks.first()
        }
        decks to selected
    }

    private val partsFlow = combine(
        deckListAndSelection,
        container.settings.settings.map { it.unlockAllParts }
    ) { (_, selected), unlockAll ->
        selected to unlockAll
    }.flatMapLatest { (selected, unlockAll) ->
        if (selected == null) {
            flowOf(emptyList())
        } else {
            combine(
                container.decks.partSummaries(selected.id),
                quizScoresFlow(selected.id)
            ) { summaries, scores ->
                buildPartModels(summaries, scores, unlockAll)
            }
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        deckListAndSelection,
        partsFlow,
        container.settings.settings
    ) { (decks, selected), parts, settings ->
        HomeUiState(
            isLoading = false,
            errorMessage = null,
            decks = decks,
            selectedDeckId = selected?.id,
            parts = parts
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = HomeUiState()
    )

    val settings: StateFlow<UserSettings> = container.settings.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UserSettings()
        )

    fun selectDeck(deckId: Long) {
        selectedDeckId.value = deckId
    }

    /** Re-runs deck seeding, used by the retry button when the first launch failed. */
    fun retry() {
        viewModelScope.launch { container.decks.ensureCoreDeck() }
    }

    /** Wraps the suspend score lookup so it can be combined with a Flow. */
    private fun quizScoresFlow(deckId: Long): kotlinx.coroutines.flow.Flow<Map<Int, Int?>> =
        kotlinx.coroutines.flow.flow { emit(container.decks.bestQuizScores(deckId)) }

    private fun buildPartModels(
        summaries: List<ir.laxipatem.lexiflash.data.db.PartSummary>,
        scores: Map<Int, Int?>,
        unlockAll: Boolean
    ): List<PartUiModel> {
        val statuses = summaries.map { summary ->
            UnlockPolicy.PartStatus(
                partIndex = summary.partIndex,
                cardCount = summary.cardCount,
                seenCount = summary.seenCount,
                bestScorePercent = scores[summary.partIndex]
            )
        }
        val access = UnlockPolicy.compute(statuses, unlockAll)
        return summaries.mapIndexed { index, summary ->
            PartUiModel(
                index = summary.partIndex,
                cardCount = summary.cardCount,
                seenCount = summary.seenCount,
                access = access.getOrElse(index) { UnlockPolicy.PartAccess.LOCKED },
                bestScorePercent = scores[summary.partIndex]
            )
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
