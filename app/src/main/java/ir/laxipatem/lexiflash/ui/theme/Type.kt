package ir.laxipatem.lexiflash.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography built entirely on the platform font.
 *
 * No custom font is bundled: the system font already covers Persian and Latin
 * glyphs with correct shaping, and shipping a Persian webfont would add roughly
 * a megabyte to every build for no behavioural gain.
 */
private val Default = Typography()

val LexiFlashTypography = Typography(
    displayLarge = Default.displayLarge.copy(fontFamily = FontFamily.Default),
    displayMedium = Default.displayMedium.copy(fontFamily = FontFamily.Default),
    displaySmall = Default.displaySmall.copy(fontFamily = FontFamily.Default),
    headlineLarge = Default.headlineLarge.copy(fontFamily = FontFamily.Default),
    headlineMedium = Default.headlineMedium.copy(fontFamily = FontFamily.Default),
    headlineSmall = Default.headlineSmall.copy(fontFamily = FontFamily.Default),
    titleLarge = Default.titleLarge.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold),
    titleMedium = Default.titleMedium.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium),
    titleSmall = Default.titleSmall.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium),
    bodyLarge = Default.bodyLarge.copy(fontFamily = FontFamily.Default),
    bodyMedium = Default.bodyMedium.copy(fontFamily = FontFamily.Default),
    bodySmall = Default.bodySmall.copy(fontFamily = FontFamily.Default),
    labelLarge = Default.labelLarge.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium),
    labelMedium = Default.labelMedium.copy(fontFamily = FontFamily.Default),
    labelSmall = Default.labelSmall.copy(fontFamily = FontFamily.Default)
)

/** The large, readable style used for the sentence on the front of a flashcard. */
val FlashcardSentenceStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 26.sp,
    lineHeight = 38.sp
)

/** The translation style on the back of the card. */
val FlashcardTranslationStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 22.sp,
    lineHeight = 34.sp
)

/** IPA and other phonetic strings. */
val PhoneticStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp
)
