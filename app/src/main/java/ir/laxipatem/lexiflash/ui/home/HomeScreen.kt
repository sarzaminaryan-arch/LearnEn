package ir.laxipatem.lexiflash.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.EditNote
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
import androidx.compose.runtime.remember
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
import ir.laxipatem.lexiflash.ui.common.RaisedCard
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
                        // The whole card opens the catalogue, which now starts
                        // fetching as soon as it opens. A chip inside it would
                        // add a tap that leads to another tap.
                        RaisedCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onOpenCatalog,
                            faceColor = MaterialTheme.colorScheme.primaryContainer,
                            edgeColor = MaterialTheme.colorScheme.primary
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CloudDownload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.home_online_catalog),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = stringResource(R.string.home_online_catalog_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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
    RaisedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        faceColor = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
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

/**
 * Grid of part tiles, three per row.
 *
 * This is a plain [Column] of [Row]s rather than a LazyVerticalGrid, and the
 * choice is deliberate. PartsGrid is rendered inside an `item {}` of the
 * LazyColumn above, which measures its children with an unbounded maximum
 * height so they can size themselves. A lazy grid refuses that: it throws
 * "Vertically scrollable component was measured with an infinity maximum height
 * constraints" at measure time and takes the process down on the first frame.
 * Lazy grids cannot be nested in a lazy parent on the same axis; the part count
 * is small and fixed anyway, so nothing is lost by laying it out eagerly.
 */
@Composable
private fun PartsGrid(
    parts: List<PartUiModel>,
    usePersianDigits: Boolean,
    onOpenPart: (Int) -> Unit
) {
    val rows = remember(parts) { parts.chunked(PART_COLUMNS) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { part ->
                    Box(modifier = Modifier.weight(1f)) {
                        PartTile(
                            part = part,
                            usePersianDigits = usePersianDigits,
                            onClick = {
                                if (part.access != UnlockPolicy.PartAccess.LOCKED) {
                                    onOpenPart(part.index)
                                }
                            }
                        )
                    }
                }
                // Keep the last row aligned with the rows above it instead of
                // stretching one or two tiles across the full width.
                repeat(PART_COLUMNS - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Parts per row in [PartsGrid]. */
private const val PART_COLUMNS = 3

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

    // A locked tile keeps its depth but not its press: it still looks like a
    // physical tile sitting on the page, which is what makes the unlocked ones
    // read as pressable.
    RaisedCard(
        onClick = if (locked) null else onClick,
        enabled = !locked,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = tileDescription
            },
        faceColor = when {
            locked -> MaterialTheme.colorScheme.surfaceContainerLow
            completed -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        edgeColor = when {
            locked -> MaterialTheme.colorScheme.surfaceContainerHighest
            completed -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        },
        contentPadding = PaddingValues(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
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
