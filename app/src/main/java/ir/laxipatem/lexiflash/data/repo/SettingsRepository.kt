package ir.laxipatem.lexiflash.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ir.laxipatem.lexiflash.online.CatalogDefaults
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Which colour theme the learner picked. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Which language the interface uses. */
enum class AppLanguage(val tag: String) { PERSIAN("fa"), ENGLISH("en") }

/**
 * Everything the learner can configure, exposed as one immutable snapshot so
 * the UI can render from a single state object.
 */
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val language: AppLanguage = AppLanguage.PERSIAN,
    val persianDigits: Boolean = false,
    val dailyGoal: Int = DEFAULT_DAILY_GOAL,
    val ttsSpeed: Float = 1.0f,
    val unlockAllParts: Boolean = false,
    val catalogUrl: String = CatalogDefaults.DEFAULT_CATALOG_URL,
    val importDisclaimerAccepted: Boolean = false,
    val coreDeckVersion: Int = 0
) {
    companion object {
        const val DEFAULT_DAILY_GOAL = 20
        const val MIN_DAILY_GOAL = 5
        const val MAX_DAILY_GOAL = 200
        const val MIN_TTS_SPEED = 0.5f
        const val MAX_TTS_SPEED = 1.5f
    }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "lexiflash_settings"
)

/**
 * Persists [UserSettings] in a Preferences DataStore.
 *
 * The DataStore is a process-wide singleton per file name, which is why it is
 * held as a property delegate on Context rather than created per instance.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val LANGUAGE = stringPreferencesKey("language")
        val PERSIAN_DIGITS = booleanPreferencesKey("persian_digits")
        val DAILY_GOAL = intPreferencesKey("daily_goal")
        val TTS_SPEED = intPreferencesKey("tts_speed_milli")
        val UNLOCK_ALL = booleanPreferencesKey("unlock_all")
        val CATALOG_URL = stringPreferencesKey("catalog_url")
        val DISCLAIMER = booleanPreferencesKey("import_disclaimer_accepted")
        val CORE_VERSION = intPreferencesKey("core_deck_version")
    }

    private val store: DataStore<Preferences> get() = context.settingsDataStore

    val settings: Flow<UserSettings> = store.data
        .catch { error ->
            // A corrupted preferences file must not crash the app; fall back to
            // defaults and let the next write heal the file.
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            UserSettings(
                themeMode = preferences[Keys.THEME]?.let { raw ->
                    ThemeMode.entries.firstOrNull { it.name == raw }
                } ?: ThemeMode.SYSTEM,
                dynamicColor = preferences[Keys.DYNAMIC_COLOR] ?: false,
                language = preferences[Keys.LANGUAGE]?.let { raw ->
                    AppLanguage.entries.firstOrNull { it.tag == raw }
                } ?: AppLanguage.PERSIAN,
                persianDigits = preferences[Keys.PERSIAN_DIGITS] ?: false,
                dailyGoal = (preferences[Keys.DAILY_GOAL] ?: UserSettings.DEFAULT_DAILY_GOAL)
                    .coerceIn(UserSettings.MIN_DAILY_GOAL, UserSettings.MAX_DAILY_GOAL),
                ttsSpeed = (preferences[Keys.TTS_SPEED] ?: 1000) / 1000f,
                unlockAllParts = preferences[Keys.UNLOCK_ALL] ?: false,
                catalogUrl = preferences[Keys.CATALOG_URL] ?: CatalogDefaults.DEFAULT_CATALOG_URL,
                importDisclaimerAccepted = preferences[Keys.DISCLAIMER] ?: false,
                coreDeckVersion = preferences[Keys.CORE_VERSION] ?: 0
            )
        }

    suspend fun current(): UserSettings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = enabled }

    suspend fun setLanguage(language: AppLanguage) = edit { it[Keys.LANGUAGE] = language.tag }

    suspend fun setPersianDigits(enabled: Boolean) = edit { it[Keys.PERSIAN_DIGITS] = enabled }

    suspend fun setDailyGoal(goal: Int) = edit {
        it[Keys.DAILY_GOAL] = goal.coerceIn(UserSettings.MIN_DAILY_GOAL, UserSettings.MAX_DAILY_GOAL)
    }

    suspend fun setTtsSpeed(speed: Float) = edit {
        val clamped = speed.coerceIn(UserSettings.MIN_TTS_SPEED, UserSettings.MAX_TTS_SPEED)
        it[Keys.TTS_SPEED] = (clamped * 1000).toInt()
    }

    suspend fun setUnlockAll(enabled: Boolean) = edit { it[Keys.UNLOCK_ALL] = enabled }

    suspend fun setCatalogUrl(url: String) = edit { it[Keys.CATALOG_URL] = url.trim() }

    suspend fun acceptImportDisclaimer() = edit { it[Keys.DISCLAIMER] = true }

    suspend fun setCoreDeckVersion(version: Int) = edit { it[Keys.CORE_VERSION] = version }

    /** Removes every stored preference. Used by "delete all data". */
    suspend fun clearAll() {
        store.edit { it.clear() }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        store.edit { preferences -> block(preferences) }
    }
}
