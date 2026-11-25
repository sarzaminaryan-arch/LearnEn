package ir.laxipatem.lexiflash.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.laxipatem.lexiflash.AppContainer
import ir.laxipatem.lexiflash.R
import ir.laxipatem.lexiflash.domain.unlock.UnlockPolicy
import ir.laxipatem.lexiflash.ui.common.ErrorState
import ir.laxipatem.lexiflash.ui.common.Formatters
import ir.laxipatem.lexiflash.ui.common.LoadingState
import ir.laxipatem.lexiflash.ui.common.ViewModelFactories

/**
 * Home screen: the deck list, the parts of the selected deck and the import and
 * catalogue entry points.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenPart: (deckId: Long, partIndex: Int) -> Unit,
    onImport: () -> Unit,
    onOpenCatalog: () -> Unit
) {
    val viewModel: HomeViewModel = viewModel(
        factory = ViewModelFactories.containerFactory(container) { HomeViewModel(it) }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val onlineAvailable = container.onlineFeatures.isAvailable

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.home_title)) })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onImport,
                icon = { Icon(Icons.Outlined.EditNote, contentDescription = null) },
                text = { Text(stringResource(R.string.action_import)) }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))

            state.errorMessage != null -> ErrorState(
                message = state.errorMessage,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(padding)
            )

            state.decks.isEmpty() -> ir.laxipatem.lexiflash.ui.common.EmptyState(
                title = stringResource(R.string.state_empty_deck),
                body = stringResource(R.string.home_import_cta),
                actionLabel = stringResource(R.string.action_import),
                onAction = onImport,
                modifier = Modifier.padding(padding)
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(state.decks, key = { it.id }) { deck ->
                    DeckCard(
                        deck = deck,
                        selected = deck.id == state.selectedDeckId,
                        onClick = { viewModel.selectDeck(deck.id) }
                    )
                }

                if (state.parts.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.home_parts_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    item {
                        PartsGrid(
                            parts = state.parts,
                            usePersianDigits = settings.persianDigits,
                            onOpenPart = { partIndex ->
                                state.selectedDeckId?.let { deckId -> onOpenPart(deckId, partIndex) }
                            }
                        )
                    }
                }

                if (onlineAvailable) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.home_online_catalog),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                AssistChip(
                                    onClick = onOpenCatalog,
                                    label = { Text(stringResource(R.string.catalog_title)) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.CloudDownload, contentDescription = null)
                                    },
                                    colors = AssistChipDefaults.assistChipColors()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One deck row, showing its title, licence and overall progress. */
@Composable
private fun DeckCard(
    deck: DeckUiModel,
    selected: Boolean,
    onClick: () -> Unit
) {
    // DeckCard has no numeric text, so it takes no digit-formatting flag.
    val border = CardDefaults.outlinedCardBorder()
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (selected) null else border
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = deck.title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(
                            R.string.home_progress_label,
                            deck.seenCount,
                            deck.cardCount
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val progressDescription = stringResource(R.string.cd_progress_bar)
            LinearProgressIndicator(
                progress = { deck.completionRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = progressDescription
                    }
            )
        }
    }
}

/** Grid of part tiles, three per row. */
@Composable
private fun PartsGrid(
    parts: List<PartUiModel>,
    usePersianDigits: Boolean,
    onOpenPart: (Int) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth(),
        userScrollEnabled = false,
        contentPadding = PaddingValues(0.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(parts, key = { it.index }) { part ->
            PartTile(
                part = part,
                usePersianDigits = usePersianDigits,
                onClick = { if (part.access != UnlockPolicy.PartAccess.LOCKED) onOpenPart(part.index) }
            )
        }
    }
}

/** A single part tile with its lock state icon and accessible description. */
@Composable
private fun PartTile(
    part: PartUiModel,
    usePersianDigits: Boolean,
    onClick: () -> Unit
) {
    val locked = part.access == UnlockPolicy.PartAccess.LOCKED
    val completed = part.access == UnlockPolicy.PartAccess.COMPLETED
    val stateDescription = when (part.access) {
        UnlockPolicy.PartAccess.LOCKED -> stringResource(R.string.home_part_locked_desc)
        UnlockPolicy.PartAccess.OPEN -> stringResource(R.string.home_part_open_desc)
        UnlockPolicy.PartAccess.COMPLETED -> stringResource(R.string.home_part_completed_desc)
    }
    val stateLabel = when (part.access) {
        UnlockPolicy.PartAccess.LOCKED -> stringResource(R.string.cd_part_locked)
        UnlockPolicy.PartAccess.OPEN -> stringResource(R.string.cd_part_open)
        UnlockPolicy.PartAccess.COMPLETED -> stringResource(R.string.cd_part_completed)
    }

    // stringResource is @Composable and SemanticsPropertyReceiver is not, so the
    // label has to be resolved before the semantics block runs.
    val partLabel = stringResource(R.string.home_part_number, part.index)
    val tileDescription = "$partLabel. $stateDescription"

    Card(
        onClick = { if (!locked) onClick() },
        enabled = !locked,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = tileDescription
            },
        colors = CardDefaults.cardColors(
            containerColor = when {
                locked -> MaterialTheme.colorScheme.surfaceVariant
                completed -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.primaryContainer
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = when {
                    locked -> Icons.Filled.Lock
                    completed -> Icons.Filled.CheckCircle
                    else -> Icons.Filled.PlayArrow
                },
                contentDescription = stateLabel,
                modifier = Modifier.size(20.dp),
                tint = when {
                    locked -> MaterialTheme.colorScheme.onSurfaceVariant
                    completed -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.primary
                }
            )
            Text(
                text = stringResource(R.string.home_part_number, part.index),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = "${
                    Formatters.number(part.seenCount, usePersianDigits)
                }/${Formatters.number(part.cardCount, usePersianDigits)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinearProgressIndicator(
                progress = { part.completionRatio },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
