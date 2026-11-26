package ir.laxipatem.lexiflash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.laxipatem.lexiflash.data.repo.ThemeMode
import ir.laxipatem.lexiflash.ui.nav.AppNavHost
import ir.laxipatem.lexiflash.ui.theme.LexiFlashTheme

/**
 * The only Activity in the app.
 *
 * Language handling note: instead of the AppCompat per-app-language API (which
 * would add the appcompat dependency for one feature), the locale is applied in
 * Compose by overriding `LocalLayoutDirection` and using resources from a
 * configuration-aware context. The full rationale is in docs/DECISIONS.md.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // Locale-aware contexts provided deeper in the theme can obscure
            // Activity-derived owners from Compose. Publish them explicitly at
            // the root so activity-result launchers and back handling always
            // resolve against this Activity.
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides this@MainActivity,
                LocalOnBackPressedDispatcherOwner provides this@MainActivity
            ) {
                val container = appContainer
                val settings by container.settings.settings.collectAsStateWithLifecycle(
                    initialValue = ir.laxipatem.lexiflash.data.repo.UserSettings()
                )
                val followsSystem = isSystemInDarkTheme()
                val appIsDark = when (settings.themeMode) {
                    ThemeMode.SYSTEM -> followsSystem
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
                SideEffect {
                    // Edge-to-edge system icons should follow the app's override,
                    // not only the device theme, so they remain visible on a
                    // manually selected dark or light palette.
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !appIsDark
                        isAppearanceLightNavigationBars = !appIsDark
                    }
                }

                LexiFlashTheme(
                    themeMode = settings.themeMode,
                    dynamicColor = settings.dynamicColor,
                    language = settings.language
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppNavHost(container = container, settings = settings)
                    }
                }
            }
        }
    }
}
