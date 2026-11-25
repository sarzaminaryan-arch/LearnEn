package ir.laxipatem.lexiflash.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.laxipatem.lexiflash.AppContainer
import ir.laxipatem.lexiflash.data.db.Converters
import ir.laxipatem.lexiflash.data.db.QuizResultEntity
import ir.laxipatem.lexiflash.domain.quiz.Quiz
import ir.laxipatem.lexiflash.domain.quiz.QuizCard
import ir.laxipatem.lexiflash.domain.quiz.QuizGenerator
import ir.laxipatem.lexiflash.domain.quiz.QuizQuestion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable state of the quiz screen. */
data class QuizUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val questions: List<QuizQuestion> = emptyList(),
    val currentIndex: Int = 0,
    /** Index of the option the learner picked, or null before answering. */
    val selectedIndex: Int? = null,
    val correctCount: Int = 0,
    val isFinished: Boolean = false,
    val deckTitle: String = ""
) {
    val currentQuestion: QuizQuestion? get() = questions.getOrNull(currentIndex)
    val total: Int get() = questions.size
    val answered: Boolean get() = selectedIndex != null
    val isCurrentCorrect: Boolean
        get() = currentQuestion != null && selectedIndex == currentQuestion.correctIndex
    val progress: Float
        get() = if (questions.isEmpty()) 0f else currentIndex.toFloat() / questions.size.toFloat()
}

/**
 * Runs one quiz attempt over a part.
 *
 * The questions are generated once per attempt with a seed derived from the
 * part and an attempt counter, so retrying a quiz yields a fresh but still
 * reproducible set. The best score is stored through the quiz DAO, which is what
 * the unlock policy reads.
 */
class QuizViewModel(
    private val container: AppContainer,
    private val deckId: Long,
    val partIndex: Int,
    private val seed: Long = System.currentTimeMillis()
) : ViewModel() {

    private val generator = QuizGenerator()

    private val _uiState = MutableStateFlow(QuizUiState())
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    /** Score per question, kept separately so a retry can reset cleanly. */
    private var answeredQuestionIds: MutableSet<String> = HashSet()

    init {
        startAttempt()
    }

    /** Builds a new attempt, resetting all progress. */
    fun startAttempt() {
        viewModelScope.launch {
            _uiState.value = QuizUiState(isLoading = true)
            answeredQuestionIds = HashSet()
            runCatching {
                val deck = container.decks.deck(deckId)
                    ?: throw IllegalStateException("deck-missing")
                val partCards = container.decks.cardsOfPart(deckId, partIndex)
                val otherCards = container.decks.cardsOfDeck(deckId)
                    .filter { it.partIndex != partIndex }
                deck.title to buildQuiz(partCards, otherCards)
            }.onSuccess { (title, quiz) ->
                _uiState.value = QuizUiState(
                    isLoading = false,
                    errorMessage = if (quiz.questions.isEmpty()) "quiz-empty" else null,
                    questions = quiz.questions,
                    deckTitle = title
                )
            }.onFailure { error ->
                _uiState.value = QuizUiState(
                    isLoading = false,
                    errorMessage = error.message ?: "quiz-failed"
                )
            }
        }
    }

    /** Records the chosen option. Only the first choice counts. */
    fun answer(optionIndex: Int) {
        val state = _uiState.value
        val question = state.currentQuestion ?: return
        if (state.answered) return
        if (optionIndex !in question.options.indices) return

        val correct = optionIndex == question.correctIndex
        _uiState.update {
            it.copy(
                selectedIndex = optionIndex,
                correctCount = it.correctCount + if (correct) 1 else 0
            )
        }
        answeredQuestionIds += question.cardId
    }

    /** Advances to the next question, or finishes and persists the result. */
    fun next(nowMs: Long = System.currentTimeMillis()) {
        val state = _uiState.value
        if (!state.answered) return

        val nextIndex = state.currentIndex + 1
        if (nextIndex >= state.questions.size) {
            _uiState.update { it.copy(isFinished = true) }
            persistResult(nowMs)
        } else {
            _uiState.update { it.copy(currentIndex = nextIndex, selectedIndex = null) }
        }
    }

    private fun persistResult(nowMs: Long) {
        val state = _uiState.value
        if (state.questions.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                container.database.quizDao().insertResult(
                    QuizResultEntity(
                        deckId = deckId,
                        partIndex = partIndex,
                        score = state.correctCount,
                        total = state.questions.size,
                        takenAt = nowMs
                    )
                )
            }
        }
    }

    private fun buildQuiz(
        partCards: List<ir.laxipatem.lexiflash.data.db.CardEntity>,
        otherCards: List<ir.laxipatem.lexiflash.data.db.CardEntity>
    ): Quiz {
        val toQuizCard = { card: ir.laxipatem.lexiflash.data.db.CardEntity ->
            QuizCard(
                id = card.id,
                english = card.english,
                persian = card.persian,
                ipa = card.ipa,
                vocab = Converters.decodeVocab(card.vocabJson).map { it.word },
                translatePending = card.translatePending
            )
        }
        return generator.generate(
            deckId = deckId.toString(),
            partIndex = partIndex,
            partCards = partCards.map(toQuizCard),
            otherCards = otherCards.map(toQuizCard),
            seed = seed
        )
    }
}
