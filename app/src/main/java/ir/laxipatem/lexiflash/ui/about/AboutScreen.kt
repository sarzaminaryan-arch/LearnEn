package ir.laxipatem.lexiflash.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.laxipatem.lexiflash.BuildConfig
import ir.laxipatem.lexiflash.R

/**
 * Attribution and licence screen.
 *
 * Tatoeba is credited by name with the licence identifiers used by the deck, and
 * the trademark disclaimer is shown verbatim as required.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { AboutCard(title = stringResource(R.string.about_app_section)) {
                Text(text = stringResource(R.string.about_body), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = stringResource(R.string.about_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
            } }

            item { AboutCard(title = stringResource(R.string.about_data_section)) {
                Text(
                    text = stringResource(R.string.about_tatoeba),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.about_tatoeba_body),
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(
                    onClick = { uriHandler.openUri("https://tatoeba.org") },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.about_open_tatoeba))
                }
            } }

            item { AboutCard(title = stringResource(R.string.about_licenses_section)) {
                Text(
                    text = stringResource(R.string.about_licenses_body),
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(
                    onClick = {
                        uriHandler.openUri(
                            "https://www.apache.org/licenses/LICENSE-2.0.txt"
                        )
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Apache License 2.0")
                }
            } }

            item { AboutCard(title = stringResource(R.string.about_privacy)) {
                Text(
                    text = stringResource(R.string.about_privacy_body),
                    style = MaterialTheme.typography.bodyMedium
                )
            } }
        }
    }
}

@Composable
private fun AboutCard(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
