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

/** Light palette built from the brand colours. */
private val LightColors = lightColorScheme(
    primary = BrandColors.Primary,
    onPrimary = Color.White,
    primaryContainer = BrandColors.PrimaryContainerLight,
    onPrimaryContainer = BrandColors.InkLight,
    secondary = BrandColors.MutedLight,
    onSecondary = Color.White,
    secondaryContainer = BrandColors.SurfaceRaisedLight,
    onSecondaryContainer = BrandColors.InkLight,
    // tertiary is used for label-sized text (for example the machine-translation
    // tag), so it takes the darker clay that clears 4.5:1 on the sand background.
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
    surfaceContainerLowest = Color.White,
    outline = BrandColors.SurfaceEdgeLight,
    outlineVariant = BrandColors.SurfaceEdgeLight,
    error = BrandColors.ErrorLight,
    onError = BrandColors.OnErrorLight,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    scrim = Color(0x99301F0F)
)

/** Dark palette. Surfaces are low-chroma so long study sessions stay comfortable. */
private val DarkColors = darkColorScheme(
    primary = Color(0xFFE8A659),
    onPrimary = Color(0xFF3D2102),
    primaryContainer = BrandColors.PrimaryContainerDark,
    onPrimaryContainer = Color(0xFFFBEAD2),
    secondary = BrandColors.MutedDark,
    onSecondary = Color(0xFF1A1310),
    secondaryContainer = BrandColors.SurfaceRaisedDark,
    onSecondaryContainer = Color(0xFFF2E6D8),
    tertiary = Color(0xFFE9A392),
    onTertiary = Color(0xFF4A1F16),
    tertiaryContainer = BrandColors.AccentContainerDark,
    onTertiaryContainer = Color(0xFFFAE3DC),
    background = BrandColors.BackgroundDark,
    onBackground = BrandColors.InkDark,
    surface = BrandColors.SurfaceDark,
    onSurface = BrandColors.InkDark,
    surfaceVariant = BrandColors.SurfaceRaisedDark,
    onSurfaceVariant = BrandColors.MutedDark,
    surfaceContainer = BrandColors.SurfaceRaisedDark,
    surfaceContainerHigh = BrandColors.SurfaceRaisedDark,
    surfaceContainerHighest = Color(0xFF3A2A20),
    surfaceContainerLow = BrandColors.SurfaceDark,
    surfaceContainerLowest = BrandColors.BackgroundDark,
    outline = Color(0xFF5C4736),
    outlineVariant = Color(0xFF3A2A20),
    error = BrandColors.ErrorDark,
    onError = BrandColors.OnErrorDark,
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    scrim = Color(0xCC000000)
)

@Composable
fun LexiFlashTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
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
        // The tag on AppLanguage is the single source of truth for the locale.
        val locale = if (language == AppLanguage.PERSIAN) {
            java.util.Locale("fa", "IR")
        } else {
            java.util.Locale.forLanguageTag(language.tag)
        }
        java.util.Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration).apply {
            // setLocale also derives the layout direction from the locale.
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
