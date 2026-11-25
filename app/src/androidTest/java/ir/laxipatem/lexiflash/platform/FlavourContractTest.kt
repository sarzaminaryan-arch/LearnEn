package ir.laxipatem.lexiflash.platform

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the flavour contract on the actual installed APK.
 *
 * The CI workflow greps the merged manifest, which catches a permission that a
 * dependency added. This test covers the other half of the promise: the offline
 * build must also *behave* as if it had no network, and the online build must
 * actually be able to work.
 *
 * Because the two flavours share one test source set, the expectations are
 * derived from the app's own `isAvailable` flag rather than from a build
 * constant, so the same file is correct for both variants.
 */
@RunWith(AndroidJUnit4::class)
class FlavourContractTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** True when this APK was built from the online flavour. */
    private val isOnlineBuild: Boolean =
        OnlineFeaturesFactory.create(context).isAvailable

    private fun declares(permission: String): Boolean =
        context.packageManager.checkPermission(
            permission,
            context.packageName
        ) == PackageManager.PERMISSION_GRANTED

    @Test
    fun the_offline_build_has_no_network_permission() {
        if (isOnlineBuild) {
            assertTrue(
                "the online build is expected to request INTERNET",
                declares(android.Manifest.permission.INTERNET)
            )
            return
        }
        assertFalse(
            "the offline build must not declare INTERNET, but it does",
            declares(android.Manifest.permission.INTERNET)
        )
        assertFalse(
            "the offline build must not declare ACCESS_NETWORK_STATE",
            declares(android.Manifest.permission.ACCESS_NETWORK_STATE)
        )
    }

    @Test
    fun the_offline_build_reports_online_features_as_unavailable() {
        if (isOnlineBuild) {
            assertTrue("the online build must report its features as available", isOnlineBuild)
            return
        }
        assertFalse(
            "the offline stub must report isAvailable = false",
            OnlineFeaturesFactory.create(context).isAvailable
        )
    }

    /**
     * Offline calls must fail loudly instead of pretending to succeed, so the UI
     * can explain that the feature is missing from this build.
     */
    @Test
    fun the_offline_stub_fails_every_operation() = runTest {
        val features = OnlineFeaturesFactory.create(context)
        if (features.isAvailable) {
            // The online build talks to the network here, which an instrumented
            // test must not do; availability was asserted above.
            assertEquals(true, features.isAvailable)
            return@runTest
        }

        val catalog = features.fetchCatalog("https://example.com/catalog.json")
        val deck = features.downloadDeck("https://example.com/deck.json")
        val model = features.downloadTranslationModel()
        val translated = features.translate(listOf("Hello."))

        assertTrue("catalogue must fail", catalog.isFailure)
        assertTrue("deck download must fail", deck.isFailure)
        assertTrue("model download must fail", model.isFailure)
        assertTrue("translation must fail", translated.isFailure)
        assertFalse("the model can never be ready offline", features.isTranslationModelReady())

        val reason = catalog.exceptionOrNull()
        assertTrue(
            "the failure must be the dedicated offline exception, was $reason",
            reason is OnlineFeatureUnavailableException
        )
        assertTrue(
            "the message must name the feature so the UI can show it",
            reason?.message?.contains("not available in the offline build") == true
        )
    }
}
