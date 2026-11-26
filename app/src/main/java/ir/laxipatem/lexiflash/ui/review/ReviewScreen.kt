package ir.laxipatem.lexiflash.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.laxipatem.lexiflash.AppContainer
import ir.laxipatem.lexiflash.R
import ir.laxipatem.lexiflash.domain.fsrs.Rating
import ir.laxipatem.lexiflash.platform.TtsController
import ir.laxipatem.lexiflash.platform.TtsState
import ir.laxipatem.lexiflash.ui.common.CardBack
import ir.laxipatem.lexiflash.ui.common.CardFront
import ir.laxipatem.lexiflash.ui.common.EmptyState
import ir.laxipatem.lexiflash.ui.common.ErrorState
import ir.laxipatem.lexiflash.ui.common.FlipCard
import ir.laxipatem.lexiflash.ui.common.LoadingState
import ir.laxipatem.lexiflash.ui.common.ViewModelFactories
import ir.laxipatem.lexiflash.ui.study.RatingPrediction

/** The daily review queue, reusing the study card and the rating buttons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(container: AppContainer) {
    val viewModel: ReviewViewModel = viewModel(
        factory = ViewModelFactories.containerFactory(container) { ReviewViewModel(it) }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(
        initialValue = ir.laxipatem.lexiflash.data.repo.UserSettings()
    )

    val context = LocalContext.current
    val tts = remember { TtsController(context) }
    DisposableEffect(tts) {
        tts.initialise()
        onDispose { tts.shutdown() }
    }
    LaunchedEffect(settings.ttsSpeed) { tts.setSpeed(settings.ttsSpeed) }
    val ttsState by tts.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.review_title)) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                state.isLoading -> LoadingState()

                state.errorMessage != null && state.queue.isEmpty() -> ErrorState(
                    message = state.errorMessage,
                    onRetry = { viewModel.load() }
                )

                state.isEmpty -> EmptyState(
                    title = stringResource(R.string.review_empty_title),
                    body = stringResource(R.string.review_empty_body)
                )

                state.isComplete -> EmptyState(
                    icon = Icons.Filled.TaskAlt,
                    title = stringResource(R.string.review_finished),
                    body = stringResource(R.string.review_finished_body)
                )

                else -> {
                    val card = state.currentCard
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.plural_due,
                                state.remaining,
                                state.remaining
                            ),
                            style = MaterialTheme.typography.labelLarge
                        )
                        LinearProgressIndicator(
                            progress = {
                                if (state.queue.isEmpty()) {
                                    0f
                                } else {
                                    state.currentIndex.toFloat() / state.queue.size
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (card != null) {
                        FlipCard(
                            front = {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    CardFront(english = card.english)
                                    IconButton(
                                        onClick = { tts.speak(card.english) },
                                        enabled = ttsState == TtsState.Ready
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.VolumeUp,
                                            contentDescription = stringResource(R.string.cd_speech)
                                        )
                                    }
                                }
                            },
                            back = {
                                CardBack(
                                    persian = card.persian,
                                    ipa = card.ipa,
                                    vocab = card.vocab.map { it.word to it.meaning },
                                    note = card.note,
                                    pendingTranslationLabel = stringResource(
                                        R.string.study_pending_translation
                                    )
                                )
                            },
                            flipped = state.isFlipped,
                            onFlip = viewModel::flip
                        )
                    }

                    if (state.isFlipped) {
                        RatingRow(
                            predictions = state.predictions,
                            enabled = !state.isSaving,
                            onRate = viewModel::rate
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.study_tap_to_flip),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/** Four equal-width rating buttons, mirroring the study screen. */
@Composable
private fun RatingRow(
    predictions: List<RatingPrediction>,
    enabled: Boolean,
    onRate: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        predictions.forEach { prediction ->
            val label = stringResource(
                when (prediction.rating) {
                    Rating.AGAIN -> R.string.rating_again
                    Rating.HARD -> R.string.rating_hard
                    Rating.GOOD -> R.string.rating_good
                    else -> R.string.rating_easy
                }
            )
            val description = stringResource(
                when (prediction.rating) {
                    Rating.AGAIN -> R.string.cd_rating_again
                    Rating.HARD -> R.string.cd_rating_hard
                    Rating.GOOD -> R.string.cd_rating_good
                    else -> R.string.cd_rating_easy
                }
            )
            Button(
                onClick = { onRate(prediction.rating) },
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (prediction.rating) {
                        Rating.AGAIN -> MaterialTheme.colorScheme.errorContainer
                        Rating.HARD -> MaterialTheme.colorScheme.tertiaryContainer
                        Rating.GOOD -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = when (prediction.rating) {
                        Rating.AGAIN -> MaterialTheme.colorScheme.onErrorContainer
                        Rating.HARD -> MaterialTheme.colorScheme.onTertiaryContainer
                        Rating.GOOD -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            ) {
                Text(text = label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
