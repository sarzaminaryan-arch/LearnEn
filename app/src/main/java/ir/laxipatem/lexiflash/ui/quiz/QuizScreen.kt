package ir.laxipatem.lexiflash.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.laxipatem.lexiflash.AppContainer
import ir.laxipatem.lexiflash.R
import ir.laxipatem.lexiflash.domain.quiz.QuizKind
import ir.laxipatem.lexiflash.domain.quiz.QuizQuestion
import ir.laxipatem.lexiflash.ui.common.ErrorState
import ir.laxipatem.lexiflash.ui.common.LoadingState
import ir.laxipatem.lexiflash.ui.common.RaisedCard
import ir.laxipatem.lexiflash.ui.common.appCardOutlineColor
import ir.laxipatem.lexiflash.ui.common.ViewModelFactories
import ir.laxipatem.lexiflash.ui.theme.successColor

/**
 * Multiple-choice quiz for one part.
 *
 * Feedback is never colour-only: the selected option gains an icon, a text label
 * and a border, so the result is legible with a screen reader and without colour
 * perception.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    container: AppContainer,
    deckId: Long,
    partIndex: Int,
    onFinished: (score: Int, total: Int) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: QuizViewModel = viewModel(
        key = "quiz-$deckId-$partIndex",
        factory = ViewModelFactories.containerFactory(container) {
            QuizViewModel(it, deckId, partIndex)
        }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showExitDialog by remember { mutableStateOf(false) }

    // Hand control back to the navigation graph once the attempt is finished.
    // Navigation must not run during composition; it would re-fire on every
    // recomposition and push the result screen repeatedly.
    LaunchedEffect(state.isFinished) {
        if (state.isFinished) {
            onFinished(state.correctCount, state.total)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.quiz_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.questions.isNotEmpty() && !state.isFinished) {
                            showExitDialog = true
                        } else {
                            onBack()
                        }
                    }) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                state.isLoading -> LoadingState()
                state.errorMessage != null && state.questions.isEmpty() -> ErrorState(
                    message = state.errorMessage,
                    onRetry = viewModel::startAttempt
                )
                state.currentQuestion == null -> ErrorState(
                    message = stringResource(R.string.quiz_empty),
                    onRetry = null
                )
                else -> {
                    val question = state.currentQuestion!!
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(
                                R.string.quiz_progress,
                                state.currentIndex + 1,
                                state.total
                            ),
                            style = MaterialTheme.typography.labelLarge
                        )
                        if (state.answered) {
                            Text(
                                text = if (state.isCurrentCorrect) {
                                    stringResource(R.string.quiz_correct)
                                } else {
                                    stringResource(R.string.quiz_incorrect)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (state.isCurrentCorrect) {
                                    successColor(
                                        darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
                                    )
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth()
                    )

                    QuestionCard(question)

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        question.options.forEachIndexed { index, option ->
                            OptionRow(
                                text = option,
                                index = index,
                                question = question,
                                selectedIndex = state.selectedIndex,
                                onClick = { viewModel.answer(index) }
                            )
                        }
                    }

                    if (state.answered) {
                        FeedbackBlock(question = question, isCorrect = state.isCurrentCorrect)
                        Button(
                            onClick = { viewModel.next() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .padding(bottom = 8.dp)
                        ) {
                            Text(
                                if (state.currentIndex + 1 >= state.total) {
                                    stringResource(R.string.action_done)
                                } else {
                                    stringResource(R.string.action_next)
                                }
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.quiz_pick_one),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    if (showExitDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.quiz_title)) },
            text = { Text(stringResource(R.string.quiz_exit_confirm)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showExitDialog = false
                    onBack()
                }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/** The prompt, with a heading that explains what is being asked. */
@Composable
private fun QuestionCard(question: QuizQuestion) {
    val promptDirection = if (question.kind == QuizKind.REVERSE) {
        TextDirection.ContentOrRtl
    } else {
        TextDirection.Ltr
    }
    RaisedCard(
        modifier = Modifier.fillMaxWidth(),
        faceColor = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(
                    when (question.kind) {
                        QuizKind.MEANING -> R.string.quiz_question_meaning
                        QuizKind.REVERSE -> R.string.quiz_question_reverse
                        QuizKind.CLOZE -> R.string.quiz_question_cloze
                    }
                ),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
            Text(
                text = question.prompt,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium,
                    textDirection = promptDirection
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** One selectable answer, with distinct visuals for correct, wrong and idle. */
@Composable
private fun OptionRow(
    text: String,
    index: Int,
    question: QuizQuestion,
    selectedIndex: Int?,
    onClick: () -> Unit
) {
    val answered = selectedIndex != null
    val isCorrect = index == question.correctIndex
    val isSelected = index == selectedIndex
    val revealCorrect = answered && isCorrect
    val revealWrong = answered && isSelected && !isCorrect

    val containerColor = when {
        revealCorrect -> MaterialTheme.colorScheme.primaryContainer
        revealWrong -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        revealCorrect -> MaterialTheme.colorScheme.primary
        revealWrong -> MaterialTheme.colorScheme.error
        else -> appCardOutlineColor()
    }

    val stateContent = when {
        revealCorrect -> stringResource(R.string.cd_correct)
        revealWrong -> stringResource(R.string.cd_wrong)
        else -> null
    }
    val optionDirection = if (question.kind == QuizKind.MEANING) {
        TextDirection.ContentOrRtl
    } else {
        TextDirection.Ltr
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .selectable(
                selected = isSelected,
                enabled = !answered,
                role = Role.RadioButton,
                onClick = onClick
            )
            .semantics {
                if (stateContent != null) contentDescription = "$text, $stateContent"
            }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null,
            enabled = !answered
        )
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(textDirection = optionDirection),
            textAlign = TextAlign.Center
        )
        when {
            revealCorrect -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.cd_correct),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            revealWrong -> Icon(
                imageVector = Icons.Filled.Cancel,
                contentDescription = stringResource(R.string.cd_wrong),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Immediate feedback with a centered, direction-safe answer and optional Persian help. */
@Composable
private fun FeedbackBlock(question: QuizQuestion, isCorrect: Boolean) {
    val accent = if (isCorrect) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    val answerDirection = if (question.kind == QuizKind.MEANING) {
        TextDirection.ContentOrRtl
    } else {
        TextDirection.Ltr
    }
    RaisedCard(
        modifier = Modifier.fillMaxWidth(),
        faceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = PaddingValues(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isCorrect) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (isCorrect) {
                        stringResource(R.string.quiz_correct)
                    } else {
                        stringResource(R.string.quiz_incorrect)
                    },
                    modifier = Modifier.padding(start = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = accent
                )
            }
            Text(
                text = stringResource(R.string.quiz_correct_answer),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = question.correctOption,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge.copy(textDirection = answerDirection),
                textAlign = TextAlign.Center
            )
            if (!isCorrect) {
                question.explanationPersian?.let { persian ->
                    Text(
                        text = persian,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            textDirection = TextDirection.ContentOrRtl
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            question.focusWord?.let { word ->
                Text(
                    text = stringResource(R.string.quiz_explanation_vocab),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = word,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        textDirection = TextDirection.Ltr
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
