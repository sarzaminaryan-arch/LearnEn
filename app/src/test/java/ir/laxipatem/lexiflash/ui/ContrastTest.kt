package ir.laxipatem.lexiflash.ui

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG contrast checks for the fixed brand palette. */
class ContrastTest {

    private object Palette {
        const val PRIMARY = 0x365ACB
        const val PRIMARY_TOP = 0x4B6CD8
        const val PRIMARY_BOTTOM = 0x2544A4
        const val PRIMARY_CONTAINER_LIGHT = 0xE4EAFF
        const val PRIMARY_CONTAINER_DARK = 0x29396D
        const val PRIMARY_DARK = 0xB8C7FF
        const val ON_PRIMARY_DARK = 0x1B2C64
        const val SECONDARY = 0x1F6C64
        const val SECONDARY_DARK = 0x9BD6CF
        const val ON_SECONDARY_DARK = 0x153C37
        const val SECONDARY_CONTAINER_LIGHT = 0xDDF4EF
        const val SECONDARY_CONTAINER_DARK = 0x193E39
        const val ACCENT = 0x8651A8
        const val ACCENT_DARK = 0xD9B5EF
        const val ON_ACCENT_DARK = 0x3B2350
        const val ACCENT_INK = 0x704386
        const val ACCENT_CONTAINER_LIGHT = 0xF2E8F8
        const val ACCENT_CONTAINER_DARK = 0x3E294B
        const val BACKGROUND_LIGHT = 0xEEF3FA
        const val SURFACE_LIGHT = 0xF8FAFE
        const val SURFACE_RAISED_LIGHT = 0xFFFFFF
        const val CARD_OUTLINE_LIGHT = 0x788AA7
        const val INK_LIGHT = 0x18243A
        const val MUTED_LIGHT = 0x53617A
        const val BACKGROUND_DARK = 0x0F1624
        const val SURFACE_DARK = 0x151E2C
        const val SURFACE_RAISED_DARK = 0x1B293B
        const val INK_DARK = 0xEAF0FC
        const val MUTED_DARK = 0xB0BED2
        const val GLOW_DARK = 0x68F6D5
        const val ERROR_LIGHT = 0xB3261E
        const val ERROR_DARK = 0xFFB4AB
        const val ON_ERROR_LIGHT = 0xFFFFFF
        const val ON_ERROR_DARK = 0x690005
        const val SUCCESS_LIGHT = 0x1B6E3C
        const val SUCCESS_DARK = 0x7ED6A1
    }

    private fun channel(value: Int): Double {
        val normalized = value / 255.0
        return if (normalized <= 0.03928) {
            normalized / 12.92
        } else {
            ((normalized + 0.055) / 1.055).pow(2.4)
        }
    }

    private fun luminance(rgb: Int): Double {
        val red = (rgb shr 16) and 0xFF
        val green = (rgb shr 8) and 0xFF
        val blue = rgb and 0xFF
        return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
    }

    /** WCAG contrast ratio, always ≥ 1. */
    private fun contrast(first: Int, second: Int): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun assertAA(label: String, first: Int, second: Int, minimum: Double = 4.5) {
        val ratio = contrast(first, second)
        assertTrue(
            "$label has contrast %.2f, needs %.1f".format(ratio, minimum),
            ratio >= minimum
        )
    }

    /** Composite an opaque RGB foreground over an opaque RGB background. */
    private fun composite(foreground: Int, background: Int, alpha: Double): Int {
        fun channel(shift: Int): Int {
            val front = (foreground shr shift) and 0xFF
            val back = (background shr shift) and 0xFF
            return (front * alpha + back * (1.0 - alpha)).toInt()
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    @Test
    fun `body text on the light background passes AA`() {
        assertAA("ink on background", Palette.INK_LIGHT, Palette.BACKGROUND_LIGHT)
        assertAA("ink on surface", Palette.INK_LIGHT, Palette.SURFACE_LIGHT)
        assertAA("ink on raised card", Palette.INK_LIGHT, Palette.SURFACE_RAISED_LIGHT)
    }

    @Test
    fun `secondary text on the light background passes AA`() {
        assertAA("muted on background", Palette.MUTED_LIGHT, Palette.BACKGROUND_LIGHT)
        assertAA("muted on surface", Palette.MUTED_LIGHT, Palette.SURFACE_LIGHT)
    }

    @Test
    fun `body text on the dark background passes AA`() {
        assertAA("ink on background", Palette.INK_DARK, Palette.BACKGROUND_DARK)
        assertAA("ink on surface", Palette.INK_DARK, Palette.SURFACE_DARK)
    }

    @Test
    fun `secondary text on the dark background passes AA`() {
        assertAA("muted on background", Palette.MUTED_DARK, Palette.BACKGROUND_DARK)
        assertAA("muted on surface", Palette.MUTED_DARK, Palette.SURFACE_DARK)
    }

    @Test
    fun `raised action faces carry white text`() {
        assertAA("white on primary", 0xFFFFFF, Palette.PRIMARY)
        assertAA("white on top face", 0xFFFFFF, Palette.PRIMARY_TOP)
        assertAA("white on bottom edge", 0xFFFFFF, Palette.PRIMARY_BOTTOM)
        assertAA("white on secondary", 0xFFFFFF, Palette.SECONDARY)
        assertAA("white on tertiary", 0xFFFFFF, Palette.ACCENT_INK)
    }

    @Test
    fun `dark action colours have readable labels`() {
        assertAA("on dark primary", Palette.ON_PRIMARY_DARK, Palette.PRIMARY_DARK)
        assertAA("on dark secondary", Palette.ON_SECONDARY_DARK, Palette.SECONDARY_DARK)
        assertAA("on dark tertiary", Palette.ON_ACCENT_DARK, Palette.ACCENT_DARK)
    }

    @Test
    fun `text on the primary containers passes AA`() {
        assertAA("ink on light primary container", Palette.INK_LIGHT, Palette.PRIMARY_CONTAINER_LIGHT)
        assertAA("light ink on dark primary container", Palette.INK_DARK, Palette.PRIMARY_CONTAINER_DARK)
    }

    @Test
    fun `text on secondary and tertiary containers passes AA`() {
        assertAA("ink on light secondary container", Palette.INK_LIGHT, Palette.SECONDARY_CONTAINER_LIGHT)
        assertAA("ink on light tertiary container", Palette.INK_LIGHT, Palette.ACCENT_CONTAINER_LIGHT)
        assertAA("light ink on dark secondary container", Palette.INK_DARK, Palette.SECONDARY_CONTAINER_DARK)
        assertAA("light ink on dark tertiary container", Palette.INK_DARK, Palette.ACCENT_CONTAINER_DARK)
    }

    @Test
    fun `error colours pass AA`() {
        assertAA("on-error on error (light)", Palette.ON_ERROR_LIGHT, Palette.ERROR_LIGHT)
        assertAA("on-error on error (dark)", Palette.ON_ERROR_DARK, Palette.ERROR_DARK)
        assertAA("error text on light surface", Palette.ERROR_LIGHT, Palette.SURFACE_RAISED_LIGHT)
    }

    @Test
    fun `success colours pass AA`() {
        assertAA("success on light surface", Palette.SUCCESS_LIGHT, Palette.SURFACE_RAISED_LIGHT)
        assertAA("success on dark surface", Palette.SUCCESS_DARK, Palette.SURFACE_DARK)
    }

    @Test
    fun `raised card faces separate from the page`() {
        assertAA(
            "raised face on the light page",
            Palette.SURFACE_RAISED_LIGHT,
            Palette.BACKGROUND_LIGHT,
            1.10
        )
        assertAA(
            "raised face on the dark page",
            Palette.SURFACE_RAISED_DARK,
            Palette.BACKGROUND_DARK,
            1.20
        )
    }

    @Test
    fun `card borders remain visible in both themes`() {
        assertAA("light card border on card", Palette.CARD_OUTLINE_LIGHT, Palette.SURFACE_RAISED_LIGHT, 3.0)
        assertAA("light card border on page", Palette.CARD_OUTLINE_LIGHT, Palette.BACKGROUND_LIGHT, 3.0)
        val translucentGlow = composite(
            foreground = Palette.GLOW_DARK,
            background = Palette.SURFACE_RAISED_DARK,
            alpha = 0.72
        )
        assertAA("aqua dark card edge", translucentGlow, Palette.SURFACE_RAISED_DARK, 3.0)
    }

    @Test
    fun `large text and icons only need three to one`() {
        assertAA("primary on light surface", Palette.PRIMARY, Palette.SURFACE_RAISED_LIGHT, 3.0)
        assertAA("accent on light surface", Palette.ACCENT, Palette.SURFACE_RAISED_LIGHT, 3.0)
    }

    @Test
    fun `accent coloured text passes AA`() {
        assertAA("accent ink on light surface", Palette.ACCENT_INK, Palette.SURFACE_RAISED_LIGHT)
        assertAA("accent ink on light background", Palette.ACCENT_INK, Palette.BACKGROUND_LIGHT)
    }

    @Test
    fun `the contrast formula behaves like the specification`() {
        assertTrue("black on white must be 21:1", kotlin.math.abs(contrast(0x000000, 0xFFFFFF) - 21.0) < 0.01)
        assertTrue("white on white must be 1:1", kotlin.math.abs(contrast(0xFFFFFF, 0xFFFFFF) - 1.0) < 0.001)
    }
}
