package ir.laxipatem.lexiflash.domain.quiz

/** The three question flavours the generator can produce. */
enum class QuizKind {
    /** English sentence shown, pick the correct Persian meaning. */
    MEANING,

    /** Persian translation shown, pick the correct English sentence. */
    REVERSE,

    /** An English sentence with one word blanked out, pick the missing word. */
    CLOZE
}

/**
 * A single quiz question.
 *
 * [options] always contain [correctIndex] exactly once, and option strings are
 * unique within a question. [cardId] identifies the card the question came from,
 * which is what the review log stores.
 */
data class QuizQuestion(
    val kind: QuizKind,
    val cardId: String,
    /** The prompt: the English sentence, the Persian sentence or the cloze sentence. */
    val prompt: String,
    val options: List<String>,
    val correctIndex: Int,
    /** Persian sentence shown as part of the feedback, when it exists. */
    val explanationPersian: String?,
    /** IPA transcription of the English sentence, when available. */
    val explanationIpa: String?,
    /** The key word the question is about, for feedback and cloze. */
    val focusWord: String?
) {
    val correctOption: String get() = options[correctIndex]
}

/** A generated quiz for one part. */
data class Quiz(
    val deckId: String,
    val partIndex: Int,
    val questions: List<QuizQuestion>
) {
    val total: Int get() = questions.size
}

/** A card projection used by the generator; deliberately storage agnostic. */
data class QuizCard(
    val id: String,
    val english: String,
    val persian: String?,
    val ipa: String?,
    val vocab: List<String>,
    /** True when the card has no human translation yet. */
    val translatePending: Boolean
)
