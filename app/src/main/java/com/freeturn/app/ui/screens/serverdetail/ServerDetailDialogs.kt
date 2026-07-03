package com.freeturn.app.ui.screens.serverdetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.freeturn.app.R
import com.freeturn.app.data.HapticUtil
import com.freeturn.app.data.control.UninstallData
import com.freeturn.app.ui.components.BusyProgressIndicator
import com.freeturn.app.ui.theme.Spacing
import com.freeturn.app.viewmodel.settings.ServerCleanupState

/** Подтверждение удаления сервера из приложения (без серверной очистки). */
@Composable
internal fun DeleteServerDialog(
    serverName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.server_delete_confirm_title)) },
        text = { Text(stringResource(R.string.server_delete_confirm_desc, serverName)) },
        confirmButton = {
            TextButton(
                onClick = {
                    HapticUtil.perform(context, HapticUtil.Pattern.ERROR)
                    onConfirm()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(stringResource(R.string.server_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/**
 * Машина диалогов очистки сервера от FreeTurn: предупреждение -> wavy -> итог.
 * Сервер из приложения НЕ удаляется (отдельное действие).
 */
@Composable
internal fun ServerCleanupDialog(
    state: ServerCleanupState,
    onConfirm: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    when (state) {
        ServerCleanupState.Running -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.server_delete_cleaning_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.server_delete_cleaning_desc))
                    Spacer(Modifier.height(Spacing.lg))
                    BusyProgressIndicator()
                }
            },
            confirmButton = {}
        )
        is ServerCleanupState.Done -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.server_delete_done_title)) },
            text = { CleanupResult(state.data) },
            confirmButton = {
                TextButton(onClick = {
                    HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                    onClose()
                }) { Text(stringResource(R.string.server_delete_done_confirm)) }
            }
        )
        else -> {
            val err = (state as? ServerCleanupState.Error)?.message
            AlertDialog(
                onDismissRequest = onClose,
                title = { Text(stringResource(R.string.server_clean_confirm_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.server_clean_confirm_desc))
                        err?.let {
                            Spacer(Modifier.height(Spacing.md))
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            HapticUtil.perform(context, HapticUtil.Pattern.ERROR)
                            onConfirm()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(if (err != null) R.string.server_delete_retry else R.string.server_clean_confirm_btn))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
    }
}

@Composable
internal fun RenameServerDialog(
    currentName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var newName by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_server_title)) },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text(stringResource(R.string.server_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    HapticUtil.perform(context, HapticUtil.Pattern.CLICK)
                    onSave(newName)
                },
                enabled = newName.isNotBlank()
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** Итог удалённой очистки: что снесли с сервера и что не тронули. */
@Composable
private fun CleanupResult(data: UninstallData) {
    Column {
        Text(
            stringResource(R.string.server_delete_done_desc),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(Spacing.md))

        val r = data.removed
        val removed = buildList {
            if (r.binary) add(R.string.cleanup_item_binary)
            if (r.unit || r.legacyUnit) add(R.string.cleanup_item_service)
            if (r.wgIface) add(R.string.cleanup_item_wg)
            if (data.wgPkgRemoved) add(R.string.cleanup_item_wg_pkg)
            if (r.ufw) add(R.string.cleanup_item_firewall)
            if (r.prefix) add(R.string.cleanup_item_files)
        }
        if (removed.isEmpty()) {
            Text(
                stringResource(R.string.cleanup_nothing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                stringResource(R.string.cleanup_removed_label),
                style = MaterialTheme.typography.labelLarge
            )
            removed.forEach {
                Text("•  " + stringResource(it), style = MaterialTheme.typography.bodySmall)
            }
        }

        if (data.kept.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            Text(
                stringResource(R.string.cleanup_kept_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            data.kept.forEach { token ->
                // kept-токены - контракт _kept_add (80-cmd.sh); незнакомый показываем как есть.
                val label = when (token) {
                    "foreign_ft_wg0" -> stringResource(R.string.cleanup_kept_foreign_wg)
                    "wireguard-tools" -> stringResource(R.string.cleanup_kept_wg_tools)
                    "ip_forward" -> stringResource(R.string.cleanup_kept_ip_forward)
                    else -> token
                }
                Text(
                    "•  $label",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
