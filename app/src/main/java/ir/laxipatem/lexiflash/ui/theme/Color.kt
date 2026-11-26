package ir.laxipatem.lexiflash.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand palette.
 *
 * The app leans warm: a honey-amber primary, a soft sand background, and deep
 * espresso ink. Cards carry a light top face and a darker bottom edge so they
 * read as raised rather than flat, which is why the surface colours come in
 * three steps rather than two.
 *
 * Every on-colour below was chosen to reach at least WCAG AA (4.5:1) contrast
 * against its container. The numbers are re-checked by ThemeContrastTest on
 * every build, so a palette change that breaks a pair fails the build rather
 * than shipping unreadable text.
 */
object BrandColors {

    /**
     * Honey amber. The main call-to-action colour.
     *
     * Dark enough that white text on it clears WCAG AA at 5.0:1. A brighter
     * amber reads better but lands at 4.09:1, which fails for button labels.
     */
    val Primary = Color(0xFFA85C10)

    /** Lighter face of an amber surface, used for the top of a raised button. */
    val PrimaryTop = Color(0xFFC0721F)

    /** Deeper amber for the bottom edge of a raised surface. */
    val PrimaryBottom = Color(0xFF8A4A08)

    val PrimaryContainerLight = Color(0xFFFBEAD2)
    val PrimaryContainerDark = Color(0xFF4A2A05)

    /**
     * Warm clay used for highlights and the "machine translation" label.
     *
     * Chosen so the colour itself clears the WCAG 3:1 non-text contrast rule on
     * the sand background. For body-sized text use [AccentInk], which clears
     * 4.5:1.
     */
    val Accent = Color(0xFFA8503C)

    /** Darker clay that is safe for body-sized text on light surfaces. */
    val AccentInk = Color(0xFF8A3B29)
    val AccentContainerLight = Color(0xFFFAE3DC)
    val AccentContainerDark = Color(0xFF4A1F16)

    /**
     * A warm sand background rather than the usual near-white.
     *
     * It is deliberately a shade deeper than the card face: a raised card only
     * reads as raised if the page behind it is darker. At 1.21:1 the difference
     * is visible without the page looking dirty, and the same value is pinned by
     * ContrastTest so a later colour tweak cannot flatten the cards back out.
     */
    val BackgroundLight = Color(0xFFF4E4CB)
    val SurfaceLight = Color(0xFFFFFBF5)

    /** The raised face of a card: warmer and slightly deeper than the outline. */
    val SurfaceRaisedLight = Color(0xFFFFF3E0)

    /** The bottom edge of a raised card, one step deeper than its face. */
    val SurfaceEdgeLight = Color(0xFFEDD7B5)

    val InkLight = Color(0xFF3B2A1A)
    val MutedLight = Color(0xFF6E5A47)

    /**
     * Dark mode keeps the warmth instead of going blue-grey: espresso brown
     * surfaces with amber highlights, so the app does not change character
     * between light and dark.
     */
    val BackgroundDark = Color(0xFF1A1310)
    val SurfaceDark = Color(0xFF251B15)
    val SurfaceRaisedDark = Color(0xFF3A2A1D)
    val SurfaceEdgeDark = Color(0xFF120D0A)

    val InkDark = Color(0xFFF2E6D8)
    val MutedDark = Color(0xFFBDA893)

    val ErrorLight = Color(0xFFB3261E)
    val ErrorDark = Color(0xFFF2B8B5)
    val OnErrorLight = Color(0xFFFFFFFF)
    val OnErrorDark = Color(0xFF601410)

    val SuccessLight = Color(0xFF1B6E3C)
    val SuccessDark = Color(0xFF7ED6A1)

    /** Colour of the soft drop shadow under a raised surface. */
    val ShadowLight = Color(0xFF8A6A3F)
    val ShadowDark = Color(0xFF000000)
}
