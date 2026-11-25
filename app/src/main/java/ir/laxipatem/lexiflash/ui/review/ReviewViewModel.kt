package ir.laxipatem.lexiflash.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.laxipatem.lexiflash.AppContainer
import ir.laxipatem.lexiflash.data.db.CardWithState
import ir.laxipatem.lexiflash.data.db.Converters
import ir.laxipatem.lexiflash.domain.fsrs.CardMemory
import ir.laxipatem.lexiflash.domain.fsrs.Fsrs
import ir.laxipatem.lexiflash.domain.fsrs.Rating
import ir.laxipatem.lexiflash.ui.study.RatingPrediction
import ir.laxipatem.lexiflash.ui.study.StudyCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable state of the review queue. */
data class ReviewUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val queue: List<StudyCard> = emptyList(),
    val currentIndex: Int = 0,
    val isFlipped: Boolean = false,
    val isSaving: Boolean = false,
    val isComplete: Boolean = false,
    val predictions: List<RatingPrediction> = emptyList(),
    val reviewedCount: Int = 0
) {
    val currentCard: StudyCard? get() = queue.getOrNull(currentIndex)
    val remaining: Int get() = (queue.size - currentIndex).coerceAtLeast(0)
    val isEmpty: Boolean get() = !isLoading && queue.isEmpty()
}

/**
 * Serves the spaced-repetition queue: every card whose due time has passed,
 * oldest first.
 *
 * The queue is snapshotted once when the screen opens. Cards answered "Again"
 * today therefore come back tomorrow rather than immediately, which avoids a
 * review loop that never ends while still honouring the ten-minute relearning
 * step recorded by FSRS.
 */
class ReviewViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load(nowMs: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                container.reviews.dueCards(nowMs)
            }.onSuccess { cards ->
                val queue = cards.map { it.toStudyCard() }
                _uiState.value = ReviewUiState(
                    isLoading = false,
                    queue = queue,
                    isComplete = queue.isEmpty(),
                    predictions = predictionsFor(queue.firstOrNull(), nowMs)
                )
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = error.message ?: "review-load-failed")
                }
            }
        }
    }

    fun flip() {
        _uiState.update { state ->
            if (state.isComplete) state else state.copy(isFlipped = !state.isFlipped)
        }
    }

    fun rate(rating: Int, nowMs: Long = System.currentTimeMillis()) {
        val state = _uiState.value
        if (state.isComplete || state.isSaving) return
        val card = state.currentCard ?: return
        if (!Rating.isValid(rating)) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            container.reviews.submitReview(card.id, rating, nowMs)
                .onSuccess {
                    val nextIndex = state.currentIndex + 1
                    val complete = nextIndex >= state.queue.size
                    _uiState.update { current ->
                        current.copy(
                            isSaving = false,
                            currentIndex = if (complete) current.queue.size else nextIndex,
                            isComplete = complete,
                            isFlipped = false,
                            reviewedCount = current.reviewedCount + 1,
                            predictions = if (complete) {
                                emptyList()
                            } else {
                                predictionsFor(current.queue.getOrNull(nextIndex), nowMs)
                            }
                        )
                    }
                }
                .onFailure {
                    _uiState.update { current ->
                        current.copy(isSaving = false, errorMessage = "save-failed")
                    }
                }
        }
    }

    private fun predictionsFor(card: StudyCard?, nowMs: Long): List<RatingPrediction> {
        if (card == null) return emptyList()
        return (Rating.AGAIN..Rating.EASY).map { rating ->
            val result = Fsrs.review(memory = card.memory, rating = rating, nowMs = nowMs)
            RatingPrediction(
                rating = rating,
                days = result.scheduledDays.toInt(),
                minutes = result.scheduledMinutes
            )
        }
    }

    private fun CardWithState.toStudyCard(): StudyCard = StudyCard(
        id = id,
        english = english,
        persian = persian,
        ipa = ipa,
        vocab = Converters.decodeVocab(vocabJson),
        note = note,
        pendingTranslation = translatePending,
        memory = if (stability == null || difficulty == null) {
            null
        } else {
            CardMemory(
                stability = stability,
                difficulty = difficulty,
                state = state ?: 0,
                lastReviewAtMs = lastReviewAt,
                reps = reps ?: 0,
                lapses = lapses ?: 0
            )
        }
    )
}
