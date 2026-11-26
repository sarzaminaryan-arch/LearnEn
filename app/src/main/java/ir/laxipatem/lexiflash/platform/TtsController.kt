package ir.laxipatem.lexiflash.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lifecycle of the speech engine, surfaced to the UI. */
sealed interface TtsState {
    data object Initialising : TtsState
    data object Ready : TtsState
    /** No English voice data on this device; the UI offers an install action. */
    data object VoiceMissing : TtsState
    data object Failed : TtsState
}

/**
 * Thin wrapper over the platform [TextToSpeech] engine.
 *
 * Uses only `android.speech.tts`, with no third-party dependency. The engine is
 * initialized for the active study/review screen and always shut down in
 * [shutdown] when that screen leaves composition.
 */
class TtsController(private val context: Context) {

    private val _state = MutableStateFlow<TtsState>(TtsState.Initialising)
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private var engine: TextToSpeech? = null
    private var pendingInit = false
    private var speed: Float = 1.0f
    private var persona: Locale = Locale.US
    private var utteranceCounter = 0L

    /** Creates the engine. Safe to call once per controller. */
    fun initialise() {
        if (engine != null || pendingInit) return
        pendingInit = true
        engine = TextToSpeech(context.applicationContext) { status ->
            pendingInit = false
            if (status != TextToSpeech.SUCCESS) {
                _state.value = TtsState.Failed
                return@TextToSpeech
            }
            configureLanguage()
        }
    }

    /** Rechecks voice availability when the app resumes after installing speech data. */
    fun refreshLanguage() {
        if (!pendingInit) configureLanguage()
    }

    private fun configureLanguage() {
        val tts = engine ?: return
        val result = tts.setLanguage(persona)
        _state.value = when {
            result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED -> TtsState.VoiceMissing
            result < 0 -> TtsState.Failed
            else -> {
                tts.setSpeechRate(speed)
                TtsState.Ready
            }
        }
    }

    /** Sets the speaking rate; values are clamped to the supported 0.5x..1.5x. */
    fun setSpeed(newSpeed: Float) {
        speed = newSpeed.coerceIn(MIN_SPEED, MAX_SPEED)
        engine?.setSpeechRate(speed)
    }

    /**
     * Speaks [text] in English.
     *
     * @return false when the engine is not ready or the text is blank, so the UI
     *   can show a hint instead of failing silently.
     */
    fun speak(text: String): Boolean {
        val tts = engine ?: return false
        if (text.isBlank()) return false
        if (_state.value != TtsState.Ready) return false
        val id = "lexiflash-${utteranceCounter++}"
        val mode = TextToSpeech.QUEUE_FLUSH
        return tts.speak(text, mode, null, id) == TextToSpeech.SUCCESS
    }

    fun stop() {
        engine?.stop()
    }

    /**
     * Opens the system installer for speech data.
     *
     * @return false when no activity can handle the intent, which happens on
     *   stripped-down ROMs; the caller then shows an explanatory message.
     */
    fun openInstallVoiceData(): Boolean {
        val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    /** Releases the engine. Idempotent. */
    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        pendingInit = false
        _state.value = TtsState.Failed
    }

    companion object {
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 1.5f
    }
}
