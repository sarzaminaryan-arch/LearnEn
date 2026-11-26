package ir.laxipatem.lexiflash.ui.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.laxipatem.lexiflash.AppContainer
import ir.laxipatem.lexiflash.R
import ir.laxipatem.lexiflash.domain.fsrs.Rating
import ir.laxipatem.lexiflash.ui.common.CardBack
import ir.laxipatem.lexiflash.ui.common.CardFront
import ir.laxipatem.lexiflash.ui.common.ErrorState
import ir.laxipatem.lexiflash.ui.common.FlipCard
import ir.laxipatem.lexiflash.ui.common.Formatters
import ir.laxipatem.lexiflash.ui.common.IntervalText
import ir.laxipatem.lexiflash.ui.common.LoadingState
import ir.laxipatem.lexiflash.ui.common.ViewModelFactories

/**
 * Flashcard study screen for one deck part.
 *
 * The card flips on tap. The four rating buttons only appear once the answer has
 * been revealed, which is what keeps the self-assessment honest, and each button
 * shows the interval FSRS would schedule.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    container: AppContainer,
    deckId: Long,
    partIndex: Int,
    onStartQuiz: () -> Unit,
    onBack: () -> Unit
) {
    val viewModel: StudyViewModel = viewModel(
        key = "study-$deckId-$partIndex",
        factory = ViewModelFactories.containerFactory(container) {
            StudyViewModel(it, deckId, partIndex)
        }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(
        initialValue = ir.laxipatem.lexiflash.data.repo.UserSettings()
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = state.deckTitle, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(R.string.home_part_number, partIndex),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
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

                state.errorMessage != null && state.cards.isEmpty() -> ErrorState(
                    message = state.errorMessage,
                    onRetry = viewModel::load
                )

                state.isFinished -> FinishedState(
                    cardCount = state.cards.size,
                    onStartQuiz = onStartQuiz,
                    onBack = onBack
                )

                else -> {
                    StudyHeader(
                        current = state.currentIndex + 1,
                        total = state.total,
                        progress = state.progress,
                        usePersianDigits = settings.persianDigits
                    )

                    BoxWithWeight {
                        val card = state.currentCard
                        if (card != null) {
                            FlipCard(
                                front = { CardFront(english = card.english) },
                                back = {
                                    CardBack(
                                        persian = card.persian,
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
                    }

                    if (state.isFlipped) {
                        RatingButtons(
                            predictions = state.predictions,
                            enabled = !state.isSaving,
                            usePersianDigits = settings.persianDigits,
                            onRate = viewModel::rate
                        )
                    } else {
                        HintText(stringResource(R.string.study_tap_to_flip))
                    }
                }
            }
        }
    }
}

/** Lays content out in the remaining vertical space without a fixed height. */
@Composable
private fun BoxWithWeight(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) { content() }
}

@Composable
private fun StudyHeader(
    current: Int,
    total: Int,
    progress: Float,
    usePersianDigits: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.study_title),
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = stringResource(
                    R.string.study_progress,
                    current,
                    total
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

/** The four rating buttons, each at least 48dp tall. */
@Composable
private fun RatingButtons(
    predictions: List<RatingPrediction>,
    enabled: Boolean,
    usePersianDigits: Boolean,
    onRate: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                val contentDescription = stringResource(
                    when (prediction.rating) {
                        Rating.AGAIN -> R.string.cd_rating_again
                        Rating.HARD -> R.string.cd_rating_hard
                        Rating.GOOD -> R.string.cd_rating_good
                        else -> R.string.cd_rating_easy
                    }
                )
                val interval = intervalLabel(prediction, usePersianDigits)

                val containerColor = when (prediction.rating) {
                    Rating.AGAIN -> MaterialTheme.colorScheme.errorContainer
                    Rating.HARD -> MaterialTheme.colorScheme.tertiaryContainer
                    Rating.GOOD -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.secondaryContainer
                }
                val contentColor = when (prediction.rating) {
                    Rating.AGAIN -> MaterialTheme.colorScheme.onErrorContainer
                    Rating.HARD -> MaterialTheme.colorScheme.onTertiaryContainer
                    Rating.GOOD -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                }

                Button(
                    onClick = { onRate(prediction.rating) },
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = containerColor,
                        contentColor = contentColor
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 4.dp,
                        vertical = 8.dp
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = label, style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = interval,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/** Formats the predicted interval of a rating button. */
@Composable
private fun intervalLabel(prediction: RatingPrediction, usePersianDigits: Boolean): String =
    when (val text = Formatters.interval(prediction.days, prediction.minutes)) {
        is IntervalText.Minutes -> stringResource(
            R.string.rating_again_when,
            text.value.toInt()
        )
        is IntervalText.Days -> stringResource(
            R.string.rating_interval_days,
            text.value
        )
        is IntervalText.Months -> stringResource(
            R.string.rating_interval_months,
            text.value
        )
        is IntervalText.Years -> stringResource(
            R.string.rating_interval_years,
            text.value
        )
    }

@Composable
private fun FinishedState(
    cardCount: Int,
    onStartQuiz: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.study_finished_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.study_finished_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = androidx.compose.ui.res.pluralStringResource(
                R.plurals.plural_cards,
                cardCount,
                cardCount
            ),
            style = MaterialTheme.typography.labelLarge
        )
        Button(
            onClick = onStartQuiz,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.study_start_quiz))
        }
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.study_back_to_parts))
        }
    }
}
