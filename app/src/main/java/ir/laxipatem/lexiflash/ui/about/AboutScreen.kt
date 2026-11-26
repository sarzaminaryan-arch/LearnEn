package ir.laxipatem.lexiflash.ui.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.laxipatem.lexiflash.BuildConfig
import ir.laxipatem.lexiflash.CrashReport
import ir.laxipatem.lexiflash.R
import ir.laxipatem.lexiflash.ui.common.glassOutline

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
    val context = LocalContext.current
    var showCrashReport by remember { mutableStateOf(false) }
    var crashReport by remember { mutableStateOf<String?>(null) }

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
                TextButton(
                    onClick = {
                        crashReport = CrashReport.read(context).orEmpty()
                        showCrashReport = true
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.crash_report_open))
                }
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

    if (showCrashReport) {
        val report = crashReport.orEmpty()
        AlertDialog(
            onDismissRequest = { showCrashReport = false },
            title = { Text(stringResource(R.string.crash_report_title)) },
            text = {
                if (report.isBlank()) {
                    Text(stringResource(R.string.crash_report_empty))
                } else {
                    Text(
                        text = report,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (report.isNotBlank()) {
                        TextButton(onClick = {
                            runCatching {
                                context.getSystemService(ClipboardManager::class.java)
                                    ?.setPrimaryClip(ClipData.newPlainText("LexiFlash crash", report))
                            }
                            Toast.makeText(
                                context,
                                context.getString(R.string.crash_report_copied),
                                Toast.LENGTH_SHORT
                            ).show()
                        }) { Text(stringResource(R.string.crash_report_copy)) }
                        TextButton(onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, report)
                            }
                            runCatching {
                                context.startActivity(
                                    Intent.createChooser(sendIntent, context.getString(R.string.crash_report_share))
                                )
                            }
                        }) { Text(stringResource(R.string.crash_report_share)) }
                    }
                    TextButton(onClick = { showCrashReport = false }) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            }
        )
    }
}

@Composable
private fun AboutCard(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().glassOutline(MaterialTheme.shapes.medium),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
        )
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
