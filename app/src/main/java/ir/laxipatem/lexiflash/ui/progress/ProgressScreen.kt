package ir.laxipatem.lexiflash.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.laxipatem.lexiflash.AppContainer
import ir.laxipatem.lexiflash.R
import ir.laxipatem.lexiflash.platform.DeckExporter
import ir.laxipatem.lexiflash.ui.common.Formatters
import ir.laxipatem.lexiflash.ui.common.LoadingState
import ir.laxipatem.lexiflash.ui.common.RaisedCard
import ir.laxipatem.lexiflash.ui.common.ViewModelFactories
import ir.laxipatem.lexiflash.ui.home.PartUiModel

/** Statistics dashboard: streak, accuracy, due count, goal and per-part progress. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(container: AppContainer) {
    val viewModel: ProgressViewModel = viewModel(
        factory = ViewModelFactories.containerFactory(container) { ProgressViewModel(it) }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val export by viewModel.exportState.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(
        initialValue = ir.laxipatem.lexiflash.data.repo.UserSettings()
    )

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.progress_title)) }) }
    ) { padding ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        label = stringResource(R.string.progress_streak),
                        value = Formatters.number(state.stats.streakDays, settings.persianDigits),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = stringResource(R.string.progress_accuracy),
                        value = stringResource(
                            R.string.progress_accuracy_value,
                            state.stats.accuracyPercent
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        label = stringResource(R.string.progress_due),
                        value = Formatters.number(state.stats.dueNow, settings.persianDigits),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = stringResource(R.string.progress_daily_goal),
                        value = stringResource(
                            R.string.progress_daily_goal_value,
                            state.stats.reviewsToday,
                            state.dailyGoal
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                RaisedCard(
                    modifier = Modifier.fillMaxWidth(),
                    faceColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.progress_reviews_total),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = Formatters.number(
                                state.stats.totalReviews,
                                settings.persianDigits
                            ),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = stringResource(R.string.progress_accuracy_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (state.parts.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.progress_parts_header),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(state.parts, key = { it.index }) { part ->
                    PartProgressRow(part = part, usePersianDigits = settings.persianDigits)
                }
            }
            if (state.deckId != null) {
                item {
                    ExportCard(
                        export = export,
                        exporter = container.exporter,
                        subject = viewModel.exportSubject,
                        onExport = viewModel::exportDeck,
                        onDismiss = viewModel::clearExportResult
                    )
                }
            }
        }
    }
}

/**
 * Deck export: writes a deck.json file into the cache directory and exposes it
 * through the app's FileProvider so the share sheet can hand it to any target.
 */
@Composable
private fun ExportCard(
    export: ExportUiState,
    exporter: DeckExporter,
    subject: String,
    onExport: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onExport,
            enabled = !export.isExporting,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.action_export))
        }
        export.uri?.let { uri ->
            Button(
                onClick = {
                    // One builder in DeckExporter owns the MIME type, the subject
                    // and the read grant, so the share sheet cannot drift from
                    // what the exporter actually wrote.
                    context.startActivity(
                        exporter.shareIntent(
                            uri = uri,
                            subject = subject,
                            chooserTitle = context.getString(R.string.export_share)
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.export_share))
            }
        }
        if (export.error) {
            Text(
                text = stringResource(R.string.export_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    RaisedCard(
        modifier = modifier,
        faceColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(text = value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun PartProgressRow(part: PartUiModel, usePersianDigits: Boolean) {
    val label = stringResource(R.string.progress_part_title, part.index)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "$label: ${part.seenCount} / ${part.cardCount}"
            },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${Formatters.number(part.seenCount, usePersianDigits)}/${
                    Formatters.number(part.cardCount, usePersianDigits)
                }",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { part.completionRatio },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
