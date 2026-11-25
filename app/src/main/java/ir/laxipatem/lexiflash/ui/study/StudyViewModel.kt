package ir.laxipatem.lexiflash.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.laxipatem.lexiflash.AppContainer
import ir.laxipatem.lexiflash.data.db.CardWithState
import ir.laxipatem.lexiflash.data.db.Converters
import ir.laxipatem.lexiflash.data.model.VocabJson
import ir.laxipatem.lexiflash.data.repo.ReviewOutcome
import ir.laxipatem.lexiflash.domain.fsrs.CardMemory
import ir.laxipatem.lexiflash.domain.fsrs.Fsrs
import ir.laxipatem.lexiflash.domain.fsrs.Rating
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A card as the study screen needs it. */
data class StudyCard(
    val id: String,
    val english: String,
    val persian: String?,
    val ipa: String?,
    val vocab: List<VocabJson>,
    val note: String?,
    val pendingTranslation: Boolean,
    val memory: CardMemory?
)

/** Predicted next interval shown on each rating button. */
data class RatingPrediction(
    val rating: Int,
    val days: Int,
    val minutes: Long?
)

/** Immutable state of the study session. */
data class StudyUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val deckTitle: String = "",
    val partIndex: Int = 1,
    val cards: List<StudyCard> = emptyList(),
    val currentIndex: Int = 0,
    val isFlipped: Boolean = false,
    val isFinished: Boolean = false,
    val predictions: List<RatingPrediction> = emptyList(),
    val isSaving: Boolean = false,
    val reviewCount: Int = 0
) {
    val currentCard: StudyCard? get() = cards.getOrNull(currentIndex)
    val total: Int get() = cards.size
    val progress: Float
        get() = if (cards.isEmpty()) 0f else (currentIndex.coerceAtMost(cards.size).toFloat() / cards.size.toFloat())
}

/**
 * Drives a study session over a single deck part.
 *
 * Ratings are written immediately through [ReviewRepository]; the session then
 * advances. If a write fails, the card stays put and the error is surfaced, so a
 * failed save can never silently lose a review.
 */
class StudyViewModel(
    private val container: AppContainer,
    private val deckId: Long,
    val partIndex: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudyUiState(partIndex = partIndex))
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** Loads the part's cards in deck order so a session is deterministic. */
    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val deck = container.decks.deck(deckId)
                    ?: throw IllegalStateException("deck-missing")
                val cards = container.decks.cardsOfPart(deckId, partIndex)
                deck.title to cards
            }.onSuccess { (title, cards) ->
                val studyCards = cards
                    .sortedBy { it.orderInPart }
                    .map { it.toStudyCard() }
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = if (studyCards.isEmpty()) "empty-part" else null,
                        deckTitle = title,
                        cards = studyCards,
                        currentIndex = 0,
                        isFlipped = false,
                        isFinished = studyCards.isEmpty(),
                        predictions = predictionsFor(studyCards.firstOrNull())
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "load-failed"
                    )
                }
            }
        }
    }

    /** Flips the card, which is also when the answer becomes visible. */
    fun flip() {
        _uiState.update { state ->
            if (state.isFinished) state else state.copy(isFlipped = !state.isFlipped)
        }
    }

    /**
     * Records [rating] for the current card and moves on.
     *
     * Nothing happens while a previous save is still in flight, which prevents a
     * double tap from rating the same card twice.
     */
    fun rate(rating: Int, nowMs: Long = System.currentTimeMillis()) {
        val state = _uiState.value
        if (state.isFinished || state.isSaving) return
        val card = state.currentCard ?: return
        if (!Rating.isValid(rating)) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            container.reviews.submitReview(card.id, rating, nowMs)
                .onSuccess { _: ReviewOutcome ->
                    advance()
                }
                .onFailure {
                    _uiState.update { current ->
                        current.copy(isSaving = false, errorMessage = "save-failed")
                    }
                }
        }
    }

    private fun advance() {
        _uiState.update { state ->
            val nextIndex = state.currentIndex + 1
            val finished = nextIndex >= state.cards.size
            state.copy(
                isSaving = false,
                currentIndex = if (finished) state.cards.size else nextIndex,
                isFinished = finished,
                isFlipped = false,
                reviewCount = state.reviewCount + 1,
                predictions = if (finished) emptyList() else predictionsFor(state.cards.getOrNull(nextIndex))
            )
        }
    }

    /**
     * Predicts what each rating would do, so the buttons can show the interval.
     * Uses the same FSRS call the save path uses, with the same clock.
     */
    private fun predictionsFor(card: StudyCard?, nowMs: Long = System.currentTimeMillis()): List<RatingPrediction> {
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
