package ir.laxipatem.lexiflash.ui.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.laxipatem.lexiflash.AppContainer
import ir.laxipatem.lexiflash.R
import ir.laxipatem.lexiflash.domain.unlock.UnlockPolicy

/**
 * Score screen shown after a quiz.
 *
 * The pass decision is delegated to [UnlockPolicy.PASS_PERCENT] rather than
 * re-derived here, so the number shown to the learner and the number the unlock
 * rule uses can never diverge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizResultScreen(
    container: AppContainer,
    deckId: Long,
    partIndex: Int,
    score: Int,
    total: Int,
    onRetry: () -> Unit,
    onNextPart: () -> Unit,
    onBack: () -> Unit
) {
    val percent = if (total <= 0) 0 else (score * 100) / total
    val passed = percent >= UnlockPolicy.PASS_PERCENT

    var partCount by remember { mutableStateOf(0) }
    LaunchedEffect(deckId) {
        partCount = container.decks.deck(deckId)?.partCount ?: 0
    }
    val hasNextPart = partIndex < partCount

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.quiz_result_title)) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = if (passed) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = stringResource(R.string.quiz_result_percent, percent),
                style = MaterialTheme.typography.displaySmall
            )
            Text(
                text = stringResource(R.string.quiz_result_score, score, total),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = when {
                    passed && hasNextPart -> stringResource(R.string.quiz_result_passed)
                    passed -> stringResource(R.string.quiz_result_last_part)
                    else -> stringResource(R.string.quiz_result_not_passed, UnlockPolicy.PASS_PERCENT)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (passed && hasNextPart) {
                Button(
                    onClick = onNextPart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.quiz_result_next_part))
                }
            }

            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Icon(Icons.Outlined.Replay, contentDescription = null)
                Text(
                    text = stringResource(R.string.quiz_result_retry),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.quiz_result_back))
            }
        }
    }
}
