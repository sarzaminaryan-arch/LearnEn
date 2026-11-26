package ir.laxipatem.lexiflash.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Accessible brand palette for the study experience.
 *
 * Light mode uses indigo, teal and violet. Dark mode switches to near-black
 * green-tinted surfaces, luminous green progress, light-red errors and amber
 * emphasis. [GlowDark] is reserved for a fine card edge and its soft halo; text
 * and semantic states keep their own contrast-safe colours.
 */
object BrandColors {
    /** Indigo action colour; white labels have a 6:1 contrast ratio. */
    val Primary = Color(0xFF365ACB)
    val PrimaryTop = Color(0xFF4B6CD8)
    val PrimaryBottom = Color(0xFF2544A4)
    val PrimaryContainerLight = Color(0xFFE4EAFF)
    val PrimaryContainerDark = Color(0xFF17432B)
    val PrimaryDark = Color(0xFF79E9A6)
    val OnPrimaryDark = Color(0xFF0A321B)
    val OnPrimaryContainerDark = Color(0xFFD2F8DE)

    /** Teal communicates steady progress and positive secondary actions. */
    val Secondary = Color(0xFF1F6C64)
    val SecondaryContainerLight = Color(0xFFDDF4EF)
    val SecondaryContainerDark = Color(0xFF193B2A)
    val SecondaryDark = Color(0xFF89DDB8)
    val OnSecondaryDark = Color(0xFF0C3825)
    val OnSecondaryContainerDark = Color(0xFFD3F5E0)

    /** Amber is reserved for the tertiary/hard-rating state. */
    val Accent = Color(0xFF8651A8)
    val AccentInk = Color(0xFF704386)
    val AccentContainerLight = Color(0xFFF2E8F8)
    val AccentContainerDark = Color(0xFF49370A)
    val AccentDark = Color(0xFFFFD166)
    val OnAccentDark = Color(0xFF3D2C00)
    val OnAccentContainerDark = Color(0xFFFFE9B5)

    /** Cool, nearly-white page with a brighter card face for clear depth. */
    val BackgroundLight = Color(0xFFEEF3FA)
    val SurfaceLight = Color(0xFFF8FAFE)
    val SurfaceRaisedLight = Color(0xFFFFFFFF)
    val SurfaceEdgeLight = Color(0xFFD5E0F2)
    val CardOutlineLight = Color(0xFF788AA7)
    val InkLight = Color(0xFF18243A)
    val MutedLight = Color(0xFF53617A)

    /** Near-black green-tinted surfaces for a deeper, lower-glare dark theme. */
    val BackgroundDark = Color(0xFF050907)
    val SurfaceDark = Color(0xFF0B120E)
    val SurfaceRaisedDark = Color(0xFF152119)
    val SurfaceEdgeDark = Color(0xFF26382B)
    val InkDark = Color(0xFFEAF5ED)
    val MutedDark = Color(0xFFB5C7BB)

    /** Fine luminous-green outline and low-opacity shadow tint for dark cards. */
    val GlowDark = Color(0xFF7AEEA6)

    val ErrorLight = Color(0xFFB3261E)
    val ErrorDark = Color(0xFFFF8178)
    val OnErrorLight = Color(0xFFFFFFFF)
    val OnErrorDark = Color(0xFF550006)
    val ErrorContainerDark = Color(0xFF5D1212)
    val OnErrorContainerDark = Color(0xFFFFDAD6)

    val SuccessLight = Color(0xFF1B6E3C)
    val SuccessDark = Color(0xFF73E9A2)

    /** Soft, neutral lift for light surfaces; dark cards use [GlowDark]. */
    val ShadowLight = Color(0xFF647A9D)
    val ShadowDark = Color(0xFF000000)
}
