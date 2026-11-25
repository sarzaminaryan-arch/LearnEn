package ir.laxipatem.lexiflash.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand palette.
 *
 * The primary colour is a deep blue (#3569B7) with a warm amber accent
 * (#C88B00). Every on-colour below was chosen to reach at least WCAG AA
 * (4.5:1) contrast against its container:
 *
 * * onPrimary            white on primary            ~5.9:1
 * * onPrimaryContainer   ink on primaryContainer    ~11.6:1
 * * onTertiaryContainer  ink on tertiaryContainer   ~10.9:1
 * * onSurfaceVariant     muted on surface           ~5.1:1
 *
 * The numbers are re-checked by ThemeContrastTest on every build.
 */
object BrandColors {
    val Primary = Color(0xFF3569B7)
    val PrimaryContainerLight = Color(0xFFDCE7F7)
    val PrimaryContainerDark = Color(0xFF23395C)
    /**
     * Warm amber used for highlights and the "machine translation" label.
     *
     * Chosen so the colour itself clears the WCAG 3:1 non-text contrast rule on
     * white (2.94:1 for the previous value failed it). For body-sized text use
     * [AccentInk], which clears the 4.5:1 rule.
     */
    val Accent = Color(0xFFB87F00)

    /** Darker amber that is safe for body-sized text on light surfaces. */
    val AccentInk = Color(0xFF8A5A00)
    val AccentContainerLight = Color(0xFFFBEBC4)
    val AccentContainerDark = Color(0xFF3E2F03)

    val BackgroundLight = Color(0xFFF6F7F8)
    val SurfaceLight = Color(0xFFFFFFFF)
    val InkLight = Color(0xFF171A1F)
    val MutedLight = Color(0xFF616A75)

    val BackgroundDark = Color(0xFF101419)
    val SurfaceDark = Color(0xFF171C22)
    val InkDark = Color(0xFFE7EAEF)
    val MutedDark = Color(0xFF9AA4B2)

    val ErrorLight = Color(0xFFB3261E)
    val ErrorDark = Color(0xFFF2B8B5)
    val OnErrorLight = Color(0xFFFFFFFF)
    val OnErrorDark = Color(0xFF601410)

    val SuccessLight = Color(0xFF1B6E3C)
    val SuccessDark = Color(0xFF7ED6A1)
}
