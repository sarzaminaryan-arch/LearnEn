package ir.laxipatem.lexiflash.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.laxipatem.lexiflash.AppContainer
import ir.laxipatem.lexiflash.ui.common.Formatters
import ir.laxipatem.lexiflash.data.repo.AppLanguage
import ir.laxipatem.lexiflash.data.repo.ThemeMode
import ir.laxipatem.lexiflash.data.repo.UserSettings
import ir.laxipatem.lexiflash.online.CatalogDefaults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One-shot messages the settings screen shows as a snackbar. */
enum class SettingsMessage { DATA_DELETED, SAVE_FAILED }

/** Immutable state of the settings screen. */
data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val onlineAvailable: Boolean = false,
    val defaultCatalogUrl: String = CatalogDefaults.DEFAULT_CATALOG_URL,
    val message: SettingsMessage? = null,
    val isBusy: Boolean = false
)

/**
 * Exposes every preference as a setter that writes straight through to
 * DataStore. There is no draft state: a toggle that looks on is on.
 */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> = kotlinx.coroutines.flow.combine(
        _state,
        container.settings.settings
    ) { state, settings ->
        state.copy(
            settings = settings,
            onlineAvailable = container.onlineFeatures.isAvailable,
            defaultCatalogUrl = CatalogDefaults.DEFAULT_CATALOG_URL
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SettingsUiState(onlineAvailable = container.onlineFeatures.isAvailable)
    )

    fun setThemeMode(mode: ThemeMode) = launch { container.settings.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = launch { container.settings.setDynamicColor(enabled) }

    fun setLanguage(language: AppLanguage) = launch { container.settings.setLanguage(language) }

    fun setPersianDigits(enabled: Boolean) = launch { container.settings.setPersianDigits(enabled) }

    fun setDailyGoal(goal: Int) = launch { container.settings.setDailyGoal(goal) }

    fun setTtsSpeed(speed: Float) = launch { container.settings.setTtsSpeed(speed) }

    fun setUnlockAll(enabled: Boolean) = launch { container.settings.setUnlockAll(enabled) }

    /**
     * Stores a new catalogue URL, but only when it is a usable HTTPS address.
     * A rejected value leaves the previous one untouched.
     */
    fun setCatalogUrl(url: String): Boolean {
        // A Persian keyboard produces Persian digits; normalise before validating
        // so a URL typed on an RTL keyboard is not rejected for the wrong reason.
        val trimmed = Formatters.toLatinDigits(url).trim()
        if (!trimmed.startsWith("https://", ignoreCase = true)) return false
        launch { container.settings.setCatalogUrl(trimmed) }
        return true
    }

    fun resetCatalogUrl() = launch {
        container.settings.setCatalogUrl(CatalogDefaults.DEFAULT_CATALOG_URL)
    }

    /** Deletes every deck, review, quiz result and preference. */
    fun deleteAllData() {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            val result = runCatching { container.decks.deleteAll() }
            _state.update {
                it.copy(
                    isBusy = false,
                    message = if (result.isSuccess) SettingsMessage.DATA_DELETED else SettingsMessage.SAVE_FAILED
                )
            }
            // Re-seed the bundled deck so the app is immediately usable again.
            container.decks.ensureCoreDeck()
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() } }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
