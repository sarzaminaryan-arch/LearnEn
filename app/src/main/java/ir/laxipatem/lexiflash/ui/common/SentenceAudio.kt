package ir.laxipatem.lexiflash.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.laxipatem.lexiflash.R
import ir.laxipatem.lexiflash.platform.TtsController
import ir.laxipatem.lexiflash.platform.TtsState

/** Creates and owns a screen-scoped Android speech engine. */
@Composable
fun rememberTtsController(speed: Float): TtsController {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(context) { TtsController(context) }

    DisposableEffect(controller, lifecycleOwner) {
        controller.initialise()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) controller.refreshLanguage()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.shutdown()
        }
    }
    LaunchedEffect(controller, speed) {
        controller.setSpeed(speed)
    }

    return controller
}

/** English sentence with a separate, accessible speak action (no IPA display). */
@Composable
fun CardFrontWithAudio(
    english: String,
    controller: TtsController,
    state: TtsState,
    modifier: Modifier = Modifier
) {
    var installFailed by remember(controller) { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CardFront(english = english)

        IconButton(
            onClick = { controller.speak(english) },
            enabled = state == TtsState.Ready
        ) {
            Icon(
                imageVector = Icons.Filled.VolumeUp,
                contentDescription = stringResource(R.string.cd_speech),
                tint = if (state == TtsState.Ready) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        if (state == TtsState.VoiceMissing || state == TtsState.Failed) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.tts_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                TextButton(
                    onClick = {
                        installFailed = !controller.openInstallVoiceData()
                    }
                ) {
                    Text(stringResource(R.string.tts_install))
                }
                if (installFailed) {
                    Text(
                        text = stringResource(R.string.tts_install_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** Collects speech state with lifecycle awareness for the active screen. */
@Composable
fun rememberTtsState(controller: TtsController): TtsState {
    val state by controller.state.collectAsStateWithLifecycle()
    return state
}
