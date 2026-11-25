package ir.laxipatem.lexiflash.ui.nav

/**
 * Route definitions for the single-Activity navigation graph.
 *
 * Arguments are embedded in the path rather than passed as a bundle so that
 * process death restores the exact screen the learner was on.
 */
object Routes {
    // Bottom-bar destinations.
    const val HOME = "home"
    const val REVIEW = "review"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"

    // Pushed destinations.
    const val STUDY = "study/{deckId}/{partIndex}"
    const val QUIZ = "quiz/{deckId}/{partIndex}"
    const val QUIZ_RESULT = "quizResult/{deckId}/{partIndex}/{score}/{total}"
    const val IMPORT = "import"
    const val ABOUT = "about"
    const val CATALOG = "catalog"

    fun study(deckId: Long, partIndex: Int): String = "study/$deckId/$partIndex"

    fun quiz(deckId: Long, partIndex: Int): String = "quiz/$deckId/$partIndex"

    fun quizResult(deckId: Long, partIndex: Int, score: Int, total: Int): String =
        "quizResult/$deckId/$partIndex/$score/$total"

    /** Every destination that shows the bottom navigation bar. */
    val bottomBarDestinations: Set<String> = setOf(HOME, REVIEW, PROGRESS, SETTINGS)

    const val ARG_DECK_ID = "deckId"
    const val ARG_PART_INDEX = "partIndex"
    const val ARG_SCORE = "score"
    const val ARG_TOTAL = "total"
}
