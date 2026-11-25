package ir.laxipatem.lexiflash.ui.home

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    val downloadingId: String? = null
)

/**
 * Fetches and downloads catalogue decks.
 *
 * Every network call is triggered by a button press; nothing is fetched when the
 * screen opens. Downloaded documents are validated by the shared deck parser
 * before they are stored, so a broken remote file cannot corrupt the database.
 */
class CatalogViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    fun load() {
        val settings = container.onlineFeatures
        if (!settings.isAvailable) {
            _state.update { it.copy(hasLoaded = true, errorMessage = "offline-build") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val url = container.settings.current().catalogUrl
            runCatching { settings.fetchCatalog(url).getOrThrow() }
                .onSuccess { entries ->
                    _state.value = CatalogUiState(
                        isLoading = false,
                        hasLoaded = true,
                        entries = entries,
                        errorMessage = null
                    )
                }
                .onFailure { error ->
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

    fun download(entry: CatalogEntry) {
        viewModelScope.launch {
            _state.update { it.copy(downloadingId = entry.id, errorMessage = null) }
            runCatching {
                val json = container.onlineFeatures.downloadDeck(entry.url).getOrThrow()
                container.decks.saveDeckFromJson(json).getOrThrow()
            }.onSuccess {
                _state.update {
                    it.copy(downloadingId = null, message = entry.title)
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(downloadingId = null, errorMessage = error.message ?: "download-failed")
                }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null, errorMessage = null) }
}

/** Browse and download decks from the configured HTTPS catalogue. */
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
                !container.onlineFeatures.isAvailable -> EmptyState(
                    title = stringResource(R.string.catalog_offline_note)
                )

                state.isLoading -> LoadingState()

                state.errorMessage != null && state.entries.isEmpty() -> ErrorState(
                    message = state.errorMessage,
                    onRetry = viewModel::load
                )

                !state.hasLoaded -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = viewModel::load,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.catalog_fetch))
                    }
                    Text(
                        text = settings.catalogUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                state.entries.isEmpty() -> EmptyState(
                    title = stringResource(R.string.catalog_empty),
                    actionLabel = stringResource(R.string.catalog_fetch),
                    onAction = viewModel::load
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.entries, key = { it.id }) { entry ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
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
                                Text(
                                    text = stringResource(R.string.catalog_cards, entry.cardCount),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Row {
                                    Button(
                                        onClick = { viewModel.download(entry) },
                                        enabled = state.downloadingId == null,
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text(stringResource(R.string.catalog_download))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            state.message?.let { title ->
                Text(
                    text = stringResource(R.string.catalog_downloaded, title),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
