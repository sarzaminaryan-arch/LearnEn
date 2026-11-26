package ir.laxipatem.lexiflash.ui

import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.laxipatem.lexiflash.BuildConfig
import ir.laxipatem.lexiflash.MainActivity
import ir.laxipatem.lexiflash.R
import java.util.Locale
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
        val faConfiguration = Configuration(composeRule.activity.resources.configuration).apply {
            setLocale(Locale("fa", "IR"))
        }
        val resources = composeRule.activity.createConfigurationContext(faConfiguration).resources
        composeRule.onNodeWithText(resources.getString(R.string.home_title)).assertIsDisplayed()

        // Wait for the bundled sample deck so navigation is tested from the
        // populated Home state rather than racing the initial empty-state CTA.
        val partOneDescription = "${resources.getString(R.string.home_part_number, 1)}. " +
            resources.getString(R.string.home_part_open_desc)
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodes(hasContentDescription(partOneDescription))
                .fetchSemanticsNodes().isNotEmpty()
        }

        // This is the reported crash route: composition registers three
        // rememberLauncherForActivityResult launchers on a real MainActivity.
        composeRule.onNodeWithText(resources.getString(R.string.action_import)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(resources.getString(R.string.import_title)).assertIsDisplayed()
        pressBack()

        // Catalog is only exposed by the online flavor.
        if (BuildConfig.IS_ONLINE) {
            composeRule.onNodeWithText(resources.getString(R.string.home_online_catalog))
                .performScrollTo()
                .performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText(resources.getString(R.string.catalog_title)).assertIsDisplayed()
            pressBack()
        }

        composeRule.onNodeWithText(resources.getString(R.string.nav_settings)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(resources.getString(R.string.settings_title)).assertIsDisplayed()
        composeRule.onNodeWithText(resources.getString(R.string.settings_about))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(resources.getString(R.string.about_title)).assertIsDisplayed()
        // About's clipboard and ACTION_SEND paths are LocalContext-based. This
        // test validates the About host screen without launching external apps.
        pressBack()
        pressBack()

        composeRule.onNodeWithContentDescription(partOneDescription)
            .performScrollTo()
            .performClick()
        val flipCardDescription = resources.getString(R.string.cd_flip_card)
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodes(hasContentDescription(flipCardDescription))
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Complete the small bundled first part through the real study UI, then
        // open Quiz using the Study screen's own action.
        val quizButton = resources.getString(R.string.study_start_quiz)
        val rateGoodLabel = resources.getString(R.string.rating_good)
        var attempts = 0
        while (composeRule.onAllNodes(hasText(quizButton)).fetchSemanticsNodes().isEmpty() && attempts < 60) {
            composeRule.onNodeWithContentDescription(flipCardDescription).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText(rateGoodLabel).performClick()
            composeRule.waitForIdle()
            attempts++
        }
        composeRule.onNodeWithText(quizButton).assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(resources.getString(R.string.quiz_title)).assertIsDisplayed()
    }

    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }
}
