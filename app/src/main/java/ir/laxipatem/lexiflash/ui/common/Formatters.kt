package ir.laxipatem.lexiflash.ui.common

import java.util.Locale

/**
 * Number and interval formatting.
 *
 * Persian digits are opt-in: the learner picks "Persian digits" in Settings,
 * and this object is the single place that decision is applied so that every
 * screen formats numbers identically.
 */
object Formatters {

    private val PERSIAN_LOCALE = Locale("fa", "IR")
    private val LATIN_LOCALE = Locale.US

    private val PERSIAN_ZERO = '\u06F0'
    private val ARABIC_INDIC_ZERO = '\u0660'

    /**
     * Formats [value] in the requested digit system.
     *
     * @param usePersianDigits render ۰۱۲۳۴۵۶۷۸۹ instead of 0123456789.
     */
    fun number(value: Long, usePersianDigits: Boolean): String {
        val text = String.format(LATIN_LOCALE, "%,d", value)
        return if (usePersianDigits) toPersianDigits(text) else text
    }

    fun number(value: Int, usePersianDigits: Boolean): String = number(value.toLong(), usePersianDigits)

    /** Converts ASCII digits in [text] to Persian digits, leaving everything else alone. */
    fun toPersianDigits(text: String): String = buildString(text.length) {
        for (character in text) {
            if (character in '0'..'9') {
                append(PERSIAN_ZERO + (character - '0'))
            } else {
                append(character)
            }
        }
    }

    /** Converts Persian and Arabic-Indic digits in [text] back to ASCII. */
    fun toLatinDigits(text: String): String = buildString(text.length) {
        for (character in text) {
            when (character) {
                in PERSIAN_ZERO..(PERSIAN_ZERO + 9) -> append('0' + (character - PERSIAN_ZERO))
                in ARABIC_INDIC_ZERO..(ARABIC_INDIC_ZERO + 9) -> append('0' + (character - ARABIC_INDIC_ZERO))
                else -> append(character)
            }
        }
    }

    /** Percentage as a whole number, e.g. `82`. */
    fun percent(value: Int, usePersianDigits: Boolean): String =
        if (usePersianDigits) toPersianDigits(value.toString()) else value.toString()

    /**
     * Buckets a scheduled interval into the unit the UI should use.
     *
     * Returns a [IntervalText] that carries the unit, so the caller can pick the
     * matching plural string and never concatenate numbers with hard-coded words.
     */
    fun interval(days: Int, minutes: Long?): IntervalText = when {
        minutes != null -> IntervalText.Minutes(minutes)
        days < 30 -> IntervalText.Days(days)
        days < 365 -> IntervalText.Months((days / 30).coerceAtLeast(1))
        else -> IntervalText.Years((days / 365).coerceAtLeast(1))
    }

    /** Localised name of the locale used for Persian digits, exposed for tests. */
    internal fun persianLocale(): Locale = PERSIAN_LOCALE
}

/** A scheduled interval expressed in the unit the interface should display. */
sealed interface IntervalText {
    data class Minutes(val value: Long) : IntervalText
    data class Days(val value: Int) : IntervalText
    data class Months(val value: Int) : IntervalText
    data class Years(val value: Int) : IntervalText
}
