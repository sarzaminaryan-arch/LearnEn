package ir.laxipatem.lexiflash.ui.home

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.laxipatem.lexiflash.AppContainer
import ir.laxipatem.lexiflash.R
import ir.laxipatem.lexiflash.platform.CatalogEntry
import ir.laxipatem.lexiflash.ui.common.EmptyState
import ir.laxipatem.lexiflash.ui.common.ErrorState
import ir.laxipatem.lexiflash.ui.common.LoadingState
import ir.laxipatem.lexiflash.ui.common.RaisedCard
import ir.laxipatem.lexiflash.ui.common.ViewModelFactories
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State of the remote catalogue browser. */
data class CatalogUiState(
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val errorMessage: String? = null,
    val entries: List<CatalogEntry> = emptyList(),
    val message: String? = null,
    val downloadingId: String? = null,
    val downloadingTitle: String? = null,
    val downloadProgress: Float? = null
)

/**
 * Fetches and downloads catalogue decks.
 *
 * The catalogue is loaded as soon as the screen opens. On the device this was
 * reported as a 404 because the default URL pointed at a repository that does
 * not exist, which meant a tap on "Load catalogue" produced an error and
 * nothing else. Loading automatically, and offering the bundled catalogue as a
 * fallback, turns that dead end into a list of decks.
 *
 * Every download is still an explicit tap. Nothing is stored without one.
 */
class CatalogViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    fun load() {
        if (_state.value.isLoading) return
        val online = container.onlineFeatures
        if (!online.isAvailable) {
            _state.update { it.copy(hasLoaded = true, errorMessage = null) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val url = container.settings.current().catalogUrl
            Log.d("LexiImport", "catalog fetch starting url=$url")
            runCatching { online.fetchCatalog(url).getOrThrow() }
                .onSuccess { entries ->
                    Log.d("LexiImport", "catalog fetch finished entries=${entries.size}")
                    _state.value = CatalogUiState(
                        isLoading = false,
                        hasLoaded = true,
                        entries = entries,
                        errorMessage = null
                    )
                }
                .onFailure { error ->
                    Log.e("LexiImport", "catalog fetch failed", error)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            hasLoaded = true,
                            errorMessage = error.message ?: "catalog-failed"
                        )
                    }
                }
        }
    }

    /**
     * Downloads one deck and reports progress as a fraction, so the button can
     * show that something is happening on a slow connection.
     */
    fun download(entry: CatalogEntry) {
        viewModelScope.launch {
            Log.d("LexiImport", "deck download starting id=${entry.id} url=${entry.url}")
            _state.update {
                it.copy(
                    downloadingId = entry.id,
                    downloadingTitle = entry.title,
                    downloadProgress = 0f,
                    errorMessage = null
                )
            }
            runCatching {
                val json = container.onlineFeatures
                    .downloadDeck(entry.url)
                    .getOrThrow()
                Log.d("LexiImport", "deck download response received chars=${json.length}")
                _state.update { it.copy(downloadProgress = 0.6f) }
                Log.d("LexiImport", "Room save downloaded deck starting")
                container.decks.saveDeckFromJson(json).getOrThrow()
            }.onSuccess {
                Log.d("LexiImport", "Room save downloaded deck finished")
                _state.update {
                    it.copy(
                        downloadingId = null,
                        downloadingTitle = null,
                        downloadProgress = null,
                        message = entry.title
                    )
                }
            }.onFailure { error ->
                Log.e("LexiImport", "deck download or save failed", error)
                _state.update {
                    it.copy(
                        downloadingId = null,
                        downloadingTitle = null,
                        downloadProgress = null,
                        errorMessage = error.message ?: "download-failed"
                    )
                }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null, errorMessage = null) }
}

/**
 * Browse and download decks from the configured HTTPS catalogue.
 *
 * The catalogue is fetched once on entry rather than behind a button: the
 * screen exists to show decks, and a tap that only reveals "the server answered
 * 404" is not a useful first interaction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: CatalogViewModel = viewModel(
        factory = ViewModelFactories.containerFactory(container) { CatalogViewModel(it) }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(
        initialValue = ir.laxipatem.lexiflash.data.repo.UserSettings()
    )
    val onlineAvailable = remember(container) { container.onlineFeatures.isAvailable }

    // Load on entry, once. A failure leaves the error state with its retry.
    LaunchedEffect(onlineAvailable) {
        if (onlineAvailable) viewModel.load()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.catalog_title)) },
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
            when {
                !onlineAvailable -> EmptyState(
                    title = stringResource(R.string.catalog_offline_note)
                )

                state.isLoading && state.entries.isEmpty() -> LoadingState()

                state.errorMessage != null && state.entries.isEmpty() -> ErrorState(
                    message = state.errorMessage,
                    onRetry = viewModel::load
                )

                state.hasLoaded && state.entries.isEmpty() -> EmptyState(
                    title = stringResource(R.string.catalog_empty),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = viewModel::load
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(state.entries, key = { it.id }) { entry ->
                        CatalogDeckCard(
                            entry = entry,
                            downloading = state.downloadingId == entry.id,
                            anyDownloading = state.downloadingId != null,
                            progress = if (state.downloadingId == entry.id) {
                                state.downloadProgress
                            } else {
                                null
                            },
                            onDownload = { viewModel.download(entry) }
                        )
                    }

                    item {
                        Text(
                            text = settings.catalogUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            state.message?.let { title ->
                Text(
                    text = stringResource(R.string.catalog_downloaded, title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

/**
 * One catalogue row.
 *
 * The whole card is the download target: a tap anywhere on it starts the
 * download, which is what a catalogue row is for. The button inside is drawn
 * for discoverability and reports its own progress while the work runs.
 */
@Composable
private fun CatalogDeckCard(
    entry: CatalogEntry,
    downloading: Boolean,
    anyDownloading: Boolean,
    progress: Float?,
    onDownload: () -> Unit
) {
    RaisedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (anyDownloading) null else onDownload,
        enabled = !anyDownloading,
        contentPadding = PaddingValues(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium
            )
            if (entry.description.isNotBlank()) {
                Text(
                    text = entry.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.catalog_cards, entry.cardCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onDownload,
                    enabled = !anyDownloading,
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    if (downloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = stringResource(
                            if (downloading) R.string.catalog_downloading
                            else R.string.catalog_download
                        ),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
