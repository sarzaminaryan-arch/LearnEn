package ir.laxipatem.lexiflash.domain.quiz

import kotlin.math.abs
import kotlin.random.Random

/**
 * Builds deterministic multiple-choice quizzes without any AI or network call.
 *
 * Guarantees
 * ----------
 * * Between [minQuestions] and [maxQuestions] questions per quiz (6 by default),
 *   fewer only when the part does not hold enough usable cards.
 * * Every question has between 2 and 4 options.
 * * The correct answer appears exactly once, and options are never duplicated.
 * * The same seed on the same input always yields the same quiz.
 * * Cards without a Persian translation are never used for MEANING or REVERSE
 *   questions; only CLOZE can use them.
 */
class QuizGenerator(
    private val minQuestions: Int = 5,
    private val maxQuestions: Int = 7,
    private val targetQuestions: Int = 6,
    private val maxOptions: Int = 4
) {

    init {
        require(minQuestions in 1..maxQuestions) { "minQuestions must be within 1..maxQuestions" }
        require(targetQuestions in minQuestions..maxQuestions) {
            "targetQuestions must lie within min..maxQuestions"
        }
        require(maxOptions in 2..4) { "maxOptions must be within 2..4, a 4-option quiz is the usual shape" }
    }

    /**
     * Generates a quiz for one part.
     *
     * @param deckId identifier stored on the resulting [Quiz].
     * @param partIndex the part being quizzed.
     * @param partCards cards of the part; questions are drawn from these first.
     * @param otherCards cards from other parts of the same deck, used only when
     *   the part alone cannot supply enough distractors.
     * @param seed makes the output reproducible.
     */
    fun generate(
        deckId: String,
        partIndex: Int,
        partCards: List<QuizCard>,
        otherCards: List<QuizCard> = emptyList(),
        seed: Long = 0L,
        questionCount: Int = targetQuestions
    ): Quiz {
        val random = Random(seed)
        val wanted = questionCount.coerceIn(minQuestions, maxQuestions)
        val usable = partCards.filter { it.english.isNotBlank() }
        if (usable.isEmpty()) return Quiz(deckId, partIndex, emptyList())

        val pool = usable.shuffled(random)
        val questions = ArrayList<QuizQuestion>(wanted)
        val usedCardIds = HashSet<String>()

        for (card in pool) {
            if (questions.size >= wanted) break
            if (!usedCardIds.add(card.id)) continue

            val kind = kindFor(card, random)
            val question = when (kind) {
                QuizKind.CLOZE -> buildCloze(card, usable, otherCards, random)
                QuizKind.REVERSE -> buildReverse(card, pool, otherCards, random)
                QuizKind.MEANING -> buildMeaning(card, pool, otherCards, random)
            } ?: continue

            questions += question
        }

        // If strict rules rejected too many cards, top the quiz up with any
        // remaining question kind that can still be built.
        if (questions.size < wanted) {
            for (card in pool) {
                if (questions.size >= wanted) break
                if (usedCardIds.contains(card.id)) continue
                val fallback = buildMeaning(card, pool, otherCards, random)
                    ?: buildReverse(card, pool, otherCards, random)
                    ?: buildCloze(card, usable, otherCards, random)
                    ?: continue
                usedCardIds += card.id
                questions += fallback
            }
        }

        return Quiz(deckId, partIndex, questions)
    }

    /** Picks a question kind that the card can actually support. */
    private fun kindFor(card: QuizCard, random: Random): QuizKind {
        val hasTranslation = !card.translatePending && !card.persian.isNullOrBlank()
        val hasClozeWord = clozeCandidate(card) != null
        return when {
            hasTranslation && hasClozeWord -> when (random.nextInt(3)) {
                0 -> QuizKind.CLOZE
                1 -> QuizKind.REVERSE
                else -> QuizKind.MEANING
            }
            hasTranslation -> if (random.nextBoolean()) QuizKind.MEANING else QuizKind.REVERSE
            hasClozeWord -> QuizKind.CLOZE
            else -> QuizKind.MEANING
        }
    }

    // --- MEANING -------------------------------------------------------------

    private fun buildMeaning(
        card: QuizCard,
        partPool: List<QuizCard>,
        otherCards: List<QuizCard>,
        random: Random
    ): QuizQuestion? {
        val correct = card.persian?.takeIf { it.isNotBlank() } ?: return null
        if (card.translatePending) return null

        val candidates = translationDistractors(card, correct, partPool, otherCards)
        val options = assembleOptions(correct, candidates, random) ?: return null
        return QuizQuestion(
            kind = QuizKind.MEANING,
            cardId = card.id,
            prompt = card.english,
            options = options.choices,
            correctIndex = options.correctIndex,
            explanationPersian = correct,
            explanationIpa = card.ipa?.takeIf { it.isNotBlank() },
            focusWord = null
        )
    }

    // --- REVERSE -------------------------------------------------------------

    private fun buildReverse(
        card: QuizCard,
        partPool: List<QuizCard>,
        otherCards: List<QuizCard>,
        random: Random
    ): QuizQuestion? {
        val correctPersian = card.persian?.takeIf { it.isNotBlank() } ?: return null
        if (card.translatePending) return null

        val correctEnglish = card.english
        val candidates = partPool
            .asSequence()
            .filter { it.id != card.id && !it.translatePending }
            .filter { it.persian == correctPersian }
            .map { it.english }
            .plus(
                otherCards.asSequence()
                    .filter { it.id != card.id && !it.translatePending }
                    .filter { it.persian == correctPersian }
                    .map { it.english }
            )
            .plus(
                // Same-part English sentences with the closest length are the
                // strongest distractors when no shared translation exists.
                partPool.asSequence()
                    .filter { it.id != card.id }
                    .sortedBy { abs(it.english.length - correctEnglish.length) }
                    .map { it.english }
            )
            .plus(otherCards.asSequence().sortedBy { abs(it.english.length - correctEnglish.length) }.map { it.english })
            .toList()

        val options = assembleOptions(correctEnglish, candidates, random) ?: return null
        return QuizQuestion(
            kind = QuizKind.REVERSE,
            cardId = card.id,
            prompt = correctPersian,
            options = options.choices,
            correctIndex = options.correctIndex,
            explanationPersian = correctPersian,
            explanationIpa = card.ipa?.takeIf { it.isNotBlank() },
            focusWord = null
        )
    }

    // --- CLOZE ---------------------------------------------------------------

    /** Finds a vocab word that literally occurs in the English sentence. */
    internal fun clozeCandidate(card: QuizCard): String? {
        for (word in card.vocab) {
            val trimmed = word.trim()
            if (trimmed.length < 3) continue
            if (containsWord(card.english, trimmed)) return trimmed
        }
        return null
    }

    private fun buildCloze(
        card: QuizCard,
        partPool: List<QuizCard>,
        otherCards: List<QuizCard>,
        random: Random
    ): QuizQuestion? {
        val word = clozeCandidate(card) ?: return null
        val blanked = blankOut(card.english, word) ?: return null

        // Distractors: vocab words of other cards in the part with a similar
        // length, then other cards of the deck, then nothing.
        val sameLength = { candidate: String ->
            candidate.length in (word.length - 3)..(word.length + 3) &&
                !candidate.equals(word, ignoreCase = true)
        }
        val candidates = partPool.asSequence()
            .filter { it.id != card.id }
            .flatMap { it.vocab.asSequence() }
            .map { it.trim() }
            .filter { it.length >= 3 && sameLength(it) && containsWord(card.english, it).not() }
            .plus(
                otherCards.asSequence()
                    .flatMap { it.vocab.asSequence() }
                    .map { it.trim() }
                    .filter { it.length >= 3 && sameLength(it) }
            )
            .toList()

        val options = assembleOptions(word, candidates, random) ?: return null
        return QuizQuestion(
            kind = QuizKind.CLOZE,
            cardId = card.id,
            prompt = blanked,
            options = options.choices,
            correctIndex = options.correctIndex,
            explanationPersian = card.persian?.takeIf { it.isNotBlank() },
            explanationIpa = card.ipa?.takeIf { it.isNotBlank() },
            focusWord = word
        )
    }

    // --- Shared helpers ------------------------------------------------------

    private data class Options(val choices: List<String>, val correctIndex: Int)

    /**
     * Picks unique distractors, pads from [candidates] in order and shuffles.
     *
     * Returns null when fewer than two options could be produced, in which case
     * the caller drops the question rather than showing a single-answer prompt.
     */
    private fun assembleOptions(
        correct: String,
        candidates: List<String>,
        random: Random
    ): Options? {
        if (correct.isBlank()) return null
        val chosen = LinkedHashSet<String>()
        for (candidate in candidates) {
            val value = candidate.trim()
            if (value.isEmpty()) continue
            if (value.equals(correct, ignoreCase = true)) continue
            if (chosen.any { it.equals(value, ignoreCase = true) }) continue
            chosen += value
            if (chosen.size >= maxOptions - 1) break
        }
        if (chosen.isEmpty()) return null

        val all = ArrayList<String>(chosen.size + 1)
        all += correct
        all += chosen
        all.shuffle(random)
        return Options(all, all.indexOf(correct))
    }

    private fun translationDistractors(
        card: QuizCard,
        correct: String,
        partPool: List<QuizCard>,
        otherCards: List<QuizCard>
    ): List<String> {
        fun project(source: List<QuizCard>) = source.asSequence()
            .filter { it.id != card.id }
            .filter { !it.translatePending }
            .mapNotNull { it.persian?.takeIf(String::isNotBlank) }
            .distinct()

        val inPart = project(partPool).toList()
        val elsewhere = project(otherCards).toList()
        return (inPart + elsewhere).filter { !it.equals(correct, ignoreCase = true) }
    }
}

/** Word-boundary aware containment test that tolerates apostrophes. */
internal fun containsWord(haystack: String, needle: String): Boolean {
    if (needle.isEmpty()) return false
    var index = haystack.indexOf(needle, ignoreCase = true)
    while (index >= 0) {
        val before = haystack.getOrNull(index - 1)
        val after = haystack.getOrNull(index + needle.length)
        val beforeOk = before == null || !before.isLetter() && before != '\''
        val afterOk = after == null || !after.isLetter() && after != '\''
        if (beforeOk && afterOk) return true
        index = haystack.indexOf(needle, index + 1, ignoreCase = true)
    }
    return false
}

/** Replaces the first standalone occurrence of [word] with a blank. */
internal fun blankOut(sentence: String, word: String): String? {
    if (!containsWord(sentence, word)) return null
    var index = sentence.indexOf(word, ignoreCase = true)
    while (index >= 0) {
        val before = sentence.getOrNull(index - 1)
        val after = sentence.getOrNull(index + word.length)
        val beforeOk = before == null || !before.isLetter() && before != '\''
        val afterOk = after == null || !after.isLetter() && after != '\''
        if (beforeOk && afterOk) {
            return sentence.substring(0, index) + BLANK + sentence.substring(index + word.length)
        }
        index = sentence.indexOf(word, index + 1, ignoreCase = true)
    }
    return null
}

/** The blank marker used in cloze prompts. */
const val BLANK: String = "____"
