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
    secondaryContainer = BrandColors.PrimaryContainerLight,
    onSecondaryContainer = BrandColors.InkLight,
    // tertiary is used for label-sized text (for example the machine-translation
    // tag), so it takes the darker amber that clears 4.5:1 on white.
    tertiary = BrandColors.AccentInk,
    onTertiary = Color.White,
    tertiaryContainer = BrandColors.AccentContainerLight,
    onTertiaryContainer = BrandColors.InkLight,
    background = BrandColors.BackgroundLight,
    onBackground = BrandColors.InkLight,
    surface = BrandColors.SurfaceLight,
    onSurface = BrandColors.InkLight,
    surfaceVariant = BrandColors.PrimaryContainerLight,
    onSurfaceVariant = BrandColors.MutedLight,
    outline = BrandColors.MutedLight,
    outlineVariant = Color(0xFFD3D8DF),
    error = BrandColors.ErrorLight,
    onError = BrandColors.OnErrorLight,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

/** Dark palette. Surfaces are low-chroma so long study sessions stay comfortable. */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CBBEC),
    onPrimary = Color(0xFF0B2545),
    primaryContainer = BrandColors.PrimaryContainerDark,
    onPrimaryContainer = Color(0xFFDCE7F7),
    secondary = BrandColors.MutedDark,
    onSecondary = Color(0xFF101419),
    secondaryContainer = Color(0xFF243040),
    onSecondaryContainer = Color(0xFFE7EAEF),
    tertiary = Color(0xFFF0C25C),
    onTertiary = Color(0xFF3E2F03),
    tertiaryContainer = BrandColors.AccentContainerDark,
    onTertiaryContainer = Color(0xFFFBEBC4),
    background = BrandColors.BackgroundDark,
    onBackground = BrandColors.InkDark,
    surface = BrandColors.SurfaceDark,
    onSurface = BrandColors.InkDark,
    surfaceVariant = Color(0xFF243040),
    onSurfaceVariant = BrandColors.MutedDark,
    outline = Color(0xFF7D8794),
    outlineVariant = Color(0xFF343D49),
    error = BrandColors.ErrorDark,
    onError = BrandColors.OnErrorDark,
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

/** Resolves the effective dark/light choice from the stored preference. */
@Composable
fun shouldUseDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Root theme.
 *
 * @param themeMode the learner's light/dark preference.
 * @param dynamicColor when true and the device runs Android 12+, the wallpaper
 *   palette replaces the brand palette. The learner can switch this off.
 * @param language drives the layout direction of the whole app.
 */
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
