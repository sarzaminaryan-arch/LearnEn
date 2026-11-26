package ir.laxipatem.lexiflash.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.laxipatem.lexiflash.MainActivity
import ir.laxipatem.lexiflash.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises destinations through the real Activity and its navigation graph. */
@RunWith(AndroidJUnit4::class)
class MainNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainDestinations_openWithoutMissingActivityOwners() {
        val resources = composeRule.activity.resources
        composeRule.onNodeWithText(resources.getString(R.string.home_title)).assertIsDisplayed()

        // Home -> Import is the reported crash route: the screen registers three
        // ActivityResult launchers during composition.
        composeRule.onNodeWithText(resources.getString(R.string.action_import)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(resources.getString(R.string.import_title)).assertIsDisplayed()
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()

        // Home -> Catalog exercises the online destination. Its request can fail
        // without affecting this test; the screen itself must still render.
        if (ir.laxipatem.lexiflash.BuildConfig.IS_ONLINE) {
            composeRule.onNodeWithText(resources.getString(R.string.home_online_catalog))
                .performScrollTo()
                .performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText(resources.getString(R.string.catalog_title)).assertIsDisplayed()
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
            composeRule.waitForIdle()
        }

        // Settings -> About also exercises LocalContext-based clipboard/share
        // paths' host screen without launching external activities.
        composeRule.onNodeWithText(resources.getString(R.string.nav_settings)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(resources.getString(R.string.settings_about))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(resources.getString(R.string.about_title)).assertIsDisplayed()
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()

        // The bundled core deck supplies an unlocked first part. Advance its
        // cards through the ordinary study UI, then open Quiz from StudyScreen.
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodes(hasText(resources.getString(R.string.home_part_number, 1)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(resources.getString(R.string.home_part_number, 1))
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodes(
                androidx.compose.ui.test.hasContentDescription(resources.getString(R.string.cd_flip_card))
            ).fetchSemanticsNodes().isNotEmpty()
        }

        val quizButton = resources.getString(R.string.study_start_quiz)
        val flipCardDescription = resources.getString(R.string.cd_flip_card)
        val rateGoodDescription = resources.getString(R.string.cd_rating_good)
        var attempts = 0
        while (composeRule.onAllNodes(hasText(quizButton)).fetchSemanticsNodes().isEmpty() && attempts < 60) {
            composeRule.onNodeWithContentDescription(flipCardDescription).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(rateGoodDescription).performClick()
            composeRule.waitForIdle()
            attempts++
        }
        composeRule.onNodeWithText(quizButton).assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(resources.getString(R.string.quiz_title)).assertIsDisplayed()
    }
}
