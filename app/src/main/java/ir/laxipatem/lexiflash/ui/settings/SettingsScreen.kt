package ir.laxipatem.lexiflash.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import ir.laxipatem.lexiflash.BuildConfig
import ir.laxipatem.lexiflash.R
import ir.laxipatem.lexiflash.data.repo.AppLanguage
import ir.laxipatem.lexiflash.data.repo.ThemeMode
import ir.laxipatem.lexiflash.data.repo.UserSettings
import ir.laxipatem.lexiflash.ui.common.Formatters
import ir.laxipatem.lexiflash.ui.common.ViewModelFactories

/** Preferences, online settings, destructive actions and the About entry point. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpenAbout: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = ViewModelFactories.containerFactory(container) { SettingsViewModel(it) }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.settings

    var showDeleteDialog by remember { mutableStateOf(false) }
    var catalogDraft by remember(settings.catalogUrl) { mutableStateOf(settings.catalogUrl) }
    var catalogError by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val deletedMessage = stringResource(R.string.settings_delete_all_desc)
    val failedMessage = stringResource(R.string.error_generic)

    LaunchedEffect(state.message) {
        when (state.message) {
            SettingsMessage.DATA_DELETED -> {
                snackbar.showSnackbar(deletedMessage)
                viewModel.clearMessage()
            }
            SettingsMessage.SAVE_FAILED -> {
                snackbar.showSnackbar(failedMessage)
                viewModel.clearMessage()
            }
            null -> Unit
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Appearance ---------------------------------------------------
            item { SectionHeader(stringResource(R.string.settings_section_appearance)) }
            item {
                SettingsCard {
                    Text(
                        text = stringResource(R.string.settings_theme),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeChip(
                            label = stringResource(R.string.settings_theme_system),
                            selected = settings.themeMode == ThemeMode.SYSTEM,
                            onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) }
                        )
                        ThemeChip(
                            label = stringResource(R.string.settings_theme_light),
                            selected = settings.themeMode == ThemeMode.LIGHT,
                            onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) }
                        )
                        ThemeChip(
                            label = stringResource(R.string.settings_theme_dark),
                            selected = settings.themeMode == ThemeMode.DARK,
                            onClick = { viewModel.setThemeMode(ThemeMode.DARK) }
                        )
                    }
                    SwitchRow(
                        title = stringResource(R.string.settings_dynamic_color),
                        subtitle = stringResource(R.string.settings_dynamic_color_desc),
                        checked = settings.dynamicColor,
                        onCheckedChange = viewModel::setDynamicColor
                    )
                }
            }

            // --- Language -----------------------------------------------------
            item { SectionHeader(stringResource(R.string.settings_section_language)) }
            item {
                SettingsCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeChip(
                            label = stringResource(R.string.settings_language_fa),
                            selected = settings.language == AppLanguage.PERSIAN,
                            onClick = { viewModel.setLanguage(AppLanguage.PERSIAN) }
                        )
                        ThemeChip(
                            label = stringResource(R.string.settings_language_en),
                            selected = settings.language == AppLanguage.ENGLISH,
                            onClick = { viewModel.setLanguage(AppLanguage.ENGLISH) }
                        )
                    }
                    SwitchRow(
                        title = stringResource(R.string.settings_persian_digits),
                        subtitle = stringResource(R.string.settings_persian_digits_desc),
                        checked = settings.persianDigits,
                        onCheckedChange = viewModel::setPersianDigits
                    )
                }
            }

            // --- Study --------------------------------------------------------
            item { SectionHeader(stringResource(R.string.settings_section_study)) }
            item {
                SettingsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_daily_goal),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = Formatters.number(settings.dailyGoal, settings.persianDigits),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Slider(
                        value = settings.dailyGoal.toFloat(),
                        onValueChange = { viewModel.setDailyGoal(it.toInt()) },
                        valueRange = UserSettings.MIN_DAILY_GOAL.toFloat()..UserSettings.MAX_DAILY_GOAL.toFloat(),
                        steps = 0
                    )
                    Text(
                        text = stringResource(R.string.settings_daily_goal_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_tts_speed),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = String.format(java.util.Locale.US, "%.1fx", settings.ttsSpeed),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Slider(
                        value = settings.ttsSpeed,
                        onValueChange = { viewModel.setTtsSpeed(it) },
                        valueRange = UserSettings.MIN_TTS_SPEED..UserSettings.MAX_TTS_SPEED
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_unlock_all),
                        subtitle = stringResource(R.string.settings_unlock_all_desc),
                        checked = settings.unlockAllParts,
                        onCheckedChange = viewModel::setUnlockAll
                    )
                }
            }

            // --- Online -------------------------------------------------------
            if (state.onlineAvailable) {
                item { SectionHeader(stringResource(R.string.settings_section_online)) }
                item {
                    SettingsCard {
                        OutlinedTextField(
                            value = catalogDraft,
                            onValueChange = {
                                catalogDraft = it
                                catalogError = false
                            },
                            label = { Text(stringResource(R.string.settings_catalog_url)) },
                            supportingText = {
                                Text(
                                    if (catalogError) {
                                        stringResource(R.string.settings_catalog_url_invalid)
                                    } else {
                                        stringResource(R.string.settings_catalog_url_desc)
                                    }
                                )
                            },
                            isError = catalogError,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    catalogError = !viewModel.setCatalogUrl(catalogDraft)
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(stringResource(R.string.action_save))
                            }
                            TextButton(
                                onClick = {
                                    viewModel.resetCatalogUrl()
                                    catalogError = false
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(stringResource(R.string.settings_catalog_url_reset))
                            }
                        }
                    }
                }
            }

            // --- Data ---------------------------------------------------------
            item { SectionHeader(stringResource(R.string.settings_section_data)) }
            item {
                SettingsCard {
                    Text(
                        text = stringResource(R.string.settings_delete_all),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = stringResource(R.string.settings_delete_all_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_delete_all),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // --- About ---------------------------------------------------------
            item { SectionHeader(stringResource(R.string.settings_section_about)) }
            item {
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onOpenAbout,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.settings_about))
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.developer_credit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.settings_delete_all)) },
            text = { Text(stringResource(R.string.settings_delete_all_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteAllData()
                }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
    // `context` is referenced so a future platform-specific setting cannot be
    // added without acknowledging the Activity context is available here.
    context.packageName
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.heightIn(min = 48.dp)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
