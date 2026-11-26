package ir.laxipatem.lexiflash.ui.theme

import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import ir.laxipatem.lexiflash.data.repo.AppLanguage
import ir.laxipatem.lexiflash.data.repo.ThemeMode

/** Light palette built from the indigo, teal and violet brand colours. */
private val LightColors = lightColorScheme(
    primary = BrandColors.Primary,
    onPrimary = Color.White,
    primaryContainer = BrandColors.PrimaryContainerLight,
    onPrimaryContainer = BrandColors.InkLight,
    secondary = BrandColors.Secondary,
    onSecondary = Color.White,
    secondaryContainer = BrandColors.SecondaryContainerLight,
    onSecondaryContainer = BrandColors.InkLight,
    tertiary = BrandColors.AccentInk,
    onTertiary = Color.White,
    tertiaryContainer = BrandColors.AccentContainerLight,
    onTertiaryContainer = BrandColors.InkLight,
    background = BrandColors.BackgroundLight,
    onBackground = BrandColors.InkLight,
    surface = BrandColors.SurfaceLight,
    onSurface = BrandColors.InkLight,
    surfaceVariant = BrandColors.SurfaceRaisedLight,
    onSurfaceVariant = BrandColors.MutedLight,
    surfaceContainer = BrandColors.SurfaceRaisedLight,
    surfaceContainerHigh = BrandColors.SurfaceRaisedLight,
    surfaceContainerHighest = BrandColors.SurfaceEdgeLight,
    surfaceContainerLow = BrandColors.SurfaceLight,
    surfaceContainerLowest = BrandColors.SurfaceRaisedLight,
    outline = BrandColors.CardOutlineLight,
    outlineVariant = BrandColors.SurfaceEdgeLight,
    error = BrandColors.ErrorLight,
    onError = BrandColors.OnErrorLight,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    scrim = Color(0x9933425C)
)

/** Low-glare midnight surfaces with restrained aqua card edging. */
private val DarkColors = darkColorScheme(
    primary = BrandColors.PrimaryDark,
    onPrimary = BrandColors.OnPrimaryDark,
    primaryContainer = BrandColors.PrimaryContainerDark,
    onPrimaryContainer = BrandColors.OnPrimaryContainerDark,
    secondary = BrandColors.SecondaryDark,
    onSecondary = BrandColors.OnSecondaryDark,
    secondaryContainer = BrandColors.SecondaryContainerDark,
    onSecondaryContainer = BrandColors.OnSecondaryContainerDark,
    tertiary = BrandColors.AccentDark,
    onTertiary = BrandColors.OnAccentDark,
    tertiaryContainer = BrandColors.AccentContainerDark,
    onTertiaryContainer = BrandColors.OnAccentContainerDark,
    background = BrandColors.BackgroundDark,
    onBackground = BrandColors.InkDark,
    surface = BrandColors.SurfaceDark,
    onSurface = BrandColors.InkDark,
    surfaceVariant = BrandColors.SurfaceRaisedDark,
    onSurfaceVariant = BrandColors.MutedDark,
    surfaceContainer = BrandColors.SurfaceRaisedDark,
    surfaceContainerHigh = Color(0xFF202F43),
    surfaceContainerHighest = BrandColors.SurfaceEdgeDark,
    surfaceContainerLow = BrandColors.SurfaceDark,
    surfaceContainerLowest = BrandColors.BackgroundDark,
    outline = Color(0xFF657993),
    outlineVariant = Color(0xFF35475D),
    error = BrandColors.ErrorDark,
    onError = BrandColors.OnErrorDark,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xCC000000)
)

/** Resolves the learner's theme preference against the system setting. */
@Composable
private fun shouldUseDarkTheme(themeMode: ThemeMode): Boolean {
    val systemDark = isSystemInDarkTheme()
    return when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

/** Dynamic colour remains available as an explicit opt-in in Settings. */
@Composable
fun LexiFlashTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    language: AppLanguage = AppLanguage.PERSIAN,
    content: @Composable () -> Unit
) {
    val darkTheme = shouldUseDarkTheme(themeMode)
    val context = LocalContext.current

    val colorScheme = remember(darkTheme, dynamicColor) {
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColors
            else -> LightColors
        }
    }

    // Persian reads right-to-left; English reads left-to-right. Overriding the
    // CompositionLocal (rather than recreating the Activity) keeps navigation
    // state intact when the learner flips the language in Settings.
    val layoutDirection = if (language == AppLanguage.PERSIAN) LayoutDirection.Rtl else LayoutDirection.Ltr

    // `stringResource` reads from LocalContext.resources, so the language switch
    // is implemented by handing Compose a context whose configuration carries
    // the chosen locale. Without this the UI would keep following the system
    // language and the in-app switch would change nothing.
    val localizedContext = remember(context, language) {
        val locale = if (language == AppLanguage.PERSIAN) {
            java.util.Locale("fa", "IR")
        } else {
            java.util.Locale.forLanguageTag(language.tag)
        }
        java.util.Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        context.createConfigurationContext(configuration)
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
        LocalContext provides localizedContext
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LexiFlashTypography,
            shapes = LexiFlashShapes,
            content = content
        )
    }
}

/** The success colour used for "correct" feedback, paired with an icon in the UI. */
@Composable
fun successColor(darkTheme: Boolean): Color =
    if (darkTheme) BrandColors.SuccessDark else BrandColors.SuccessLight
