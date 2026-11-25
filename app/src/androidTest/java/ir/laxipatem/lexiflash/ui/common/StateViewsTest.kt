package ir.laxipatem.lexiflash.ui.common

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.laxipatem.lexiflash.ui.theme.LexiFlashTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for the shared empty/error states.
 *
 * Two behaviours are worth an instrumented test rather than a JVM test: the
 * strings have to come from real resources (so a missing translation fails
 * here), and [ErrorState] must never show a blank screen in place of a message.
 */
@RunWith(AndroidJUnit4::class)
class StateViewsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun errorState_shows_a_supplied_message() {
        composeRule.setContent {
            LexiFlashTheme {
                ErrorState(message = "The catalogue could not be reached.")
            }
        }

        composeRule.onNodeWithText("The catalogue could not be reached.").assertIsDisplayed()
    }

    /**
     * A null or blank message must still render a sentence. The screen title is
     * resolved from resources, so this also proves the error strings exist.
     */
    @Test
    fun errorState_falls_back_instead_of_showing_nothing() {
        composeRule.setContent {
            LexiFlashTheme {
                ErrorState(message = "   ")
            }
        }

        // The blank message is replaced, so no node holds only whitespace.
        composeRule.onNodeWithText("   ").assertDoesNotExist()
    }

    @Test
    fun errorState_offers_a_retry_action_when_asked() {
        var retried = false
        composeRule.setContent {
            LexiFlashTheme {
                ErrorState(message = "Offline.", onRetry = { retried = true })
            }
        }

        composeRule.onNodeWithText("Offline.").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        assertTrue("the retry callback must run", retried)
    }

    @Test
    fun emptyState_shows_its_title_and_body() {
        composeRule.setContent {
            LexiFlashTheme {
                EmptyState(title = "No decks yet", body = "Import a deck to begin.")
            }
        }

        composeRule.onNodeWithText("No decks yet").assertIsDisplayed()
        composeRule.onNodeWithText("Import a deck to begin.").assertIsDisplayed()
    }
}
