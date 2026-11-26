package ir.laxipatem.lexiflash.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Clean, cool brand palette for the study experience.
 *
 * Indigo carries primary actions, teal marks positive progress, and a restrained
 * violet is reserved for secondary information. The dark palette keeps surfaces
 * deep and quiet; [GlowDark] is used only for a fine card edge and its soft halo.
 * Text and semantic states keep their own contrast-safe colours.
 */
object BrandColors {
    /** Indigo action colour; white labels have a 6:1 contrast ratio. */
    val Primary = Color(0xFF365ACB)
    val PrimaryTop = Color(0xFF4B6CD8)
    val PrimaryBottom = Color(0xFF2544A4)
    val PrimaryContainerLight = Color(0xFFE4EAFF)
    val PrimaryContainerDark = Color(0xFF29396D)
    val PrimaryDark = Color(0xFFB8C7FF)
    val OnPrimaryDark = Color(0xFF1B2C64)
    val OnPrimaryContainerDark = Color(0xFFE4EAFF)

    /** Teal communicates steady progress and positive secondary actions. */
    val Secondary = Color(0xFF1F6C64)
    val SecondaryContainerLight = Color(0xFFDDF4EF)
    val SecondaryContainerDark = Color(0xFF193E39)
    val SecondaryDark = Color(0xFF9BD6CF)
    val OnSecondaryDark = Color(0xFF153C37)
    val OnSecondaryContainerDark = Color(0xFFDDF4EF)

    /** Violet is used sparingly for the tertiary/hard-rating state. */
    val Accent = Color(0xFF8651A8)
    val AccentInk = Color(0xFF704386)
    val AccentContainerLight = Color(0xFFF2E8F8)
    val AccentContainerDark = Color(0xFF3E294B)
    val AccentDark = Color(0xFFD9B5EF)
    val OnAccentDark = Color(0xFF3B2350)
    val OnAccentContainerDark = Color(0xFFF0DDFB)

    /** Cool, nearly-white page with a brighter card face for clear depth. */
    val BackgroundLight = Color(0xFFEEF3FA)
    val SurfaceLight = Color(0xFFF8FAFE)
    val SurfaceRaisedLight = Color(0xFFFFFFFF)
    val SurfaceEdgeLight = Color(0xFFD5E0F2)
    val CardOutlineLight = Color(0xFF788AA7)
    val InkLight = Color(0xFF18243A)
    val MutedLight = Color(0xFF53617A)

    /** Midnight blue surfaces stay comfortable during longer study sessions. */
    val BackgroundDark = Color(0xFF0F1624)
    val SurfaceDark = Color(0xFF151E2C)
    val SurfaceRaisedDark = Color(0xFF1B293B)
    val SurfaceEdgeDark = Color(0xFF26374C)
    val InkDark = Color(0xFFEAF0FC)
    val MutedDark = Color(0xFFB0BED2)

    /** Fine aqua outline and low-opacity shadow tint for dark-mode cards. */
    val GlowDark = Color(0xFF68F6D5)

    val ErrorLight = Color(0xFFB3261E)
    val ErrorDark = Color(0xFFFFB4AB)
    val OnErrorLight = Color(0xFFFFFFFF)
    val OnErrorDark = Color(0xFF690005)

    val SuccessLight = Color(0xFF1B6E3C)
    val SuccessDark = Color(0xFF7ED6A1)

    /** Soft, neutral lift for light surfaces; dark cards use [GlowDark]. */
    val ShadowLight = Color(0xFF647A9D)
    val ShadowDark = Color(0xFF000000)
}
