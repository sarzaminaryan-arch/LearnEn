package ir.laxipatem.lexiflash.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Clear, medium-to-semibold platform sans-serif typography for both Persian
 * and Latin scripts. No custom font is bundled, so the APK stays lean and the
 * operating system can use its best available glyph fallback for Persian.
 */
private val Default = Typography()

val LexiFlashTypography = Typography(
    displayLarge = Default.displayLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold),
    displayMedium = Default.displayMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold),
    displaySmall = Default.displaySmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold),
    headlineLarge = Default.headlineLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
    headlineMedium = Default.headlineMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
    headlineSmall = Default.headlineSmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
    titleLarge = Default.titleLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
    titleMedium = Default.titleMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
    titleSmall = Default.titleSmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium),
    bodyLarge = Default.bodyLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium),
    bodyMedium = Default.bodyMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium),
    bodySmall = Default.bodySmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium),
    labelLarge = Default.labelLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
    labelMedium = Default.labelMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium),
    labelSmall = Default.labelSmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium)
)

/** The large, readable style used for the sentence on the front of a flashcard. */
val FlashcardSentenceStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 27.sp,
    lineHeight = 39.sp
)

/** The translation style on the back of the card. */
val FlashcardTranslationStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 23.sp,
    lineHeight = 35.sp
)
