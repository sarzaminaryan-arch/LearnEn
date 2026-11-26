package ir.laxipatem.lexiflash.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Accessible brand palette for the study experience.
 *
 * Light mode uses indigo, amber and violet. Dark mode switches to neutral
 * near-black surfaces, luminous amber emphasis and light-red errors. [GlowDark]
 * is reserved for a fine card edge and its soft halo; text and semantic states
 * keep their own contrast-safe colours.
 */
object BrandColors {
    /** Indigo action colour; white labels have a 6:1 contrast ratio. */
    val Primary = Color(0xFF365ACB)
    val PrimaryTop = Color(0xFF4B6CD8)
    val PrimaryBottom = Color(0xFF2544A4)
    val PrimaryContainerLight = Color(0xFFE4EAFF)
    val PrimaryContainerDark = Color(0xFF49370A)
    val PrimaryDark = Color(0xFFFFD166)
    val OnPrimaryDark = Color(0xFF3D2C00)
    val OnPrimaryContainerDark = Color(0xFFFFE9B5)

    /** Amber communicates steady progress and positive secondary actions. */
    val Secondary = Color(0xFF7A4B00)
    val SecondaryContainerLight = Color(0xFFFFF1C2)
    val SecondaryContainerDark = Color(0xFF3D3418)
    val SecondaryDark = Color(0xFFF2D07B)
    val OnSecondaryDark = Color(0xFF332700)
    val OnSecondaryContainerDark = Color(0xFFFFF0C2)

    /** Violet in light mode and amber in dark mode for tertiary emphasis. */
    val Accent = Color(0xFF8651A8)
    val AccentInk = Color(0xFF704386)
    val AccentContainerLight = Color(0xFFF2E8F8)
    val AccentContainerDark = Color(0xFF553A00)
    val AccentDark = Color(0xFFFFB703)
    val OnAccentDark = Color(0xFF3A2800)
    val OnAccentContainerDark = Color(0xFFFFEAB5)

    /** Cool, nearly-white page with a brighter card face for clear depth. */
    val BackgroundLight = Color(0xFFEEF3FA)
    val SurfaceLight = Color(0xFFF8FAFE)
    val SurfaceRaisedLight = Color(0xFFFFFFFF)
    val SurfaceEdgeLight = Color(0xFFD5E0F2)
    val CardOutlineLight = Color(0xFF788AA7)
    val InkLight = Color(0xFF18243A)
    val MutedLight = Color(0xFF53617A)

    /** Near-black neutral surfaces for a deeper, lower-glare dark theme. */
    val BackgroundDark = Color(0xFF050607)
    val SurfaceDark = Color(0xFF0C0E11)
    val SurfaceRaisedDark = Color(0xFF1B2026)
    val SurfaceEdgeDark = Color(0xFF2A2F35)
    val InkDark = Color(0xFFF0F2F5)
    val MutedDark = Color(0xFFBCC3CC)

    /** Fine luminous-amber outline and low-opacity shadow tint for dark cards. */
    val GlowDark = Color(0xFFFFD166)

    val ErrorLight = Color(0xFFB3261E)
    val ErrorDark = Color(0xFFFF8178)
    val OnErrorLight = Color(0xFFFFFFFF)
    val OnErrorDark = Color(0xFF550006)
    val ErrorContainerDark = Color(0xFF5D1212)
    val OnErrorContainerDark = Color(0xFFFFDAD6)

    val SuccessLight = Color(0xFF7A4B00)
    val SuccessDark = Color(0xFFFFD166)

    /** Soft, neutral lift for light surfaces; dark cards use [GlowDark]. */
    val ShadowLight = Color(0xFF647A9D)
    val ShadowDark = Color(0xFF000000)
}
