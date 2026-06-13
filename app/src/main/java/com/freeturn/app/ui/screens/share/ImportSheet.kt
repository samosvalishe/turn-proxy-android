@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.freeturn.app.ui.screens.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freeturn.app.R
import com.freeturn.app.data.config.ObfProfile
import com.freeturn.app.data.share.FreeturnLink
import com.freeturn.app.ui.components.SettingsCard
import com.freeturn.app.ui.components.SettingsFieldSlot
import com.freeturn.app.viewmodel.ImportViewModel
import org.koin.androidx.compose.koinViewModel
import com.freeturn.app.ui.theme.Spacing

/**
 * Sheet импорта по freeturn://-ссылке. Живёт на уровне AppNavigation поверх
 * NavHost (как CaptchaWebViewDialog) - всплывает из любого места приложения.
 */
@Composable
fun ImportSheet(
    onImported: () -> Unit,
    viewModel: ImportViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.parseError) {
        AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text(stringResource(R.string.import_error_title)) },
            text = { Text(stringResource(R.string.import_error_desc)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismiss) {
                    Text(stringResource(R.string.import_error_ok))
                }
            }
        )
        return
    }

    val link = state.link ?: return
    val fallbackName = stringResource(R.string.import_default_name)

    ModalBottomSheet(onDismissRequest = viewModel::dismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xxl)
                .navigationBarsPadding()
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            if (state.saved) {
                ImportSuccess(onDone = {
                    viewModel.dismiss()
                    onImported()
                })
                return@Column
            }

            Text(
                stringResource(R.string.import_title),
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedTextField(
                value = state.serverName,
                onValueChange = viewModel::setServerName,
                label = { Text(stringResource(R.string.server_name_label)) },
                singleLine = true,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.vkLink,
                onValueChange = viewModel::setVkLink,
                label = { Text(stringResource(R.string.import_vk_link_label)) },
                supportingText = { Text(stringResource(R.string.import_vk_link_helper)) },
                singleLine = true,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth()
            )

            ImportSummary(link)

            if (state.duplicateConf) {
                ImportWarning(stringResource(R.string.import_duplicate_conf))
            } else if (state.duplicateAddress) {
                ImportWarning(stringResource(R.string.import_duplicate_address))
            }

            if (state.saveError) {
                Text(
                    stringResource(R.string.import_save_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = { viewModel.confirm(fallbackName) },
                enabled = state.canConfirm,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.saving) {
                    CircularWavyProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text(stringResource(R.string.import_confirm))
                }
            }
        }
    }
}

/** Сводка конфига из ссылки: адрес, режим, обфускация. */
@Composable
private fun ImportSummary(link: FreeturnLink) {
    SettingsCard {
        SettingsFieldSlot(verticalSpacing = 8.dp) {
            SummaryRow(
                label = stringResource(R.string.import_summary_address),
                value = link.peer
            )
            SummaryRow(
                label = stringResource(R.string.import_summary_mode),
                value = stringResource(
                    if (link.wgConf.isNotBlank()) R.string.import_summary_mode_vpn
                    else R.string.import_summary_mode_proxy
                )
            )
            SummaryRow(
                label = stringResource(R.string.import_summary_obf),
                value = stringResource(
                    if (link.obfProfile.isNotEmpty() && link.obfProfile != ObfProfile.NONE)
                        R.string.import_summary_obf_on
                    else R.string.import_summary_obf_off
                )
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ImportWarning(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.tertiary
    )
}

@Composable
private fun ImportSuccess(onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painterResource(R.drawable.check_circle_24px),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.import_success_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.import_success_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.import_success_go_home))
        }
    }
}
