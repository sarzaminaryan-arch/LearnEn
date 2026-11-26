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
 * Renders the two screens the device report showed closing.
 *
 * This is not a belt-and-braces rendering check. A Compose layout crash happens
 * during measure or layout, not during composition, so a screen can pass code
 * review and still take the process down on its first frame. Opening each screen
 * here is the cheapest way to reproduce that inside CI, where the trace can be
 * read.
 *
 * `createComposeRule` hosts the content in a real Activity, which matters for
 * [ImportScreen]: it registers activity-result launchers for the document
 * pickers, and those need a real ActivityResultRegistryOwner in the composition.
 * A rule without an Activity would fail for a reason unrelated to the app.
 */
@RunWith(AndroidJUnit4::class)
class ScreenRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val container: AppContainer by lazy {
        AppContainer(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    /**
     * The catalogue as it appears on entry: loading, then either a list or an
     * error. All three branches measure, so all three are worth opening.
     */
    @Test
    fun catalogScreen_renders() {
        composeRule.setContent {
            LexiFlashTheme { CatalogScreen(container = container, onBack = {}) }
        }
        composeRule.waitForIdle()
    }

    /**
     * The import wizard as it appears on entry, with the disclaimer card, the
     * pick button and the two bilingual pickers.
     */
    @Test
    fun importScreen_renders_the_pick_step() {
        composeRule.setContent {
            LexiFlashTheme { ImportScreen(container = container, onDone = {}, onBack = {}) }
        }
        composeRule.waitForIdle()
    }

    /**
     * The theme hands Compose a context built with `createConfigurationContext`
     * so the in-app language switch works. RTL is a different layout pass, and
     * the import wizard is the tallest screen in the app, so this pairing is the
     * one most likely to expose a constraint problem.
     */
    @Test
    fun importScreen_renders_right_to_left() {
        composeRule.setContent {
            LexiFlashTheme(language = ir.laxipatem.lexiflash.data.repo.AppLanguage.PERSIAN) {
                ImportScreen(container = container, onDone = {}, onBack = {})
            }
        }
        composeRule.waitForIdle()
    }

    /** The shipped catalogue URL has to be one the fetcher will accept. */
    @Test
    fun catalogUrl_default_is_https() {
        val url = CatalogDefaults.DEFAULT_CATALOG_URL
        assert(url.startsWith("https://")) { "catalogue URL must be https, was $url" }
    }
}
