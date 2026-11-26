package ir.laxipatem.lexiflash.ui.importer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.laxipatem.lexiflash.AppContainer
import ir.laxipatem.lexiflash.R
import ir.laxipatem.lexiflash.ui.common.Formatters
import ir.laxipatem.lexiflash.ui.common.RaisedCard
import ir.laxipatem.lexiflash.ui.common.ViewModelFactories

/** MIME filter used for the deck picker: text formats plus JSON. */
private val IMPORT_MIME_TYPES = arrayOf(
    "text/*",
    "application/json",
    "application/octet-stream"
)

/**
 * Import wizard: a one-time copyright notice, file picking through the Storage
 * Access Framework, an editable preview and a transactional save.
 *
 * No storage permission is requested anywhere; the platform grants read access to
 * exactly the file the learner picks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    container: AppContainer,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ImportViewModel = viewModel(
        factory = ViewModelFactories.containerFactory(container) { ImportViewModel(it, context) }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refreshDisclaimer() }
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            onDone()
            viewModel.restart()
        }
    }

    // Holds the English file while the learner picks the matching Persian one.
    var pendingEnglish by remember { mutableStateOf<Pair<Uri, String>?>(null) }

    val singlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importSingleFile(it, uriName(context, it)) }
    }

    val englishPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        pendingEnglish = uri?.let { it to uriName(context, it) }
    }

    val persianPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val english = pendingEnglish
        if (uri != null && english != null) {
            viewModel.importBilingual(
                englishUri = english.first,
                englishName = english.second,
                persianUri = uri,
                persianName = uriName(context, uri)
            )
            pendingEnglish = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
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
        ) {
            if (state.isWorking) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.heightIn(min = 24.dp))
                    Text(stringResource(R.string.import_processing))
                }
            }

            when (state.step) {
                ImportStep.PICK -> PickStep(
                    showDisclaimer = !state.disclaimerAccepted,
                    onAcceptDisclaimer = viewModel::acceptDisclaimer,
                    onPickSingle = { singlePicker.launch(IMPORT_MIME_TYPES) },
                    onPickEnglish = { englishPicker.launch(IMPORT_MIME_TYPES) },
                    onPickPersian = { persianPicker.launch(IMPORT_MIME_TYPES) }
                )

                ImportStep.PREVIEW -> PreviewStep(
                    state = state,
                    onTitleChange = viewModel::setDeckTitle,
                    onPartCountChange = viewModel::setPartCount,
                    onEditTranslation = viewModel::editTranslation,
                    onRemoveCard = viewModel::removeCard,
                    onTranslate = viewModel::translatePending,
                    onSave = viewModel::save
                )
            }
        }
    }

    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text(stringResource(R.string.import_failed)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
}

@Composable
private fun PickStep(
    showDisclaimer: Boolean,
    onAcceptDisclaimer: () -> Unit,
    onPickSingle: () -> Unit,
    onPickEnglish: () -> Unit,
    onPickPersian: () -> Unit
) {

    item {
        if (showDisclaimer) {
            RaisedCard(faceColor = MaterialTheme.colorScheme.primaryContainer) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.import_disclaimer_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.import_disclaimer_body),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = onAcceptDisclaimer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.import_disclaimer_accept))
                    }
                }
            }
        }
    }

    item {
        RaisedCard(
            onClick = if (showDisclaimer) null else onPickSingle,
            enabled = !showDisclaimer
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.import_pick_cta),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.import_supported_formats),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    item {
        Text(
            text = stringResource(R.string.import_bilingual_cta),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    item {
        RaisedCard(
            onClick = if (showDisclaimer) null else onPickEnglish,
            enabled = !showDisclaimer
        ) {
            Text(
                text = stringResource(R.string.import_bilingual_english),
                style = MaterialTheme.typography.titleSmall
            )
        }
    }

    item {
        RaisedCard(
            onClick = if (showDisclaimer) null else onPickPersian,
            enabled = !showDisclaimer
        ) {
            Text(
                text = stringResource(R.string.import_bilingual_persian),
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

@Composable
private fun PreviewStep(
    state: ImportUiState,
    onTitleChange: (String) -> Unit,
    onPartCountChange: (Int) -> Unit,
    onEditTranslation: (Int, String) -> Unit,
    onRemoveCard: (Int) -> Unit,
    onTranslate: () -> Unit,
    onSave: () -> Unit
) {
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = state.deckTitle,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.import_deck_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.import_part_count),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { onPartCountChange(state.requestedParts - 1) },
                        enabled = state.requestedParts > ImportUiState.MIN_PARTS,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text("−") }
                    Text(
                        text = Formatters.number(state.partCount, false),
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(
                        onClick = { onPartCountChange(state.requestedParts + 1) },
                        enabled = state.requestedParts < ImportUiState.MAX_PARTS,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text("+") }
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.import_card_count, state.cardCount),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (state.onlineAvailable && state.pendingCount > 0) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = onTranslate,
                        enabled = !state.isTranslating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Filled.Translate, contentDescription = null)
                        Text(
                            text = stringResource(R.string.import_translate_cta),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.import_translate_consent),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        itemsIndexed(state.cards) { index, card ->
            Card {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = card.english, style = MaterialTheme.typography.bodyLarge)
                    if (card.persian.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.import_card_pending),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            text = card.persian,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (card.machineTranslated) {
                            // Machine output is always labelled, so a learner never
                            // mistakes it for a human translation.
                            Text(
                                text = stringResource(R.string.import_machine_translated),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { editingIndex = index },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(stringResource(R.string.import_edit_translation))
                        }
                        IconButton(
                            onClick = { onRemoveCard(index) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.import_remove_card)
                            )
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = onSave,
                enabled = state.cardCount > 0 && !state.isWorking,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.import_save_cta))
            }
        }
    }

    editingIndex?.let { index ->
        val card = state.cards.getOrNull(index)
        if (card != null) {
            var draft by remember(index) { mutableStateOf(card.persian.orEmpty()) }
            AlertDialog(
                onDismissRequest = { editingIndex = null },
                title = { Text(stringResource(R.string.import_edit_translation)) },
                text = {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text(stringResource(R.string.import_translation_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        onEditTranslation(index, draft)
                        editingIndex = null
                    }) {
                        Text(stringResource(R.string.action_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingIndex = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    }
}

/** Resolves a display name for a content URI, falling back to the last path segment. */
private fun uriName(context: android.content.Context, uri: Uri): String {
    val cursor = runCatching {
        context.contentResolver.query(uri, null, null, null, null)
    }.getOrNull()
    cursor?.use {
        val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && it.moveToFirst()) {
            val name = it.getString(index)
            if (!name.isNullOrBlank()) return name
        }
    }
    return uri.lastPathSegment ?: "imported"
}
