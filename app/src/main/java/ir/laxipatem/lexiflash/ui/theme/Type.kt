package ir.laxipatem.lexiflash.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ir.laxipatem.lexiflash.R

/**
 * Vazirmatn is bundled so Persian and Latin text render consistently on every
 * Android device, including devices whose system font has incomplete Persian
 * coverage. The regular, medium, semibold and bold files keep emphasis subtle
 * and avoid synthetic weights.
 */
private val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

private val Default = Typography()

/** App-wide typography: clear body text with slightly stronger headings/actions. */
val LexiFlashTypography = Typography(
    displayLarge = Default.displayLarge.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold),
    displayMedium = Default.displayMedium.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold),
    displaySmall = Default.displaySmall.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold),
    headlineLarge = Default.headlineLarge.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold),
    headlineMedium = Default.headlineMedium.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold),
    headlineSmall = Default.headlineSmall.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold),
    titleLarge = Default.titleLarge.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold),
    titleMedium = Default.titleMedium.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold),
    titleSmall = Default.titleSmall.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium),
    bodyLarge = Default.bodyLarge.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium),
    bodyMedium = Default.bodyMedium.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium),
    bodySmall = Default.bodySmall.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Normal),
    labelLarge = Default.labelLarge.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold),
    labelMedium = Default.labelMedium.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium),
    labelSmall = Default.labelSmall.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium)
)

/** The large, readable style used for the sentence on the front of a flashcard. */
val FlashcardSentenceStyle = TextStyle(
    fontFamily = Vazirmatn,
    fontWeight = FontWeight.SemiBold,
    fontSize = 26.sp,
    lineHeight = 38.sp
)

/** The translation style on the back of the card. */
val FlashcardTranslationStyle = TextStyle(
    fontFamily = Vazirmatn,
    fontWeight = FontWeight.Medium,
    fontSize = 22.sp,
    lineHeight = 34.sp
)

/** IPA and other phonetic strings. */
val PhoneticStyle = TextStyle(
    fontFamily = Vazirmatn,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp
)
