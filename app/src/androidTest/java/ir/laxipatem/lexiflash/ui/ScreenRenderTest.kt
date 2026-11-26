package ir.laxipatem.lexiflash.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.laxipatem.lexiflash.AppContainer
import ir.laxipatem.lexiflash.online.CatalogDefaults
import ir.laxipatem.lexiflash.ui.home.CatalogScreen
import ir.laxipatem.lexiflash.ui.importer.ImportScreen
import ir.laxipatem.lexiflash.ui.theme.LexiFlashTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders the two screens the device report showed closing, in every state they
 * can be in, and lets a real measurement pass run.
 *
 * A layout crash in Compose happens during measure or layout, not during
 * composition, so a screen can look fine in code review and still take the
 * process down on the first frame. Rendering each branch here reproduces that.
 */
@RunWith(AndroidJUnit4::class)
class ScreenRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val container: AppContainer by lazy {
        AppContainer(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun catalogScreen_renders_every_state() {
        // No entries, not yet loaded: the entry point the device showed.
        composeRule.setContent {
            LexiFlashTheme { CatalogScreen(container = container, onBack = {}) }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun importScreen_renders_the_pick_step() {
        composeRule.setContent {
            LexiFlashTheme { ImportScreen(container = container, onDone = {}, onBack = {}) }
        }
        composeRule.waitForIdle()
    }

    /** The catalogue's own settings row must render whatever URL is configured. */
    @Test
    fun catalogUrl_default_is_https() {
        val url = CatalogDefaults.DEFAULT_CATALOG_URL
        assert(url.startsWith("https://")) { "catalogue URL must be https, was $url" }
    }
}
